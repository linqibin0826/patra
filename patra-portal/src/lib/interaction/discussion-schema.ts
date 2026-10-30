import { z } from "zod";

/// interaction 讨论接口（后端工程 spec 2026-10-27-interaction-discussion-design 第 6 节）的响应体。ID 一律是字符串
/// （Long → String），时间是 UTC ISO 8601。不加 `server-only`：客户端组件要用这些类型。

const id = z.string().min(1);

/// 可讨论的对象：文献（`key` 是 catalog 文献 ID）、投稿经验（`key` 是经验 ID）
export const subjectTypeSchema = z.enum(["publication", "post"]);
export type SubjectType = z.infer<typeof subjectTypeSchema>;

/// 讨论挂在哪个对象下
export interface Subject {
  type: SubjectType;
  key: string;
}

/// 根评论的排序：正序（默认）/ 最新 / 最有帮助
export type DiscussionSort = "asc" | "latest" | "helpful";

/// 作者名片（与 PAP-122 的作者对象同形）。`profileVisible` 为假时 `username`、`avatarUrl` 为 null（后端 5.8）；
/// 已注销时 `state = DELETED`，名字也为 null
export const cardSchema = z.object({
  userId: id,
  username: z.string().nullable(),
  displayName: z.string().nullable(),
  avatarUrl: z.string().nullable(),
  avatarPalette: z.number().int(),
  state: z.enum(["ACTIVE", "BANNED", "PENDING_DELETION", "DELETED"]),
  profileVisible: z.boolean(),
});
export type Card = z.infer<typeof cardSchema>;

/// 正文里 `<@用户ID>` 标记对应的人。`username` 为 null：主页对访客不可见，显示 `@昵称`、不链接；`deleted`：已注销
export const mentionSchema = z.object({
  userId: id,
  username: z.string().nullable(),
  displayName: z.string().nullable(),
  deleted: z.boolean(),
});
export type Mention = z.infer<typeof mentionSchema>;

/// 「回复 @X」的对象：被回复那条已删除、是匿名作者，或者是谁（identity 省略时 `author` 为 null）
export const replyToSchema = z.object({
  deleted: z.boolean().default(false),
  anonymous: z.boolean().default(false),
  author: cardSchema.nullable().default(null),
});

/// 一条评论（后端 6.4）。占位只有 `commentId`、`rootId`、`parentId`、`deleted: true`、`hasReplies`、`createdAt`。
/// `helpfulCount` 是有帮助数（占位为 0），`helpful` 是访客点过没有（没有会话、占位为 null）
export const commentSchema = z.object({
  commentId: id,
  rootId: id,
  parentId: id.nullable(),
  deleted: z.boolean(),
  author: cardSchema.nullable(),
  anonymous: z.boolean(),
  isOwner: z.boolean(),
  mine: z.boolean(),
  replyTo: replyToSchema.nullable(),
  body: z.string().nullable(),
  mentions: z.array(mentionSchema),
  hasReplies: z.boolean(),
  createdAt: z.string(),
  edited: z.boolean(),
  helpfulCount: z.number().int().nonnegative(),
  helpful: z.boolean().nullable(),
});
export type Comment = z.infer<typeof commentSchema>;

/// 一楼：根评论 + 回复（列表里是前 3 条，单楼里是全部）
export const threadSchema = z.object({
  root: commentSchema,
  replies: z.array(commentSchema),
  hasMoreReplies: z.boolean(),
  repliesCursor: z.string().nullable(),
});
export type Thread = z.infer<typeof threadSchema>;

export const threadPageSchema = z.object({
  items: z.array(threadSchema),
  nextCursor: z.string().nullable(),
});
export type ThreadPage = z.infer<typeof threadPageSchema>;

export const singleThreadSchema = z.object({ targetId: id, thread: threadSchema });
export type SingleThread = z.infer<typeof singleThreadSchema>;

export const repliesSchema = z.object({
  items: z.array(commentSchema),
  nextCursor: z.string().nullable(),
});
export type Replies = z.infer<typeof repliesSchema>;

export const conversationSchema = z.object({ targetId: id, items: z.array(commentSchema) });
export type Conversation = z.infer<typeof conversationSchema>;

/// 修改历史：最新在前，最后一个是最初版本；`mentions` 是全部版本里标记的并集
export const revisionsSchema = z.object({
  versions: z.array(z.object({ body: z.string(), at: z.string() })),
  mentions: z.array(mentionSchema),
});
export type Revisions = z.infer<typeof revisionsSchema>;

/// 编辑原文：`<@ID>` 已换回 `@当前用户名`
export const sourceSchema = z.object({ body: z.string() });

/// @ 联想的一行：`PARTICIPANT` 是本讨论的参与者，`OTHER` 是 identity 的全站联想
export const mentionSuggestionSchema = z.object({
  userId: id,
  username: z.string(),
  displayName: z.string(),
  avatarUrl: z.string().nullable(),
  avatarPalette: z.number().int(),
  group: z.enum(["PARTICIPANT", "OTHER"]),
});
export type MentionSuggestion = z.infer<typeof mentionSuggestionSchema>;

export const mentionSuggestionsSchema = z.object({ items: z.array(mentionSuggestionSchema) });

/// 删除的结果：留了占位，或者整条去掉的 ID（这条与级联去掉的占位祖先）
export const deleteResultSchema = z.object({
  placeholder: z.boolean(),
  removedIds: z.array(id),
});
export type DeleteResult = z.infer<typeof deleteResultSchema>;
