import { NextResponse } from "next/server";
import { isProvider } from "@/lib/auth/oauth-providers";
import { finishProviderCallback } from "@/lib/identity/oauth-login";
import { NO_STORE, redirectReply } from "@/lib/route-reply";

export const dynamic = "force-dynamic";

/// 第三方登录的回调地址（三家后台都注册了这个路径，不能改）：核对 `state` 后转给 identity，按结果写 Cookie 并跳转。
export async function GET(
  request: Request,
  { params }: { params: Promise<{ provider: string }> },
): Promise<NextResponse> {
  const { provider } = await params;
  if (!isProvider(provider)) {
    return new NextResponse(null, { status: 404, headers: NO_STORE });
  }
  return redirectReply(await finishProviderCallback(provider, new URL(request.url).search));
}
