import { z } from "zod";

/// `POST /articles/stats` 的响应：每个 ID 一行（PAP-170 接口契约 §1）
export const articleStatsSchema = z.object({
  items: z.array(
    z.object({
      articleId: z.string(),
      viewerCount: z.number().int().nonnegative(),
      fulltextReaderCount: z.number().int().nonnegative(),
      hot: z.boolean(),
    }),
  ),
});

/// `GET /hot-reads` 的响应（§2）：上榜在前、最新收录补位在后，最多 14 条
export const hotReadsSchema = z.object({
  computedAt: z.string().nullable(),
  rankedTotal: z.number().int().nonnegative(),
  items: z.array(
    z.object({
      articleId: z.string(),
      source: z.enum(["RANKED", "LATEST"]),
      rank: z.number().int().positive().nullable(),
    }),
  ),
});

/// `GET /articles/{id}/also-read` 的响应（§3）：最多 6 条，已按显示顺序排好
export const alsoReadSchema = z.object({
  items: z.array(
    z.object({
      articleId: z.string(),
      source: z.enum(["CO_READ", "SAME_MESH", "SAME_VENUE"]),
      meshTerm: z.string().nullable(),
      venueName: z.string().nullable(),
    }),
  ),
});

export type HotReads = Omit<z.infer<typeof hotReadsSchema>, "computedAt">;
export type AlsoReadItem = z.infer<typeof alsoReadSchema>["items"][number];
