import { describe, expect, it, vi } from "vitest";
import { avatarUploadMessage, checkAvatarFile } from "@/lib/identity/avatar-file";

const file = (type: string, size = 1_000) => new File([new Uint8Array(size)], "a", { type });
const decodeTo = (width: number, height: number) => vi.fn(async () => ({ width, height }));

describe("checkAvatarFile", () => {
  it.each(["image/gif", "image/svg+xml", "application/pdf", ""])("类型 %j：type", async (type) => {
    await expect(checkAvatarFile(file(type), decodeTo(512, 512))).resolves.toBe("type");
  });

  it("超过 5 MB：size", async () => {
    await expect(
      checkAvatarFile(file("image/png", 5 * 1024 * 1024 + 1), decodeTo(512, 512)),
    ).resolves.toBe("size");
  });

  it("读不出来：unreadable", async () => {
    const decode = vi.fn(async () => {
      throw new Error("bad");
    });
    await expect(checkAvatarFile(file("image/jpeg"), decode)).resolves.toBe("unreadable");
  });

  it("短边不足 128：small", async () => {
    await expect(checkAvatarFile(file("image/webp"), decodeTo(1024, 127))).resolves.toBe("small");
  });

  it("合格：null", async () => {
    await expect(checkAvatarFile(file("image/jpeg"), decodeTo(128, 300))).resolves.toBeNull();
  });
});

describe("avatarUploadMessage", () => {
  it.each([
    [{ ok: false, reason: "too-large" }, "图片不能超过 5 MB"],
    [{ ok: false, reason: "rejected", code: "UNSUPPORTED_TYPE" }, "只支持 JPG、PNG 或 WebP 图片"],
    [{ ok: false, reason: "rejected", code: "TOO_SMALL" }, "图片太小了，短边至少 128 像素"],
    [{ ok: false, reason: "rejected", code: "UNREADABLE" }, "这个文件打不开，换一张试试"],
    [{ ok: false, reason: "rejected", code: "TOO_MANY_PIXELS" }, "这个文件打不开，换一张试试"],
    [{ ok: false, reason: "rejected", code: "REQUIRED" }, "头像没有上传成功，请稍后再试"],
    [{ ok: false, reason: "unavailable" }, "头像没有上传成功，请稍后再试"],
  ] as const)("%j → %s", (result, message) => {
    expect(avatarUploadMessage(result)).toBe(message);
  });
});
