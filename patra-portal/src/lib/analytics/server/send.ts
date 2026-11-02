import "server-only";
import { after } from "next/server";
import type { RequestFacts } from "@/lib/analytics/server/context";
import {
  buildServerEnvelope,
  type EventPage,
  type ForwardedEnvelope,
} from "@/lib/analytics/server/envelope";
import type { CollectEnvelope, ServerEventInput } from "@/lib/analytics/types";
import { lookupSessionByToken } from "@/lib/auth/current-user";
import { gatewayFetch } from "@/lib/gateway";

const ENDPOINT = "/patra-analytics/events";
/// 采集端点 Kafka 不可用时最长约 5 秒返回 503，门户多等 1 秒（采集设计 §4.5）
const TIMEOUT_MS = 6_000;

/// 当前用户的 ID；没登录、令牌失效、identity 不可用都是 null（事件照发）
export async function resolveUserId(facts: RequestFacts): Promise<string | null> {
  const session = await lookupSessionByToken(facts.token, { client: facts.client });
  return session.status === "authenticated" ? session.user.userId : null;
}

/// 把一批信封发给采集端点。202 就结束；其他状态码、超时、网络错误都丢弃这一批、不重试（采集设计 §2.3），
/// 只打一行警告（不带事件内容）。永远不抛
export async function sendEnvelopes(
  envelopes: readonly (CollectEnvelope | ForwardedEnvelope)[],
): Promise<void> {
  try {
    const res = await gatewayFetch(ENDPOINT, {
      method: "POST",
      json: { events: envelopes },
      timeoutMs: TIMEOUT_MS,
    });
    if (res.status !== 202) {
      console.warn(`埋点：采集端点回 ${res.status}，丢弃 ${envelopes.length} 条`);
    }
    await res.body?.cancel();
  } catch (error) {
    const name = error instanceof Error ? error.name : "unknown";
    console.warn(`埋点：采集端点不可达（${name}），丢弃 ${envelopes.length} 条`);
  }
}

/// 发一个服务端事件：排到响应之后（`after`）再问 identity、再发，不拖慢页面。没有埋点上下文、或这是 Server Action
/// 引起的回渲染时什么也不做
export function trackServerEvent(
  event: ServerEventInput,
  facts: RequestFacts | null,
  page: EventPage,
): void {
  if (!facts || facts.isActionRequest) {
    return;
  }
  const now = new Date();
  after(async () => {
    const userId = await resolveUserId(facts);
    await sendEnvelopes([buildServerEnvelope(event, facts, userId, page, now)]);
  });
}
