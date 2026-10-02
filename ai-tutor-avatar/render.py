import sys, math, numpy as np
from PIL import Image
sys.path.insert(0, "/tmp/avatar")
import importlib, build
importlib.reload(build)
from build import head_prims, body_prims, TI, MATS, normals

def render(weights=None, W=560, H=700, eye=(0, 0.0, 0.62), target=(0, -0.045, 0), fov=34, yaw=0.0, out=None, bg=((27,23,19),(11,10,9))):
    weights = weights or {}
    tris_all = []
    for plist, is_head in ((head_prims, True), (body_prims, False)):
        for p in plist:
            pos = p.pos.copy()
            for k, w in weights.items():
                if w: pos = pos + p.t[TI[k]] * w
            if is_head and yaw:
                c, s = math.cos(yaw), math.sin(yaw)
                pos = np.stack([pos[:,0]*c + pos[:,2]*s, pos[:,1], -pos[:,0]*s + pos[:,2]*c], 1)
            n = normals(pos, p.idx)
            base = np.array(MATS[p.mat]["c"][:3])
            col = base[None, :] * (p.col[:, :3] if p.col is not None else 1)
            rough = MATS[p.mat]["r"]
            tris_all.append((pos, n, col, p.idx.reshape(-1, 3), rough, p.mat))
    eye = np.array(eye, float); tgt = np.array(target, float)
    f = tgt - eye; f /= np.linalg.norm(f); r = np.cross(f, [0, 1, 0]); r /= np.linalg.norm(r); u = np.cross(r, f)
    fl = 1 / math.tan(math.radians(fov) / 2)
    img = np.zeros((H, W, 3)); zb = np.full((H, W), np.inf)
    top, bot = np.array(bg[0]) / 255, np.array(bg[1]) / 255
    for yy in range(H): img[yy] = top * (1 - yy/H) + bot * (yy/H)
    img = img ** 2.2
    L1 = np.array([0.45, 0.45, 1.0]); L1 /= np.linalg.norm(L1)
    L2 = np.array([-0.7, 0.1, 0.6]); L2 /= np.linalg.norm(L2)
    L3 = np.array([0.0, 0.3, -1.0]); L3 /= np.linalg.norm(L3)
    for pos, n, col, tri, rough, mat in tris_all:
        q = pos - eye
        cx, cy, cz = q @ r, q @ u, q @ f
        sx = (cx / cz * fl) * (H/2) + W/2; sy = -(cy / cz * fl) * (H/2) + H/2
        V = -(q / np.linalg.norm(q, axis=1, keepdims=True))
        nd = np.clip(n @ L1, 0, 1)*1.0 + np.clip(n @ L2, 0, 1)*0.35 + np.clip(n @ L3, 0, 1)*0.28 + 0.22
        Hh = (L1 + V); Hh /= np.linalg.norm(Hh, axis=1, keepdims=True)
        spec = np.clip((n * Hh).sum(1), 0, 1) ** (8 + (1-rough)*120) * (1 - rough) * 0.9
        shade = col * nd[:, None] + spec[:, None]
        for t in tri:
            a, b, c = t
            xs = np.array([sx[a], sx[b], sx[c]]); ys = np.array([sy[a], sy[b], sy[c]])
            x0, x1 = max(int(xs.min()), 0), min(int(xs.max()) + 1, W - 1)
            y0, y1 = max(int(ys.min()), 0), min(int(ys.max()) + 1, H - 1)
            if x0 > x1 or y0 > y1: continue
            den = (ys[1]-ys[2])*(xs[0]-xs[2]) + (xs[2]-xs[1])*(ys[0]-ys[2])
            if abs(den) < 1e-9: continue
            gx, gy = np.meshgrid(np.arange(x0, x1+1) + 0.5, np.arange(y0, y1+1) + 0.5)
            w0 = ((ys[1]-ys[2])*(gx-xs[2]) + (xs[2]-xs[1])*(gy-ys[2])) / den
            w1 = ((ys[2]-ys[0])*(gx-xs[2]) + (xs[0]-xs[2])*(gy-ys[2])) / den
            w2 = 1 - w0 - w1
            m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not m.any(): continue
            zz = w0*cz[a] + w1*cz[b] + w2*cz[c]
            sub = zb[y0:y1+1, x0:x1+1]
            m &= zz < sub
            if not m.any(): continue
            sub[m] = zz[m]
            cc = w0[..., None]*shade[a] + w1[..., None]*shade[b] + w2[..., None]*shade[c]
            img[y0:y1+1, x0:x1+1][m] = cc[m]
    img = np.clip(img, 0, 1) ** (1/2.2)
    im = Image.fromarray((img*255).astype(np.uint8))
    if out: im.save(out)
    return im

if __name__ == "__main__":
    render(out="/tmp/avatar/neutral.png")
    print("ok")
