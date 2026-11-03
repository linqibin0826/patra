import { z } from "zod";

/// 行为数据服务的「我的阅读历史」一页（阅读历史设计 §6.1）：最近一次打开在前，一页 30 条
export const readingHistoryPageSchema = z.object({
  recordingEnabled: z.boolean(),
  items: z.array(
    z.object({ articleId: z.string(), lastReadAt: z.string(), fulltextRead: z.boolean() }),
  ),
  nextCursor: z.string().nullable(),
});

/// 阅读记录开关（§6.3）
export const recordingSchema = z.object({ enabled: z.boolean() });

/// 历史里的一条，配好了卡片。`paper` 为 null：这篇已不在 catalog 里（下架或合并），页面照样列出、可删
export interface HistoryEntry {
  articleId: string;
  lastReadAt: string;
  fulltextRead: boolean;
  paper: { title: string; journal: string | null; year: string | null } | null;
}

/// 阅读历史页的一页
export interface HistoryPage {
  recordingEnabled: boolean;
  items: HistoryEntry[];
  nextCursor: string | null;
}
