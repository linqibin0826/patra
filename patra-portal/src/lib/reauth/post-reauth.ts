import type { ReauthResult } from "@/lib/identity/reauth";

const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

/// 响应体是不是一个结果（有布尔 `ok`）
function isResult(value: unknown): value is ReauthResult {
  return (
    typeof value === "object" && value !== null && "ok" in value && typeof value.ok === "boolean"
  );
}

/// 浏览器端调再认证（`POST /api/auth/reauth`）：失败的状态码也读响应体；网络断开、响应体不是结果都是
/// `unavailable`。
export async function postReauth(password: string): Promise<ReauthResult> {
  try {
    const res = await fetch("/api/auth/reauth", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ password }),
    });
    const body: unknown = await res.json().catch(() => undefined);
    return isResult(body) ? body : UNAVAILABLE;
  } catch {
    return UNAVAILABLE;
  }
}
