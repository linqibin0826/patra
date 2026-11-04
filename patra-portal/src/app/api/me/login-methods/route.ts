import { NextResponse } from "next/server";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { clearSessionCookie } from "@/lib/auth/session-cookie";
import { loginMethodsSchema } from "@/lib/identity/login-methods";
import { isSessionRejected } from "@/lib/identity/problem";
import { callIdentity } from "@/lib/identity/request";
import { NO_STORE } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

const ENDPOINT = "/patra-identity/me/login-methods";

/// 登录方式列表：浏览器（安全页）→ 这里 → 网关。只读、不改状态，不校验 `Origin`（跨站页面读不到响应体）。
/// 没有会话 401、会话失效清 Cookie 后 401，其余失败 502。
export async function GET(): Promise<NextResponse> {
  const reply = await callIdentity("/me/login-methods");
  if (reply.kind === "ok") {
    const parsed = reply.res.status === 200 ? loginMethodsSchema.safeParse(reply.body) : undefined;
    if (parsed?.success) {
      return NextResponse.json(parsed.data, { headers: NO_STORE });
    }
    logUnexpectedResponse(ENDPOINT, reply.res.status, undefined);
  }
  const signedOut =
    reply.kind === "no-session" || (reply.kind === "problem" && isSessionRejected(reply.problem));
  if (signedOut) {
    if (reply.kind === "problem") {
      await clearSessionCookie();
    }
    return NextResponse.json(
      { ok: false, reason: "signed-out" },
      { status: 401, headers: NO_STORE },
    );
  }
  if (reply.kind === "problem") {
    logUnexpectedResponse(ENDPOINT, reply.problem.status, { code: reply.problem.code });
  }
  return NextResponse.json(
    { ok: false, reason: "unavailable" },
    { status: 502, headers: NO_STORE },
  );
}
