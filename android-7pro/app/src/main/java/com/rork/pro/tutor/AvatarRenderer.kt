package com.rork.pro.tutor

import android.app.ActivityManager
import android.content.Context
import android.opengl.Matrix
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.tan

/**
 * Real-time renderer for the 3D tutor, built directly on Filament (Apache-2.0).
 *
 * The model is a glTF binary (.glb) with face shape keys — ARKit names (jawOpen, eyeBlinkLeft,
 * mouthSmileLeft …) and/or the 15 visemes (viseme_aa, viseme_PP …). See docs/AI_TUTOR_AVATAR.md.
 * Every frame [frameSource] supplies shape weights and a head pose from [FaceDriver]; the
 * renderer applies whichever of those shapes the model actually has, rotates the head joint
 * (or, failing that, the whole bust very slightly) and draws over a transparent background so
 * the Compose scene behind it shows through.
 */
class AvatarRenderer(
    private val textureView: TextureView,
    private val modelFile: File,
    private val frameSource: (tSec: Double) -> Pair<Map<String, Float>, HeadPose>,
    private val onReady: () -> Unit,
    private val onFailed: (Throwable) -> Unit,
) : Choreographer.FrameCallback {

    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera
    private var cameraEntity = 0
    private lateinit var materials: UbershaderProvider
    private lateinit var assetLoader: AssetLoader
    private lateinit var resourceLoader: ResourceLoader
    private var asset: FilamentAsset? = null
    private var swapChain: SwapChain? = null
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val lights = ArrayList<Int>()
    private var indirect: IndirectLight? = null

    /** Per renderable: its Filament instance and a slot index per normalised shape name. */
    private class Morphs(val instance: Int, val slots: Map<String, Int>, val weights: FloatArray)
    private val morphs = ArrayList<Morphs>()
    private var hasVisemes = false

    private var headEntity = 0
    private val headRest = FloatArray(16)
    private val rootRest = FloatArray(16)
    private var hasAnimation = false
    private var loaded = false
    private var reportedReady = false
    private var running = false
    private var startNanos = 0L
    private var width = 1
    private var height = 1

    private val eye = FloatArray(3)
    private val target = FloatArray(3)

    fun start() {
        try {
            Utils.init() // loads filament, gltfio and utils JNI
            engine = Engine.create()
            renderer = engine.createRenderer()
            scene = engine.createScene()
            view = engine.createView()
            cameraEntity = EntityManager.get().create()
            camera = engine.createCamera(cameraEntity)
            view.scene = scene
            view.camera = camera
            view.blendMode = View.BlendMode.TRANSLUCENT
            // Soft, cheap anti-aliasing; no SSAO/bloom so weak GPUs keep 30+ fps.
            view.antiAliasing = View.AntiAliasing.FXAA
            renderer.clearOptions = renderer.clearOptions.apply {
                clear = true
                clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
            }
            setupLighting()

            uiHelper.isOpaque = false
            uiHelper.renderCallback = object : UiHelper.RendererCallback {
                override fun onNativeWindowChanged(surface: Surface) {
                    swapChain?.let { engine.destroySwapChain(it) }
                    swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                }
                override fun onDetachedFromSurface() {
                    swapChain?.let {
                        engine.destroySwapChain(it)
                        engine.flushAndWait()
                    }
                    swapChain = null
                }
                override fun onResized(w: Int, h: Int) {
                    width = max(1, w); height = max(1, h)
                    view.viewport = Viewport(0, 0, width, height)
                    updateProjection()
                }
            }
            uiHelper.attachTo(textureView)

            loadModel()
            running = true
            startNanos = System.nanoTime()
            Choreographer.getInstance().postFrameCallback(this)
        } catch (t: Throwable) {
            Log.w(TAG, "3D tutor unavailable", t)
            onFailed(t)
        }
    }

    private fun setupLighting() {
        // Warm key light from front-left above, cool fill from the right, gentle ambient —
        // a classic portrait setup that flatters skin without any expensive shadows.
        fun directional(r: Float, g: Float, b: Float, lux: Float, dx: Float, dy: Float, dz: Float) {
            val e = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(r, g, b)
                .intensity(lux)
                .direction(dx, dy, dz)
                .castShadows(false)
                .build(engine, e)
            scene.addEntity(e)
            lights.add(e)
        }
        directional(1.0f, 0.95f, 0.88f, 95_000f, 0.45f, -0.45f, -1.0f)
        directional(0.80f, 0.88f, 1.0f, 32_000f, -0.7f, -0.1f, -0.6f)
        directional(1.0f, 0.93f, 0.85f, 26_000f, 0.0f, -0.3f, 1.0f) // rim from behind
        indirect = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.62f, 0.58f, 0.56f))
            .intensity(28_000f)
            .build(engine)
        scene.indirectLight = indirect
    }

    private fun loadModel() {
        materials = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materials, EntityManager.get())
        resourceLoader = ResourceLoader(engine)
        val bytes = modelFile.readBytes()
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.flip() }
        val a = assetLoader.createAsset(buffer) ?: error("Not a valid .glb")
        asset = a
        resourceLoader.asyncBeginLoad(a)
        scene.addEntities(a.entities)

        val tm = engine.transformManager
        tm.getTransform(tm.getInstance(a.root), rootRest)

        // Face shapes, matched by name so any ARKit/viseme-rigged avatar works.
        val rm = engine.renderableManager
        for (entity in a.renderableEntities) {
            val names = runCatching { a.getMorphTargetNames(entity) }.getOrNull() ?: continue
            if (names.isEmpty()) continue
            val ri = rm.getInstance(entity)
            val slots = HashMap<String, Int>()
            names.forEachIndexed { i, n -> slots[normalise(n)] = i }
            if (slots.keys.any { it.startsWith("viseme") }) hasVisemes = true
            morphs.add(Morphs(ri, slots, FloatArray(names.size)))
        }

        // Head joint for natural head motion (common rig names).
        headEntity = HEAD_NAMES.firstNotNullOfOrNull { n -> a.getFirstEntityByName(n).takeIf { it != 0 } } ?: 0
        if (headEntity != 0) tm.getTransform(tm.getInstance(headEntity), headRest)
        hasAnimation = runCatching { a.instance.animator.animationCount > 0 }.getOrDefault(false)

        frameCamera(a)
        Log.i(TAG, "tutor model: ${morphs.sumOf { it.slots.size }} shapes, visemes=$hasVisemes, head=${headEntity != 0}, anim=$hasAnimation")
    }

    /** Frames head and shoulders whatever the model's scale: a bust is cropped below the collar, a full body to the upper third. */
    private fun frameCamera(a: FilamentAsset) {
        val box = a.boundingBox
        val c = box.center
        val h = box.halfExtent
        val tall = h[1] > h[0] * 2.2f
        // Video-call framing: head and shoulders, the chest cut off just below the collar.
        val visibleH = if (tall) 2f * h[1] * 0.36f else 2f * h[1] * 0.62f
        target[0] = c[0]
        target[1] = c[1] + h[1] * if (tall) 0.74f else 0.45f
        target[2] = c[2]
        val fovV = 2.0 * Math.atan(12.0 / FOCAL_MM) // 24 mm sensor height
        val dist = (visibleH / 2f) / tan(fovV / 2.0).toFloat()
        eye[0] = target[0]; eye[1] = target[1] + visibleH * 0.03f; eye[2] = c[2] + h[2] + dist
        camera.lookAt(eye[0].toDouble(), eye[1].toDouble(), eye[2].toDouble(), target[0].toDouble(), target[1].toDouble(), target[2].toDouble(), 0.0, 1.0, 0.0)
        updateProjection(dist + h[2] * 4f)
    }

    private var far = 100.0
    private fun updateProjection(farHint: Float? = null) {
        if (farHint != null) far = max(10.0, farHint.toDouble() * 2)
        if (!::camera.isInitialized) return
        camera.setLensProjection(FOCAL_MM, width.toDouble() / height, 0.02, far)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        Choreographer.getInstance().postFrameCallback(this)
        val a = asset ?: return
        try {
            if (!loaded) {
                resourceLoader.asyncUpdateLoad()
                if (resourceLoader.asyncGetLoadProgress() >= 1f) {
                    loaded = true
                    a.releaseSourceData()
                }
            }
            val t = (frameTimeNanos - startNanos) / 1e9
            val (weights, pose) = frameSource(t)
            applyAnimation(a, t, pose)
            applyMorphs(weights)

            val sc = swapChain
            if (sc != null && uiHelper.isReadyToRender && renderer.beginFrame(sc, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
                if (loaded && !reportedReady) {
                    reportedReady = true
                    onReady()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "render failed", t)
            running = false
            onFailed(t)
        }
    }

    private val rot = FloatArray(16)
    private val tmp = FloatArray(16)
    private val local = FloatArray(16)

    private fun applyAnimation(a: FilamentAsset, t: Double, pose: HeadPose) {
        val tm = engine.transformManager
        val animator = a.instance.animator
        if (hasAnimation) {
            val dur = animator.getAnimationDuration(0).toDouble().coerceAtLeast(0.001)
            animator.applyAnimation(0, (t % dur).toFloat())
        }
        Matrix.setIdentityM(rot, 0)
        val deg = (180.0 / Math.PI).toFloat()
        Matrix.rotateM(rot, 0, pose.yaw * deg, 0f, 1f, 0f)
        Matrix.rotateM(rot, 0, pose.pitch * deg, 1f, 0f, 0f)
        Matrix.rotateM(rot, 0, pose.roll * deg, 0f, 0f, 1f)

        if (headEntity != 0) {
            val inst = tm.getInstance(headEntity)
            if (hasAnimation) tm.getTransform(inst, local) else System.arraycopy(headRest, 0, local, 0, 16)
            Matrix.multiplyMM(tmp, 0, local, 0, rot, 0)
            tm.setTransform(inst, tmp)
            // Breathing: a barely visible rise of the whole bust.
            System.arraycopy(rootRest, 0, local, 0, 16)
            Matrix.translateM(local, 0, 0f, pose.breathe * 0.002f, 0f)
            tm.setTransform(tm.getInstance(a.root), local)
        } else {
            // No head joint: turn the whole bust by a fraction of the pose around its own centre.
            Matrix.setIdentityM(rot, 0)
            Matrix.rotateM(rot, 0, pose.yaw * deg * 0.35f, 0f, 1f, 0f)
            Matrix.rotateM(rot, 0, pose.pitch * deg * 0.25f, 1f, 0f, 0f)
            Matrix.multiplyMM(tmp, 0, rootRest, 0, rot, 0)
            tm.setTransform(tm.getInstance(a.root), tmp)
        }
        animator.updateBoneMatrices()
    }

    private fun applyMorphs(weights: Map<String, Float>) {
        if (morphs.isEmpty()) return
        val rm = engine.renderableManager
        for (m in morphs) {
            java.util.Arrays.fill(m.weights, 0f)
            for ((name, w) in weights) {
                val key = normalise(name)
                // With real viseme shapes, the ARKit mouth approximations would double up.
                if (hasVisemes && key.startsWith("mouth") && !key.startsWith("mouthsmile")) continue
                val slot = m.slots[key] ?: continue
                m.weights[slot] = w.coerceIn(0f, 1f)
            }
            rm.setMorphWeights(m.instance, m.weights, 0)
        }
    }

    fun destroy() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        if (!::engine.isInitialized) return
        runCatching {
            uiHelper.detach()
            asset?.let { a ->
                scene.removeEntities(a.entities)
                assetLoader.destroyAsset(a)
            }
            if (::materials.isInitialized) { materials.destroyMaterials(); materials.destroy() }
            if (::assetLoader.isInitialized) assetLoader.destroy()
            if (::resourceLoader.isInitialized) resourceLoader.destroy()
            indirect?.let { engine.destroyIndirectLight(it) }
            lights.forEach { engine.destroyEntity(it); EntityManager.get().destroy(it) }
            swapChain?.let { engine.destroySwapChain(it) }
            engine.destroyRenderer(renderer)
            engine.destroyView(view)
            engine.destroyScene(scene)
            engine.destroyCameraComponent(cameraEntity)
            EntityManager.get().destroy(cameraEntity)
            engine.destroy()
        }.onFailure { Log.w(TAG, "cleanup", it) }
    }

    companion object {
        private const val TAG = "TutorAvatar"
        private const val FOCAL_MM = 55.0
        private val HEAD_NAMES = listOf("Head", "head", "mixamorig:Head", "mixamorigHead", "CC_Base_Head", "DEF-head", "J_Bip_C_Head", "head_joint")

        fun normalise(name: String): String = name.substringAfterLast('.').lowercase().filter { it.isLetterOrDigit() }

        /** Filament needs OpenGL ES 3.0 and a little memory headroom; older phones get the 2D presence instead. */
        fun deviceSupports3d(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
            val gles3 = am.deviceConfigurationInfo.reqGlEsVersion >= 0x30000
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            return gles3 && mem.totalMem >= 1_400L * 1024 * 1024
        }
    }
}
