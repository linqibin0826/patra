import { z } from "zod";

/// interaction「有帮助」接口（后端工程 spec 2026-10-29-reaction-helpful-design 第 6 节）。不加 `server-only`：
/// 客户端组件要用这些类型

/// 能点有帮助的目标：评论（根评论、楼内回复）与投稿经验，ID 是字符串
export const helpfulTargetSchema = z.object({
  type: z.enum(["comment", "post"]),
  id: z.string().min(1),
});
export type HelpfulTarget = z.infer<typeof helpfulTargetSchema>;

/// 点、取消的返回：操作后的「我点过没有」与有帮助数，门户用它校正乐观值
export const helpfulResultSchema = z.object({
  helpful: z.boolean(),
  helpfulCount: z.number().int().nonnegative(),
});
export type HelpfulResult = z.infer<typeof helpfulResultSchema>;
