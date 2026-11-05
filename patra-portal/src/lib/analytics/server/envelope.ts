import "server-only";
import { SCHEMA_VERSIONS } from "@/lib/analytics/generated/events";
import { newId, truncatePath } from "@/lib/analytics/ids";
import type { RequestFacts } from "@/lib/analytics/server/context";
import type { CollectEnvelope, PageType, ServerEventInput } from "@/lib/analytics/types";

/// 契约里 `user_agent` 的上限
const MAX_USER_AGENT = 1024;
/// 取不到合法 IP 时的占位：保留地址，采集端点查出来地区为 null（契约要求 `client_ip` 必填）
const UNKNOWN_IP = "0.0.0.0";

/// 服务端事件发生在哪个页面
export interface EventPage {
  path: string;
  type: PageType;
}

/// 浏览器事件只取这几个字段，其余（身份、来源、合成标记……）一律由服务端填
const CLIENT_FIELDS = [
  "event_id",
  "event_name",
  "schema_version",
  "created_at",
  "page_path",
  "page_type",
  "properties",
] as const;

/// 转发的浏览器事件：服务端填的字段有确定类型，浏览器给的原样带过，由采集端点按 schema 逐条校验
export type ForwardedEnvelope = Omit<CollectEnvelope, (typeof CLIENT_FIELDS)[number]> &
  Record<(typeof CLIENT_FIELDS)[number], unknown>;

/// 信封里由门户服务端填的部分
function serverFields(facts: RequestFacts, userId: string | null, sentAt: string) {
  return {
    sent_at: sentAt,
    device_id: facts.deviceId,
    session_id: facts.sessionId,
    user_id: userId,
    is_synthetic: false as const,
    client_ip: facts.client.ip ?? UNKNOWN_IP,
    user_agent: (facts.client.userAgent ?? "").slice(0, MAX_USER_AGENT),
  };
}

/// 门户服务端自己产生的事件 → 采集信封
export function buildServerEnvelope(
  event: ServerEventInput,
  facts: RequestFacts,
  userId: string | null,
  page: EventPage,
  now: Date,
): CollectEnvelope {
  const at = now.toISOString();
  return {
    event_id: newId(),
    event_name: event.name,
    schema_version: SCHEMA_VERSIONS[event.name],
    source: "server",
    created_at: at,
    page_path: truncatePath(page.path),
    page_type: page.type,
    referrer_host: facts.referrerHost,
    properties: event.properties,
    ...serverFields(facts, userId, at),
  };
}

/// 浏览器批次里的一条 → 采集信封。按白名单取字段：浏览器给的任何身份字段都不带过去（阅读历史设计 §2 的硬约束），
/// 身份只按服务端会话与 Cookie 填；浏览器事件没有外部来源（`browser-event` 不许带）
export function toCollectEnvelope(
  raw: Record<string, unknown>,
  sentAt: string,
  facts: RequestFacts,
  userId: string | null,
): ForwardedEnvelope {
  const picked = Object.fromEntries(CLIENT_FIELDS.map((field) => [field, raw[field]])) as Record<
    (typeof CLIENT_FIELDS)[number],
    unknown
  >;
  return {
    ...picked,
    page_path:
      typeof picked.page_path === "string" ? truncatePath(picked.page_path) : picked.page_path,
    source: "client",
    referrer_host: null,
    ...serverFields(facts, userId, sentAt),
  };
}
