import { z } from "zod";

const count = z.number().int().nonnegative();

/// 批量对象统计（`POST /subjects/stats`）：每个请求的 key 一行，没人互动过为 0
export const subjectStatsSchema = z.object({
  items: z.array(z.object({ key: z.string(), collectorCount: count, discussionCount: count })),
});

/// 主页的获赞（`GET /users/{userId}/helpful-received`）
export const helpfulReceivedSchema = z.object({ helpfulReceived: count });
