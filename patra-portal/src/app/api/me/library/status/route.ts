import type { NextResponse } from "next/server";
import { z } from "zod";
import { fetchLibraryStatus } from "@/lib/interaction/library";
import { isEntityId } from "@/lib/library/params";
import { forbidden, invalidRequest, readBody, readReply } from "@/lib/route-reply";
import { isSameOrigin } from "@/lib/same-origin";

export const dynamic = "force-dynamic";

const bodySchema = z.object({ keys: z.array(z.string().refine(isEntityId)).min(1).max(100) });

/// 批量「我的状态」（一屏一次，≤ 100 篇）：浏览器 → 这里 → 网关 `/patra-interaction/me/library/status`。
/// 只读，但带请求体，按写操作的规矩校验 `Origin`；key 去重后再发
export async function POST(request: Request): Promise<NextResponse> {
  if (!isSameOrigin(request)) {
    return forbidden();
  }
  const body = bodySchema.safeParse(await readBody(request));
  if (!body.success) {
    return invalidRequest();
  }
  const read = await fetchLibraryStatus([...new Set(body.data.keys)]);
  return readReply(read.kind === "ok" ? { kind: "ok", data: { items: read.data } } : read);
}
