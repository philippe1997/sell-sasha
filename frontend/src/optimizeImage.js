const MAX_FILE_BYTES = 200_000;
const MAX_IMAGE_EDGE = 1920;
const MIN_IMAGE_EDGE = 320;
const WEBP_QUALITIES = [0.82, 0.72, 0.62, 0.52, 0.42, 0.32];

export async function optimizeImage(file) {
  const isHeic = isHeicFile(file);
  let bitmap;
  try {
    bitmap = await decodeImage(file, isHeic);
  } catch {
    const hint = isHeic
      ? "HEIC/HEIF decoding failed; try converting the image to JPEG."
      : "Check that the file is a supported image.";
    throw new Error(`Could not read "${file.name}" as an image. ${hint}`);
  }

  const scale = Math.min(1, MAX_IMAGE_EDGE / Math.max(bitmap.width, bitmap.height));
  let width = Math.max(1, Math.round(bitmap.width * scale));
  let height = Math.max(1, Math.round(bitmap.height * scale));
  const canvas = document.createElement("canvas");

  try {
    const context = canvas.getContext("2d", { alpha: true });
    if (!context) {
      throw new Error("Image optimization is not supported by this browser.");
    }

    while (true) {
      canvas.width = width;
      canvas.height = height;
      context.drawImage(bitmap, 0, 0, width, height);

      for (const quality of WEBP_QUALITIES) {
        const blob = await encodeWebP(canvas, quality);
        if (blob.type !== "image/webp") {
          throw new Error("This browser does not support WebP image optimization.");
        }
        if (blob.size <= MAX_FILE_BYTES) {
          const optimizedName = file.name.replace(/\.[^.]+$/, "") + ".webp";
          return new File([blob], optimizedName, {
            type: "image/webp",
            lastModified: file.lastModified,
          });
        }
      }

      if (Math.max(width, height) <= MIN_IMAGE_EDGE) {
        break;
      }

      const resizeScale = Math.max(
        MIN_IMAGE_EDGE / Math.max(width, height),
        0.8,
      );
      width = Math.max(1, Math.round(width * resizeScale));
      height = Math.max(1, Math.round(height * resizeScale));
    }
  } finally {
    bitmap.close();
    canvas.width = 0;
    canvas.height = 0;
  }

  throw new Error(
    `"${file.name}" could not be reduced to 200 KB. Try a smaller image.`,
  );
}

function isHeicFile(file) {
  const fileName = file.name.toLowerCase();
  const mimeType = file.type.toLowerCase();
  return (
    mimeType.includes("heic") ||
    mimeType.includes("heif") ||
    fileName.endsWith(".heic") ||
    fileName.endsWith(".heif")
  );
}

async function decodeImage(file, isHeic) {
  if (!isHeic) {
    return createImageBitmap(file);
  }

  const { default: convertHeic } = await import("heic2any");
  const converted = await convertHeic({
    blob: file,
    toType: "image/jpeg",
    quality: 0.9,
  });
  const jpeg = Array.isArray(converted) ? converted[0] : converted;
  if (!jpeg) {
    throw new Error("HEIC/HEIF conversion returned no image.");
  }
  return createImageBitmap(jpeg);
}

function encodeWebP(canvas, quality) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) {
          resolve(blob);
        } else {
          reject(new Error("This browser could not optimize an image."));
        }
      },
      "image/webp",
      quality,
    );
  });
}
