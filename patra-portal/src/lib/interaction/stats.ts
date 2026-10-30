import "server-only";
import { toRead } from "@/lib/interaction/read";
import { callInteraction } from "@/lib/interaction/request";
import { helpfulReceivedSchema, subjectStatsSchema } from "@/lib/interaction/stats-schema";

/// 一篇文献的公开计数
export interface PaperStats {
  collectorCount: number;
  discussionCount: number;
}

/// 一屏文献的公开计数，按文献 ID
export type PaperStatsMap = ReadonlyMap<string, PaperStats>;

/// 计数是附加信息：比页面主体的 5 秒短，取不到就不显示（简报 §3.12「短超时」）
export const STATS_TIMEOUT_MS = 2_000;

/// 后端一次最多 100 个 key
const BATCH_SIZE = 100;

/// 一屏文献的收藏人数与讨论数（决策 C）：不带会话，按 100 个一批并行取。任何一批失败（超时、5xx、响应不对、
/// 网络错误）整屏返回 null，不出现半屏有数；不抛错，失败已记日志
export async function fetchPaperStats(keys: readonly string[]): Promise<PaperStatsMap | null> {
  const unique = [...new Set(keys)];
  const batches: string[][] = [];
  for (let start = 0; start < unique.length; start += BATCH_SIZE) {
    batches.push(unique.slice(start, start + BATCH_SIZE));
  }
  const pages = await Promise.all(batches.map(fetchBatch));
  if (pages.some((page) => page === null)) {
    return null;
  }
  return new Map(
    pages.flatMap((page) =>
      (page ?? []).map((item) => [
        item.key,
        { collectorCount: item.collectorCount, discussionCount: item.discussionCount },
      ]),
    ),
  );
}

/// 一批（≤ 100 个 key）
async function fetchBatch(keys: string[]) {
  const path = "/subjects/stats";
  const reply = await callInteraction(
    path,
    { method: "POST", json: { type: "publication", keys }, timeoutMs: STATS_TIMEOUT_MS },
    { auth: "none" },
  );
  const read = toRead(reply, subjectStatsSchema, path);
  return read.kind === "ok" ? read.data.items : null;
}

/// 主页的获赞：不带会话；取不到返回 null，失败已记日志
export async function fetchHelpfulReceived(userId: string): Promise<number | null> {
  const path = `/users/${userId}/helpful-received`;
  const reply = await callInteraction(path, { timeoutMs: STATS_TIMEOUT_MS }, { auth: "none" });
  const read = toRead(reply, helpfulReceivedSchema, path);
  return read.kind === "ok" ? read.data.helpfulReceived : null;
}
