import { NextResponse } from "next/server";
import { safeNextPath } from "@/lib/auth/next-path";
import { isProvider } from "@/lib/auth/oauth-providers";
import { startProviderLogin } from "@/lib/identity/oauth-login";
import { NO_STORE, redirectReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 发起第三方登录（决策 B）：浏览器 → 这里 → identity `GET /oauth2/authorization/{provider}`，写 `state` Cookie 后
/// 把平台地址原样跳给浏览器。绑定不走这里（要先判再认证），见 `startProviderLink`。
export async function GET(
  request: Request,
  { params }: { params: Promise<{ provider: string }> },
): Promise<NextResponse> {
  const { provider } = await params;
  if (!isProvider(provider)) {
    return new NextResponse(null, { status: 404, headers: NO_STORE });
  }
  const query = new URL(request.url).searchParams;
  return redirectReply(
    await startProviderLogin(provider, {
      from: query.get("from") === "register" ? "register" : "login",
      next: safeNextPath(query.get("next")),
    }),
  );
}
