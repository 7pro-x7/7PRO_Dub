import {
  AuthError,
  corsHeaders,
  createAdminClient,
  json,
  requireAuth,
} from "../_shared/auth.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "METHOD_NOT_ALLOWED" }, 405);

  try {
    const user = await requireAuth(req);
    const admin = createAdminClient();
    const formData = await req.formData();
    const file = formData.get("file") as File;
    const mediaType = formData.get("mediaType") as string;
    const exerciseId = formData.get("exerciseId") as string;

    if (!file) return json({ error: "MISSING_FILE" }, 400);
    if (!mediaType || !["image", "video", "audio"].includes(mediaType)) {
      return json({ error: "INVALID_MEDIA_TYPE" }, 400);
    }

    // Validate file size (max 50MB for videos, 10MB for images, 5MB for audio)
    const maxSizes: Record<string, number> = {
      image: 10 * 1024 * 1024,
      video: 50 * 1024 * 1024,
      audio: 5 * 1024 * 1024,
    };

    if (file.size > maxSizes[mediaType]) {
      return json({ error: `FILE_TOO_LARGE_${mediaType.toUpperCase()}` }, 400);
    }

    // Validate file type
    const validMimes: Record<string, string[]> = {
      image: ["image/jpeg", "image/png", "image/webp", "image/gif"],
      video: ["video/mp4", "video/webm", "video/quicktime"],
      audio: ["audio/mpeg", "audio/wav", "audio/webm", "audio/ogg"],
    };

    if (!validMimes[mediaType].includes(file.type)) {
      return json({ error: `INVALID_FILE_TYPE_${mediaType.toUpperCase()}` }, 400);
    }

    // Generate unique filename
    const timestamp = Date.now();
    const random = Math.random().toString(36).substring(7);
    const ext = file.name.split(".").pop() || mediaType;
    const filename = `${user.userId}/${exerciseId || "temp"}/${mediaType}_${timestamp}_${random}.${ext}`;

    // Upload to storage (using Supabase storage)
    const { data: uploadData, error: uploadError } = await admin.storage
      .from("exercise_media")
      .upload(filename, file, {
        contentType: file.type,
        upsert: false,
      });

    if (uploadError) return json({ error: uploadError.message }, 400);

    // Get public URL
    const {
      data: { publicUrl },
    } = admin.storage.from("exercise_media").getPublicUrl(filename);

    // Extract aspect ratio for videos and images
    let aspectRatio: number | null = null;
    if (mediaType === "video" || mediaType === "image") {
      aspectRatio = await extractAspectRatio(publicUrl, file, mediaType);
    }

    return json({
      ok: true,
      url: publicUrl,
      filename: filename,
      mediaType: mediaType,
      size: file.size,
      aspectRatio: aspectRatio,
    });
  } catch (err) {
    if (err instanceof AuthError) return json({ error: "UNAUTHORIZED" }, 401);
    console.error("upload_media_error", err instanceof Error ? err.message : err);
    return json({ error: "INTERNAL_ERROR" }, 500);
  }
});

/**
 * Extracts the aspect ratio from an image or video file.
 * For images: reads metadata via Image constructor
 * For videos: uses a simpler heuristic based on file signature
 */
async function extractAspectRatio(
  url: string,
  file: File,
  mediaType: string,
): Promise<number | null> {
  try {
    if (mediaType === "image") {
      return await getImageAspectRatio(file);
    } else if (mediaType === "video") {
      return await getVideoAspectRatio(file);
    }
  } catch (err) {
    console.warn(`Could not extract aspect ratio for ${mediaType}:`, err instanceof Error ? err.message : err);
  }
  return null;
}

/**
 * Extracts aspect ratio from an image file using the Blob API.
 * Creates an Image element to read natural dimensions.
 */
async function getImageAspectRatio(file: File): Promise<number | null> {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const img = new Image();
      img.onload = () => {
        const ratio = img.naturalWidth / img.naturalHeight;
        resolve(Math.round(ratio * 100) / 100); // Round to 2 decimals
      };
      img.onerror = () => resolve(null);
      img.src = e.target?.result as string;
    };
    reader.onerror = () => resolve(null);
    reader.readAsDataURL(file);
  });
}

