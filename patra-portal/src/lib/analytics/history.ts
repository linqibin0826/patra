import "server-only";
import {
  type HistoryPage,
  readingHistoryPageSchema,
  recordingSchema,
} from "@/lib/analytics/history-schema";
import { callAnalytics } from "@/lib/analytics/request";
import { logRequestError } from "@/lib/auth/gateway-response";
import { READ_TIMEOUT_MS, type Read, toRead } from "@/lib/interaction/read";
import { fetchPublicationCards } from "@/lib/portal-api/publications";
import { publishedYear } from "@/lib/published-date";

const HISTORY = "/me/reading-history";

/// 我的阅读历史一页：行为数据服务只给文献 ID，标题、刊名、年份由 catalog 批量配（阅读历史设计 §6.4）。catalog 没回的
/// 那篇 `paper` 为 null。历史或卡片任一取不到都是 `unavailable`（页面整体降级）；坏游标（422）同样按取不到处理
export async function fetchReadingHistory(cursor?: string): Promise<Read<HistoryPage>> {
  const path = cursor ? `${HISTORY}?cursor=${encodeURIComponent(cursor)}` : HISTORY;
  const read = toRead(
    await callAnalytics(path, { timeoutMs: READ_TIMEOUT_MS }),
    readingHistoryPageSchema,
    `/patra-analytics${HISTORY}`,
  );
  if (read.kind !== "ok") {
    return read;
  }
  const ids = read.data.items.map((item) => item.articleId);
  let papers: Awaited<ReturnType<typeof fetchPublicationCards>>;
  try {
    papers = await fetchPublicationCards(ids);
  } catch (error) {
    logRequestError("/patra-catalog/portal/publications/batch", error);
    return { kind: "unavailable" };
  }
  const byId = new Map(papers.map((paper) => [paper.id, paper]));
  return {
    kind: "ok",
    data: {
      ...read.data,
      items: read.data.items.map((item) => {
        const paper = byId.get(item.articleId);
        return {
          ...item,
          paper: paper
            ? { title: paper.title, journal: paper.journal, year: publishedYear(paper) }
            : null,
        };
      }),
    },
  };
}

/// 阅读记录开关（没设过是开）
export async function fetchReadingRecording(): Promise<Read<{ enabled: boolean }>> {
  const path = `${HISTORY}/recording`;
  return toRead(
    await callAnalytics(path, { timeoutMs: READ_TIMEOUT_MS }),
    recordingSchema,
    `/patra-analytics${path}`,
  );
}
