import { z } from "zod";
import { cardSchema, subjectTypeSchema } from "@/lib/interaction/discussion-schema";

/// interaction 提醒接口（后端工程 spec 2026-10-27-interaction-notification-design 第 9 节）的响应体。ID 一律是字符串，
/// 时间是 UTC ISO 8601。不加 `server-only`：客户端组件要用这些类型。

const count = z.number().int().nonnegative();

/// 未读数：徽标用 `total`，两个标签上的小数字用 `replies` / `mentions`
export const unreadCountsSchema = z.object({ total: count, replies: count, mentions: count });
export type UnreadCounts = z.infer<typeof unreadCountsSchema>;

/// 提醒列表的两个标签：「回复我的」「@ 我的」
export const notificationTabSchema = z.enum(["replies", "mentions"]);
export type NotificationTab = z.infer<typeof notificationTabSchema>;

/// 三种动作：回复了你的评论 / 评论了你的投稿经验 / 在评论中 @ 了你
export const notificationTypeSchema = z.enum(["COMMENT_REPLY", "POST_COMMENT", "MENTION"]);
export type NotificationType = z.infer<typeof notificationTypeSchema>;

/// 提醒行的对象上下文：`key` 为 null 时不渲染链接；`available` 为假时经验已删除或文献已下线（此时没有摘录）
export const notificationSubjectSchema = z.object({
  type: subjectTypeSchema,
  key: z.string().nullable(),
  available: z.boolean(),
  title: z.string().nullable(),
  journal: z.string().nullable(),
  abbreviation: z.string().nullable(),
  submittedMonth: z.string().nullable(),
  mine: z.boolean(),
});
export type NotificationSubject = z.infer<typeof notificationSubjectSchema>;

/// 一条提醒。`actorAnonymous` 为真时 `actor` 恒为 null；`excerpt` 是后端给的纯文本（≤ 120 字素）
export const notificationSchema = z.object({
  notificationId: z.string().min(1),
  type: notificationTypeSchema,
  read: z.boolean(),
  createdAt: z.string(),
  actor: cardSchema.nullable(),
  actorAnonymous: z.boolean(),
  subject: notificationSubjectSchema.nullable(),
  commentId: z.string().min(1),
  commentDeleted: z.boolean(),
  excerpt: z.string().nullable(),
  excerptTruncated: z.boolean(),
});
export type Notification = z.infer<typeof notificationSchema>;

/// 一页提醒（每页 20 条，新的在前）；`nextCursor` 为 null 即没有下一页
export const notificationPageSchema = z.object({
  items: z.array(notificationSchema),
  nextCursor: z.string().nullable(),
});
export type NotificationPage = z.infer<typeof notificationPageSchema>;
