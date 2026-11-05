import { NextResponse } from "next/server";
import { z } from "zod";
import type { LinkResult } from "@/lib/auth/registration-types";
import { completeProviderLink } from "@/lib/identity/oauth-registration";
import { forbidden, NO_STORE, readBody } from "@/lib/route-reply";
import { isSameOrigin } from "@/lib/same-origin";

export const dynamic = "force-dynamic";

const bodySchema = z.object({ code: z.string() });

/// 结果 → 状态码；客户端只读响应体
const STATUS: Record<Exclude<LinkResult, { ok: true }>["reason"], number> = {
  expired: 422,
  "code-invalid": 422,
  "code-expired": 422,
  "provider-taken": 409,
  "already-linked": 409,
  unavailable: 503,
};

/// 第三方首次登录邮箱撞车时就地绑定 · 提交验证码。用 Route Handler 而不是 Server Action，原因同 `complete`：这里写会话
/// Cookie、清 `ticket`，Server Action 会重新渲染当前页，用户会先看到一眼「已失效」。同源校验挡跨站请求。
export async function POST(request: Request): Promise<NextResponse> {
  if (!isSameOrigin(request)) {
    return forbidden();
  }
  const body = bodySchema.safeParse(await readBody(request));
  const result: LinkResult = body.success
    ? await completeProviderLink(body.data.code)
    : { ok: false, reason: "unavailable" };
  return NextResponse.json(result, {
    status: result.ok ? 200 : STATUS[result.reason],
    headers: NO_STORE,
  });
}
