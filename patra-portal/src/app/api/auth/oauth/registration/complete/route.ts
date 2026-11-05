import { NextResponse } from "next/server";
import { z } from "zod";
import type { CompleteResult } from "@/lib/auth/registration-types";
import { completeRegistration } from "@/lib/identity/oauth-registration";
import { forbidden, NO_STORE, readBody } from "@/lib/route-reply";
import { isSameOrigin } from "@/lib/same-origin";

export const dynamic = "force-dynamic";

const bodySchema = z.object({ code: z.string() });

/// 结果 → 状态码；客户端只读响应体
const STATUS: Record<Exclude<CompleteResult, { ok: true }>["reason"], number> = {
  expired: 422,
  "code-invalid": 422,
  "code-expired": 422,
  taken: 409,
  "provider-taken": 409,
  unavailable: 503,
};

/// 第三方首次注册 · 提交验证码建账号（决策 D）。用 Route Handler 而不是 Server Action：这里写会话 Cookie、清 `ticket`，
/// Server Action 写 Cookie 会重新渲染当前页，补填页读不到 `ticket`，用户会先看到一眼「已过期」。同源校验挡跨站请求。
export async function POST(request: Request): Promise<NextResponse> {
  if (!isSameOrigin(request)) {
    return forbidden();
  }
  const body = bodySchema.safeParse(await readBody(request));
  const result: CompleteResult = body.success
    ? await completeRegistration(body.data.code)
    : { ok: false, reason: "unavailable" };
  return NextResponse.json(result, {
    status: result.ok ? 201 : STATUS[result.reason],
    headers: NO_STORE,
  });
}
