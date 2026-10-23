import { NextResponse } from "next/server";
import { isRecord } from "@/lib/auth/gateway-response";
import { type ReauthResult, reauthenticate } from "@/lib/identity/reauth";
import { isSameOrigin } from "@/lib/same-origin";

export const dynamic = "force-dynamic";

const NO_STORE = { "Cache-Control": "private, no-store" };

/// 结果 → 状态码；客户端只读响应体
const STATUS: Record<Exclude<ReauthResult, { ok: true }>["reason"], number> = {
  "wrong-password": 401,
  throttled: 429,
  "signed-out": 401,
  "pending-deletion": 409,
  unavailable: 503,
};

/// JSON 结果 + 状态码，不缓存
function reply(status: number, body: ReauthResult): NextResponse {
  return NextResponse.json(body, { status, headers: NO_STORE });
}

/// 再认证（spec §6.1）：浏览器 → 这里 → identity `POST /auth/reauth`，成功时在这里换会话 Cookie。用 Route Handler
/// 而不是 Server Action，换 Cookie 不会触发当前页的重新渲染（见 `reauthenticate` 的说明）。同源校验挡跨站请求。
export async function POST(request: Request): Promise<NextResponse> {
  if (!isSameOrigin(request)) {
    return new NextResponse(null, { status: 403 });
  }
  const body: unknown = await request.json().catch(() => undefined);
  if (!isRecord(body) || typeof body.password !== "string") {
    return reply(400, { ok: false, reason: "unavailable" });
  }
  const result = await reauthenticate(body.password);
  return reply(result.ok ? 200 : STATUS[result.reason], result);
}
