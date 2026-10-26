import { NextResponse } from "next/server";
import { z } from "zod";
import { logRequestError, logUnexpectedResponse, readJson } from "@/lib/auth/gateway-response";
import { gatewayFetch } from "@/lib/gateway";
import { isEntityId } from "@/lib/library/params";
import { forbidden, invalidRequest, NO_STORE, readBody } from "@/lib/route-reply";
import { isSameOrigin } from "@/lib/same-origin";

export const dynamic = "force-dynamic";

const ENDPOINT = "/patra-catalog/portal/publications/citations";

const CITATION_STYLES = ["gb-t-7714-2015", "vancouver", "apa-7", "ama-11"] as const;

const bodySchema = z.object({
  ids: z.array(z.string().refine(isEntityId)).min(1).max(100),
  style: z.enum(CITATION_STYLES),
});

const responseSchema = z.object({
  entries: z.array(z.object({ label: z.string().nullable(), text: z.string() })),
});

/// 复制引用（spec 2.1.3）：catalog 用 CSL 样式生成，门户只转发。编号样式按请求顺序带 `label`，APA 按作者
/// 字母序、`label` 为 null。公共数据，不带会话
export async function POST(request: Request): Promise<NextResponse> {
  if (!isSameOrigin(request)) {
    return forbidden();
  }
  const body = bodySchema.safeParse(await readBody(request));
  if (!body.success) {
    return invalidRequest();
  }
  try {
    const res = await gatewayFetch(ENDPOINT, { method: "POST", json: body.data });
    const payload = await readJson(res);
    const parsed = res.ok ? responseSchema.safeParse(payload) : undefined;
    if (parsed?.success) {
      return NextResponse.json({ entries: parsed.data.entries }, { headers: NO_STORE });
    }
    logUnexpectedResponse(ENDPOINT, res.status, payload);
  } catch (error) {
    logRequestError(ENDPOINT, error);
  }
  return NextResponse.json({ reason: "unavailable" }, { status: 502, headers: NO_STORE });
}