/**
 * Extracts aspect ratio from a video file.
 * Attempts to read MP4 metadata (moov atom) to get dimensions.
 * Falls back to common aspect ratios (16:9 = 1.78, 9:16 = 0.56, etc.)
 */
async function getVideoAspectRatio(file: File): Promise<number | null> {
  try {
    // Read first 32KB of file to extract MP4 metadata
    const chunk = await file.slice(0, 32 * 1024).arrayBuffer();
    const view = new DataView(chunk);

    // Look for 'moov' atom which contains video dimensions
    const moovOffset = findMoovOffset(chunk);
    if (moovOffset === -1) {
      // Fallback: assume 16:9 for unknown video formats
      return 1.78;
    }

    const dimensions = extractMP4Dimensions(view, moovOffset);
    if (dimensions) {
      const ratio = dimensions.width / dimensions.height;
      return Math.round(ratio * 100) / 100;
    }

    return 1.78; // Default to 16:9
  } catch {
    return 1.78; // Safe fallback
  }
}

/**
 * Finds the offset of the 'moov' atom in an MP4 file.
 */
function findMoovOffset(buffer: ArrayBuffer): number {
  const view = new Uint8Array(buffer);
  const moovSignature = [0x6d, 0x6f, 0x6f, 0x76]; // "moov" in ASCII

  for (let i = 0; i < view.length - 4; i++) {
    if (
      view[i] === moovSignature[0] &&
      view[i + 1] === moovSignature[1] &&
      view[i + 2] === moovSignature[2] &&
      view[i + 3] === moovSignature[3]
    ) {
      return i;
    }
  }
  return -1;
}

/**
 * Extracts video width and height from MP4 'tkhd' (track header) atom.
 */
function extractMP4Dimensions(
  view: DataView,
  moovOffset: number,
): { width: number; height: number } | null {
  try {
    // Look for 'tkhd' atom after 'moov'
    const tkhd = findAtomInMoov(view, moovOffset, "tkhd");
    if (tkhd === -1) return null;

    // tkhd atom structure:
    // - 8 bytes: atom size and type
    // - 4 bytes: version and flags
    // - 4 bytes: creation time
    // - 4 bytes: modification time
    // - 4 bytes: track ID
    // - 4 bytes: reserved
    // - 8 bytes: duration
    // - 8 bytes: reserved
    // - 2 bytes: layer
    // - 2 bytes: alternate group
    // - 2 bytes: volume (16.16 fixed point)
    // - 6 bytes: reserved
    // - 36 bytes: matrix
    // - 4 bytes: width (16.16 fixed point)
    // - 4 bytes: height (16.16 fixed point)

    const widthOffset = tkhd + 84;
    const heightOffset = tkhd + 88;

    if (widthOffset + 4 > view.byteLength || heightOffset + 4 > view.byteLength) {
      return null;
    }

    // Read as 16.16 fixed point and convert to integer
    const width = view.getUint32(widthOffset) >> 16;
    const height = view.getUint32(heightOffset) >> 16;

    if (width > 0 && height > 0) {
      return { width, height };
    }

    return null;
  } catch {
    return null;
  }
}

/**
 * Finds an atom type within the moov atom.
 */
function findAtomInMoov(view: DataView, moovOffset: number, atomType: string): number {
  const signature = atomType.split("").map((c) => c.charCodeAt(0));
  let i = moovOffset + 4; // Skip 'moov'

  while (i < Math.min(view.byteLength - 4, moovOffset + 1024)) {
    if (
      view.getUint8(i) === signature[0] &&
      view.getUint8(i + 1) === signature[1] &&
      view.getUint8(i + 2) === signature[2] &&
      view.getUint8(i + 3) === signature[3]
    ) {
      return i;
    }
    i++;
  }
  return -1;
}
