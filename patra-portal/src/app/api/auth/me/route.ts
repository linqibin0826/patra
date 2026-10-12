import { NextResponse } from "next/server";
import { lookupSession } from "@/lib/auth/current-user";
import { clearSessionCookie, refreshSessionCookie } from "@/lib/auth/session-cookie";
import type { CurrentUserPayload } from "@/lib/auth/types";

export const dynamic = "force-dynamic";

/// 用户态数据不缓存：HTTP 层和 Next 都不缓存，不同用户之间也不共享
const NO_STORE = { "Cache-Control": "private, no-store" };

/// 200 响应，带 `NO_STORE`。
function payload(body: CurrentUserPayload): NextResponse {
  return NextResponse.json(body, { headers: NO_STORE });
}

/// 当前用户：浏览器（导航栏）→ `/api/auth/me` → 网关 `/patra-identity/auth/me`。
/// 读到用户时把 Cookie 的有效期顺延回 30 天，网关也在同一个请求里给会话续期；
/// 网关回 401 时清掉 Cookie；网关不可用时回 503，Cookie 不动。
export async function GET(): Promise<NextResponse> {
  const session = await lookupSession();
  if (session.status === "authenticated") {
    await refreshSessionCookie();
    return payload({ user: session.user });
  }
  if (session.status === "rejected") {
    await clearSessionCookie();
    return payload({ user: null });
  }
  if (session.status === "anonymous") {
    return payload({ user: null });
  }
  return new NextResponse(null, { status: 503, headers: NO_STORE });
}
