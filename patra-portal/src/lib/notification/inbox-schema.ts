import { z } from "zod";
import { FINAL_OUTCOMES, FIRST_DECISIONS } from "@/lib/experience/labels";
import { cardSchema, subjectTypeSchema } from "@/lib/interaction/discussion-schema";

/// 通知服务信箱接口（PAP-143 契约第 1 节、PAP-144 契约第 2 节）的响应体。ID 一律是字符串，时间是 UTC ISO 8601。
/// 不加 `server-only`：客户端组件要用这些类型。

const count = z.number().int().nonnegative();

/// 未读数：铃铛与账号菜单用 `total`（= 回复 + @，不含有帮助），三个标签上的胶囊各用自己的数
export const unreadCountsSchema = z.object({
  total: count,
  replies: count,
  mentions: count,
  helpful: count,
});
export type UnreadCounts = z.infer<typeof unreadCountsSchema>;

/// 提醒列表的三个标签：「回复我的」「@ 我的」「有帮助」
export const notificationTabSchema = z.enum(["replies", "mentions", "helpful"]);
export type NotificationTab = z.infer<typeof notificationTabSchema>;

/// 五种动作：回复了你 / 评论了你的投稿经验 / @ 了你 / 觉得你的评论有帮助 / 觉得你的投稿经验有帮助
export const notificationTypeSchema = z.enum([
  "COMMENT_REPLY",
  "POST_COMMENT",
  "MENTION",
  "COMMENT_HELPFUL",
  "POST_HELPFUL",
]);
export type NotificationType = z.infer<typeof notificationTypeSchema>;

/// 经验的结果字段：有帮助点了没有正文的经验时，门户用它写结果行（同经验列表）
export const notificationExperienceSchema = z.object({
  firstDecision: z.enum(FIRST_DECISIONS),
  finalOutcome: z.enum(FINAL_OUTCOMES),
  firstRoundDays: count,
  totalDays: count.nullable(),
});
export type NotificationExperience = z.infer<typeof notificationExperienceSchema>;

/// 提醒行的对象上下文：`key` 为 null 时不渲染链接；`available` 为假时文献已下线（此时没有摘录）；`experience` 只有经验有。
/// `experience` 解析不了（后端加了新的枚举值）时当作没有：它只用来给没有正文的经验写结果行，不能让一个新值拖垮整页
export const notificationSubjectSchema = z.object({
  type: subjectTypeSchema,
  key: z.string().nullable(),
  available: z.boolean(),
  title: z.string().nullable(),
  journal: z.string().nullable(),
  abbreviation: z.string().nullable(),
  submittedMonth: z.string().nullable(),
  mine: z.boolean(),
  experience: notificationExperienceSchema.nullable().catch(null),
});
export type NotificationSubject = z.infer<typeof notificationSubjectSchema>;

/// 一条提醒。`actorAnonymous` 为真时 `actor` 恒为 null；有帮助条目的 `actor` 是最近一位点的人、`actorCount` 是点过的人数
/// （回复与 @ 恒为 1）；`occurredAt` 是排序时间，有帮助再被点时会变；`commentId` 在有帮助点经验时为空；`excerpt` 是
/// 后端给的纯文本（≤ 120 字素），有帮助条目是你自己那条内容的摘录
export const notificationSchema = z.object({
  notificationId: z.string().min(1),
  type: notificationTypeSchema,
  read: z.boolean(),
  occurredAt: z.string(),
  actor: cardSchema.nullable(),
  actorAnonymous: z.boolean(),
  actorCount: z.number().int().positive(),
  subject: notificationSubjectSchema.nullable(),
  commentId: z.string().min(1).nullable(),
  excerpt: z.string().nullable(),
  excerptTruncated: z.boolean(),
});
export type Notification = z.infer<typeof notificationSchema>;

/// 一页提醒（每页 20 条，服务端按 `occurredAt` 倒序）；`nextCursor` 不透明，为 null 即没有下一页
export const notificationPageSchema = z.object({
  items: z.array(notificationSchema),
  nextCursor: z.string().nullable(),
});
export type NotificationPage = z.infer<typeof notificationPageSchema>;
