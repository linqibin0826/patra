import { after } from "next/server";
import { z } from "zod";
import { readHandlerFacts } from "@/lib/analytics/server/context";
import { toCollectEnvelope } from "@/lib/analytics/server/envelope";
import { resolveUserId, sendEnvelopes } from "@/lib/analytics/server/send";
import { forbidden, NO_STORE } from "@/lib/route-reply";
import { isSameOrigin } from "@/lib/same-origin";

export const dynamic = "force-dynamic";

/// 浏览器一批最多 48 KiB（sendBeacon 的在途上限约 64 KiB），这里按 64 KiB 拒收
const MAX_BODY_BYTES = 64 * 1024;

/// 契约 defs 里的时间：UTC、毫秒
const TIMESTAMP =
  /^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]\.[0-9]{3}Z$/;

/// 浏览器批次（`browser-batch`）只校验外壳，单条事件由采集端点按 schema 逐条校验：一条坏事件只让它自己进 dlq
const batchSchema = z.strictObject({
  sent_at: z.string().regex(TIMESTAMP),
  events: z.array(z.record(z.string(), z.unknown())).min(1).max(50),
});

const status = (code: number) => new Response(null, { status: code, headers: NO_STORE });

/// 浏览器事件的第一方入口（埋点 spec §6）。收下立刻回 204；响应之后按服务端会话补上设备、会话、用户、IP、UA，
/// 组装成采集信封经网关转给采集端点——浏览器给的身份字段一律不用（阅读历史设计 §2）。采集端点不可用时丢弃，
/// 浏览器什么都不知道。路径不叫 analytics / ingest：拦截器的规则表认这类名字
export async function POST(request: Request): Promise<Response> {
  if (!isSameOrigin(request)) {
    return forbidden();
  }
  const text = await request.text();
  if (new TextEncoder().encode(text).length > MAX_BODY_BYTES) {
    return status(413);
  }
  let json: unknown;
  try {
    json = JSON.parse(text);
  } catch {
    return status(400);
  }
  const batch = batchSchema.safeParse(json);
  if (!batch.success) {
    return status(400);
  }
  const facts = await readHandlerFacts();
  after(async () => {
    const userId = await resolveUserId(facts);
    await sendEnvelopes(
      batch.data.events.map((event) => toCollectEnvelope(event, batch.data.sent_at, facts, userId)),
    );
  });
  return status(204);
}
