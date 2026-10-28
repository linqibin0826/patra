import "server-only";
import type { z } from "zod";
import {
  type Conversation,
  conversationSchema,
  type DiscussionSort,
  type MentionSuggestion,
  mentionSuggestionsSchema,
  type Replies,
  type Revisions,
  repliesSchema,
  revisionsSchema,
  type SingleThread,
  type Subject,
  singleThreadSchema,
  sourceSchema,
  subjectTypeSchema,
  type ThreadPage,
  threadPageSchema,
} from "@/lib/interaction/discussion-schema";
import { READ_TIMEOUT_MS, type Read, readPublic, toRead } from "@/lib/interaction/read";
import { callInteraction } from "@/lib/interaction/request";
import { isEntityId } from "@/lib/library/params";

const NOT_FOUND = { kind: "not-found" } as const;

/// 对象参数合格：类型是文献或投稿经验，key 是实体 ID
export function isSubject(type: unknown, key: unknown): boolean {
  return subjectTypeSchema.safeParse(type).success && typeof key === "string" && isEntityId(key);
}

function subjectPath(subject: Subject): string {
  return `/discussions/${subject.type}/${subject.key}`;
}

/// 要登录的读（编辑原文、@ 联想）
async function readSignedIn<S extends z.ZodType>(
  path: string,
  schema: S,
): Promise<Read<z.infer<S>>> {
  return toRead(
    await callInteraction(path, { timeoutMs: READ_TIMEOUT_MS }, { auth: "required" }),
    schema,
    path,
  );
}

function withCursor(path: string, cursor: string | undefined): string {
  return cursor
    ? `${path}${path.includes("?") ? "&" : "?"}cursor=${encodeURIComponent(cursor)}`
    : path;
}

/// 一页楼（每页 20 楼，每楼带前 3 条回复）
export async function fetchThreads(
  subject: Subject,
  sort: DiscussionSort,
  cursor?: string,
): Promise<Read<ThreadPage>> {
  if (!isSubject(subject.type, subject.key)) {
    return NOT_FOUND;
  }
  return readPublic(
    withCursor(`${subjectPath(subject)}/threads?sort=${sort}`, cursor),
    threadPageSchema,
  );
}

/// 单楼：目标所在的整楼；目标不在这个对象下或已整条去掉是 `not-found`
export async function fetchSingleThread(
  subject: Subject,
  commentId: string,
): Promise<Read<SingleThread>> {
  if (!isSubject(subject.type, subject.key) || !isEntityId(commentId)) {
    return NOT_FOUND;
  }
  return readPublic(`${subjectPath(subject)}/comments/${commentId}/thread`, singleThreadSchema);
}

/// 「展开更多回复」：一楼剩下的回复，正序，每次最多 50 条
export async function fetchReplies(rootId: string, cursor?: string): Promise<Read<Replies>> {
  if (!isEntityId(rootId)) {
    return NOT_FOUND;
  }
  return readPublic(withCursor(`/comments/${rootId}/replies`, cursor), repliesSchema);
}

/// 「查看对话」：从根评论到这一条的回复链
export async function fetchConversation(commentId: string): Promise<Read<Conversation>> {
  return isEntityId(commentId)
    ? readPublic(`/comments/${commentId}/conversation`, conversationSchema)
    : NOT_FOUND;
}

/// 修改历史（公开）
export async function fetchRevisions(commentId: string): Promise<Read<Revisions>> {
  return isEntityId(commentId)
    ? readPublic(`/comments/${commentId}/revisions`, revisionsSchema)
    : NOT_FOUND;
}

/// 编辑原文（作者本人）：`<@ID>` 已换回 `@当前用户名`
export async function fetchSource(commentId: string): Promise<Read<{ body: string }>> {
  return isEntityId(commentId)
    ? readSignedIn(`/comments/${commentId}/source`, sourceSchema)
    : NOT_FOUND;
}

/// @ 联想：`q` 是 `@` 后面已输入的原文，可空（空时只有参与者）
export async function fetchMentionSuggestions(
  subject: Subject,
  q: string,
): Promise<Read<MentionSuggestion[]>> {
  if (!isSubject(subject.type, subject.key)) {
    return NOT_FOUND;
  }
  const read = await readSignedIn(
    `${subjectPath(subject)}/mention-suggestions?q=${encodeURIComponent(q)}`,
    mentionSuggestionsSchema,
  );
  return read.kind === "ok" ? { kind: "ok", data: read.data.items } : read;
}
