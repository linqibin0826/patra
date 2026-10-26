import { NextResponse } from "next/server";
import { z } from "zod";
import { exportScopeSchema, fetchScopeKeys } from "@/lib/interaction/scope";
import { forbidden, invalidRequest, NO_STORE, readBody, readReply } from "@/lib/route-reply";
import { isSameOrigin } from "@/lib/same-origin";

export const dynamic = "force-dynamic";

const bodySchema = z.object({ scope: exportScopeSchema });

/// 导出 / 复制引用的范围 → 文献 ID 与跳过的已失效篇数；超 5,000 篇回 200 `{ tooLarge: true }`（对话框据此显示上限说明）
export async function POST(request: Request): Promise<NextResponse> {
  if (!isSameOrigin(request)) {
    return forbidden();
  }
  const body = bodySchema.safeParse(await readBody(request));
  if (!body.success) {
    return invalidRequest();
  }
  const read = await fetchScopeKeys(body.data.scope);
  if (read.kind === "too-large") {
    return NextResponse.json({ tooLarge: true }, { headers: NO_STORE });
  }
  return readReply(read);
}
