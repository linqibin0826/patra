import "server-only";
import type { z } from "zod";
import { logRequestError, logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { callInteraction, type InteractionReply } from "@/lib/interaction/request";
import type { Page } from "@/lib/interaction/schema";
import { fetchPublicationCards } from "@/lib/portal-api/publications";
import type { Paper } from "@/types/portal";

/// 读 interaction 的结果。不抛错：互动区块失败只降级，不拖垮页面（决策 H）。
/// - `signed-out`：没有会话或会话失效（RSC 里不能清 Cookie，交给调用方跳登录）
/// - `not-found`：不存在或对这位访客不可见（interaction 不区分原因）
/// - `unavailable`：超时、5xx、响应形状不对、catalog 卡片取不到
export type Read<T> =
  | { kind: "ok"; data: T }
  | { kind: "signed-out" }
  | { kind: "not-found" }
  | { kind: "unavailable" };

/// interaction 读接口的默认超时：页面主体数据 5 秒（简报 §3.17）
export const READ_TIMEOUT_MS = 5_000;

/// 一次 interaction 回复 → `Read`：200 且形状对是 `ok`
export function toRead<S extends z.ZodType>(
  reply: InteractionReply,
  schema: S,
  endpoint: string,
): Read<z.infer<S>> {
  if (reply.kind === "no-session") {
    return { kind: "signed-out" };
  }
  if (reply.kind === "error") {
    return { kind: "unavailable" };
  }
  if (reply.kind === "problem") {
    if (reply.problem.status === 401) {
      return { kind: "signed-out" };
    }
    if (reply.problem.status === 404) {
      return { kind: "not-found" };
    }
    logUnexpectedResponse(endpoint, reply.problem.status, { code: reply.problem.code });
    return { kind: "unavailable" };
  }
  const parsed = reply.res.status === 200 ? schema.safeParse(reply.body) : undefined;
  if (!parsed?.success) {
    logUnexpectedResponse(endpoint, reply.res.status, reply.body);
    return { kind: "unavailable" };
  }
  return { kind: "ok", data: parsed.data };
}

/// 公开读（登录可选）：有会话就带（拿「这条是我发的」、本人的匿名内容与主页可见性）；令牌被网关拒时按访客
/// 重取一次，不降级。讨论区与投稿经验共用
export async function readPublic<S extends z.ZodType>(
  path: string,
  schema: S,
): Promise<Read<z.infer<S>>> {
  const init = { timeoutMs: READ_TIMEOUT_MS };
  const reply = await callInteraction(path, init, { auth: "optional" });
  if (reply.kind === "problem" && reply.problem.status === 401) {
    return toRead(await callInteraction(path, init, { auth: "none" }), schema, path);
  }
  return toRead(reply, schema, path);
}

/// 条目与它的卡片。`paper` 为 null：条目已失效，或 catalog 这次没回它（刚软删、对账还没跑），按已失效外观显示
export interface WithPaper<I> {
  item: I;
  paper: Paper | null;
}

/// 给一页条目配卡片：只为 `ACTIVE` 且有 `key` 的取，一页一次批量请求；卡片取不到时整页 `unavailable`
export async function attachPapers<I extends { key: string | null; state: string }>(
  page: Page<I>,
): Promise<Read<Page<WithPaper<I>>>> {
  const keys = [
    ...new Set(
      page.items.flatMap((item) => (item.state === "ACTIVE" && item.key ? [item.key] : [])),
    ),
  ];
  let papers: Paper[];
  try {
    papers = await fetchPublicationCards(keys);
  } catch (error) {
    logRequestError("/patra-catalog/portal/publications/batch", error);
    return { kind: "unavailable" };
  }
  const byId = new Map(papers.map((paper) => [paper.id, paper]));
  return {
    kind: "ok",
    data: {
      ...page,
      items: page.items.map((item) => ({
        item,
        paper: item.state === "ACTIVE" && item.key ? (byId.get(item.key) ?? null) : null,
      })),
    },
  };
}
