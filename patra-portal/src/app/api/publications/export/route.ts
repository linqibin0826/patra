import { NextResponse } from "next/server";
import { z } from "zod";
import { logRequestError, logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { gatewayStream } from "@/lib/gateway";
import { graphemeCount, graphemes } from "@/lib/grapheme";
import { isEntityId } from "@/lib/library/params";
import { forbidden, invalidRequest, NO_STORE, readBody } from "@/lib/route-reply";
import { isSameOrigin } from "@/lib/same-origin";

export const dynamic = "force-dynamic";

const ENDPOINT = "/patra-catalog/portal/publications/export";

/// catalog 的 `name` 限 100（按 UTF-16 码元）。收藏夹名按字素可到 100 个（超过 100 个字素的拒绝），emoji 多时码元超出：按字素截，
/// 不切坏字符；名字只用于文件名，截短无妨
function fitName(name: string): string {
  let fitted = "";
  for (const part of graphemes(name)) {
    if (fitted.length + part.length > 100) {
      break;
    }
    fitted += part;
  }
  return fitted;
}

const bodySchema = z.object({
  ids: z.array(z.string().refine(isEntityId)).min(1).max(5000),
  format: z.enum(["ris", "bibtex", "csv", "csl-json"]),
  name: z
    .string()
    .refine((value) => graphemeCount(value) <= 100)
    .transform(fitName)
    .optional(),
});

/// 导出（spec 2.1.3）：浏览器 → 这里 → 网关 → catalog。公共数据，不带会话；文件名由 catalog 生成。
/// 响应体原样流式转发，只透传类型与 `Content-Disposition`；超时只计到响应头（导出 5,000 篇要写一阵）
export async function POST(request: Request): Promise<Response> {
  if (!isSameOrigin(request)) {
    return forbidden();
  }
  const body = bodySchema.safeParse(await readBody(request));
  if (!body.success) {
    return invalidRequest();
  }
  try {
    const res = await gatewayStream(ENDPOINT, {
      method: "POST",
      json: body.data,
      signal: request.signal,
    });
    if (!res.ok || !res.body) {
      logUnexpectedResponse(ENDPOINT, res.status, undefined);
      return NextResponse.json({ reason: "unavailable" }, { status: 502, headers: NO_STORE });
    }
    const headers = new Headers(NO_STORE);
    for (const name of ["Content-Type", "Content-Disposition"]) {
      const value = res.headers.get(name);
      if (value) {
        headers.set(name, value);
      }
    }
    return new Response(res.body, { status: 200, headers });
  } catch (error) {
    logRequestError(ENDPOINT, error);
    return NextResponse.json({ reason: "unavailable" }, { status: 502, headers: NO_STORE });
  }
}
