/// 头像上传、移除接口（`/api/me/avatar`）回给浏览器的结果。不带文案，裁剪对话框与头像卡片按 `reason`
/// 和原因码选文案。
export type AvatarMutationResult =
  | { ok: true; avatarUrl: string | null }
  | {
      ok: false;
      reason: "too-large" | "rejected" | "pending-deletion" | "signed-out" | "unavailable";
      /// `rejected` 时是 identity 的原因码（`UNSUPPORTED_TYPE`、`TOO_SMALL` 等）
      code?: string;
    };

const UNAVAILABLE: AvatarMutationResult = { ok: false, reason: "unavailable" };

/// 读接口的 JSON 结果；不是预期的形状、网络错误都按 `unavailable`
async function call(init: RequestInit): Promise<AvatarMutationResult> {
  try {
    const res = await fetch("/api/me/avatar", init);
    const body: unknown = await res.json();
    return typeof body === "object" && body !== null && "ok" in body
      ? (body as AvatarMutationResult)
      : UNAVAILABLE;
  } catch {
    return UNAVAILABLE;
  }
}

/// 上传裁好的头像（浏览器端）
export function uploadAvatar(file: Blob, fileName: string): Promise<AvatarMutationResult> {
  const form = new FormData();
  form.append("file", file, fileName);
  return call({ method: "POST", body: form });
}

/// 移除头像，回到默认头像（浏览器端）
export function removeAvatar(): Promise<AvatarMutationResult> {
  return call({ method: "DELETE" });
}
