import { NextResponse } from "next/server";
import { z } from "zod";
import type { SendCodeResult } from "@/lib/auth/registration-types";
import { sendRegistrationCode } from "@/lib/identity/oauth-registration";
import { forbidden, NO_STORE, readBody } from "@/lib/route-reply";
import { isSameOrigin } from "@/lib/same-origin";

export const dynamic = "force-dynamic";

const bodySchema = z.object({ username: z.string(), email: z.string() });

/// 结果 → 状态码；客户端只读响应体
const STATUS: Record<Exclude<SendCodeResult, { ok: true }>["reason"], number> = {
  expired: 422,
  "invalid-fields": 422,
  taken: 409,
  throttled: 429,
  unavailable: 503,
};

/// 第三方首次注册 · 发验证码（决策 D）：浏览器 → 这里 → identity。同源校验挡跨站请求。
export async function POST(request: Request): Promise<NextResponse> {
  if (!isSameOrigin(request)) {
    return forbidden();
  }
  const body = bodySchema.safeParse(await readBody(request));
  const result: SendCodeResult = body.success
    ? await sendRegistrationCode(body.data)
    : { ok: false, reason: "unavailable" };
  return NextResponse.json(result, {
    status: result.ok ? 200 : STATUS[result.reason],
    headers: NO_STORE,
  });
}
