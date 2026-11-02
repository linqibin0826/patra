import "server-only";
import type { z } from "zod";
import {
  type AlsoReadItem,
  alsoReadSchema,
  articleStatsSchema,
  type HotReads,
  hotReadsSchema,
} from "@/lib/analytics/insight-schema";
import { logRequestError, logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { type GatewayRequestInit, gatewayFetch } from "@/lib/gateway";
import { isEntityId } from "@/lib/library/params";

/// 计数与推荐是附加信息：比页面主体的 5 秒短，取不到就不显示
const SHORT_TIMEOUT_MS = 2_000;
/// 热读是首页一个标签的主体
const HOT_TIMEOUT_MS = 5_000;
/// 计数接口一次最多 100 个 ID
const BATCH_SIZE = 100;

/// 一篇文献的看过人数、读过全文人数，以及是否在热读榜前 100 名
export interface ArticleStats {
  viewerCount: number;
  fulltextReaderCount: number;
  hot: boolean;
}

/// 一屏文献的计数，按文献 ID。不在表里的当 0
export type ArticleStatsMap = ReadonlyMap<string, ArticleStats>;

/// 调行为数据服务的公开接口（不带会话）：200 且形状对返回数据，其余记日志返回 null，不抛错
async function read<S extends z.ZodType>(
  path: string,
  schema: S,
  init: GatewayRequestInit,
): Promise<z.infer<S> | null> {
  const endpoint = `/patra-analytics${path}`;
  try {
    const res = await gatewayFetch(endpoint, init);
    const body: unknown = res.status === 200 ? await res.json() : undefined;
    const parsed = res.status === 200 ? schema.safeParse(body) : undefined;
    if (!parsed?.success) {
      logUnexpectedResponse(endpoint, res.status, body);
      return null;
    }
    return parsed.data;
  } catch (error) {
    logRequestError(endpoint, error);
    return null;
  }
}

/// 一屏文献的看过、读过全文与热读标记（PAP-170 §1）：去重、按 100 个一批并行取；任何一批失败整屏返回 null，
/// 不出现半屏有数
export async function fetchArticleStats(ids: readonly string[]): Promise<ArticleStatsMap | null> {
  const unique = [...new Set(ids)];
  if (unique.length === 0) {
    return new Map();
  }
  const batches: string[][] = [];
  for (let start = 0; start < unique.length; start += BATCH_SIZE) {
    batches.push(unique.slice(start, start + BATCH_SIZE));
  }
  const pages = await Promise.all(
    batches.map((articleIds) =>
      read("/articles/stats", articleStatsSchema, {
        method: "POST",
        json: { articleIds },
        timeoutMs: SHORT_TIMEOUT_MS,
      }),
    ),
  );
  if (pages.some((page) => page === null)) {
    return null;
  }
  return new Map(
    pages.flatMap((page) =>
      (page?.items ?? []).map(({ articleId, ...stats }) => [articleId, stats] as const),
    ),
  );
}

/// 首页热读（§2）：最多 14 条，上榜在前、补位在后；任务从没跑过时是空列表
export async function fetchHotReads(): Promise<HotReads | null> {
  const data = await read("/hot-reads", hotReadsSchema, { timeoutMs: HOT_TIMEOUT_MS });
  return data && { rankedTotal: data.rankedTotal, items: data.items };
}

/// 看过这篇的人还看了（§3）：最多 6 条，顺序即展示顺序
export async function fetchAlsoRead(articleId: string): Promise<AlsoReadItem[] | null> {
  if (!isEntityId(articleId)) {
    return null;
  }
  const data = await read(`/articles/${articleId}/also-read`, alsoReadSchema, {
    timeoutMs: SHORT_TIMEOUT_MS,
  });
  return data?.items ?? null;
}
