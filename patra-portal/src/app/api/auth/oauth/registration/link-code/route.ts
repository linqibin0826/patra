import { NextResponse } from "next/server";
import { z } from "zod";
import type { SendLinkCodeResult } from "@/lib/auth/registration-types";
import { sendLinkCode } from "@/lib/identity/oauth-registration";
import { forbidden, NO_STORE, readBody } from "@/lib/route-reply";
import { isSameOrigin } from "@/lib/same-origin";

export const dynamic = "force-dynamic";

const bodySchema = z.object({ email: z.string() });

/// 结果 → 状态码；客户端只读响应体
const STATUS: Record<Exclude<SendLinkCodeResult, { ok: true }>["reason"], number> = {
  expired: 422,
  "invalid-fields": 422,
  throttled: 429,
  unavailable: 503,
};

/// 第三方首次登录邮箱撞车时就地绑定 · 发验证码：浏览器 → 这里 → identity。同源校验挡跨站请求。
export async function POST(request: Request): Promise<NextResponse> {
  if (!isSameOrigin(request)) {
    return forbidden();
  }
  const body = bodySchema.safeParse(await readBody(request));
  const result: SendLinkCodeResult = body.success
    ? await sendLinkCode(body.data.email)
    : { ok: false, reason: "unavailable" };
  return NextResponse.json(result, {
    status: result.ok ? 200 : STATUS[result.reason],
    headers: NO_STORE,
  });
}
