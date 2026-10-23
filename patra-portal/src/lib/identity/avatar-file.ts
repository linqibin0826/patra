import type { AvatarMutationResult } from "@/lib/identity/avatar-upload";

/// 本地校验出的问题：类型不对、超过 5 MB、短边不足 128、读不出来（简报 §4.6）
export type AvatarFileIssue = "type" | "size" | "small" | "unreadable";

const ACCEPTED_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);
const MAX_BYTES = 5 * 1024 * 1024;
const MIN_EDGE = 128;
/// 导出的正方形边长上限：服务端再生成 512 / 128 两档，再大没有意义
const MAX_EXPORT_EDGE = 1024;

/// 解码图片拿宽高（默认用 `createImageBitmap`）
async function decodeSize(file: File): Promise<{ width: number; height: number }> {
  const bitmap = await createImageBitmap(file);
  const size = { width: bitmap.width, height: bitmap.height };
  bitmap.close();
  return size;
}

/// 上传前在本地校验（服务端的 413 / 422 只是兜底）：类型 → 大小 → 能否解码 → 短边。合格时为 null。
export async function checkAvatarFile(
  file: File,
  decode: (file: File) => Promise<{ width: number; height: number }> = decodeSize,
): Promise<AvatarFileIssue | null> {
  if (!ACCEPTED_TYPES.has(file.type)) return "type";
  if (file.size > MAX_BYTES) return "size";
  let size: { width: number; height: number };
  try {
    size = await decode(file);
  } catch {
    return "unreadable";
  }
  return Math.min(size.width, size.height) < MIN_EDGE ? "small" : null;
}

/// 本地校验问题的文案
export const AVATAR_FILE_MESSAGES: Record<AvatarFileIssue, string> = {
  type: "只支持 JPG、PNG 或 WebP 图片",
  size: "图片不能超过 5 MB",
  small: "图片太小了，短边至少 128 像素",
  unreadable: "这个文件打不开，换一张试试",
};

/// 上传失败的文案（简报 §4.6、PAP-97 契约）：服务端的原因码对得上本地那几种就用同一句
export function avatarUploadMessage(result: AvatarMutationResult): string {
  if (!result.ok && result.reason === "too-large") return AVATAR_FILE_MESSAGES.size;
  if (!result.ok && result.reason === "rejected") {
    switch (result.code) {
      case "UNSUPPORTED_TYPE":
        return AVATAR_FILE_MESSAGES.type;
      case "TOO_SMALL":
        return AVATAR_FILE_MESSAGES.small;
      case "UNREADABLE":
      case "TOO_MANY_PIXELS":
        return AVATAR_FILE_MESSAGES.unreadable;
    }
  }
  return "头像没有上传成功，请稍后再试";
}

/// 裁剪区域（原图像素）
export interface CropArea {
  x: number;
  y: number;
  width: number;
  height: number;
}

/// 把裁好的正方形画到 canvas 导出：边长取 min(裁剪边长, 1024)；JPEG 源出 JPEG（q 0.92），PNG / WebP
/// （可能有透明）出 PNG。`src` 是原图的对象地址。
export async function exportCrop(src: string, area: CropArea, sourceType: string): Promise<Blob> {
  const image = new Image();
  image.src = src;
  await image.decode();
  const edge = Math.round(Math.min(area.width, MAX_EXPORT_EDGE));
  const canvas = document.createElement("canvas");
  canvas.width = edge;
  canvas.height = edge;
  const context = canvas.getContext("2d");
  if (!context) {
    throw new Error("浏览器不支持 canvas");
  }
  context.imageSmoothingQuality = "high";
  context.drawImage(image, area.x, area.y, area.width, area.height, 0, 0, edge, edge);
  const type = sourceType === "image/jpeg" ? "image/jpeg" : "image/png";
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("导出失败"))), type, 0.92),
  );
}
