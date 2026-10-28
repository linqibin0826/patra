"use server";

import type { Problem } from "@/lib/identity/problem";
import { type CommonFailure, mutate, UNAVAILABLE } from "@/lib/interaction/action-failure";
import { isSubject } from "@/lib/interaction/discussion";
import {
  type Comment,
  commentSchema,
  deleteResultSchema,
  type Subject,
} from "@/lib/interaction/discussion-schema";
import { isEntityId } from "@/lib/library/params";

/// 写评论的失败（spec 4.3）。不带文案，组件按 `reason` 选文案（`lib/discussion/messages.ts`）
export type CommentFailure =
  | { ok: false; reason: "too-frequent" | "daily-limit"; retryAfterSeconds: number | null }
  | { ok: false; reason: "email-unverified" | "account-restricted" }
  | { ok: false; reason: "reply-target-gone" }
  | { ok: false; reason: "subject-gone" }
  | { ok: false; reason: "comment-gone" }
  | { ok: false; reason: "too-many-mentions" | "too-long" | "empty" | "invalid-character" }
  | { ok: false; reason: "forbidden" }
  | CommonFailure;

export type CommentResult = { ok: true; comment: Comment } | CommentFailure;

export type DeleteCommentResult =
  | { ok: true; placeholder: boolean; removedIds: string[] }
  | CommentFailure;

/// 422 `errors[]` 里正文的字段码 → reason
const BODY_ERRORS: Record<
  string,
  "too-many-mentions" | "too-long" | "empty" | "invalid-character"
> = {
  TOO_MANY_MENTIONS: "too-many-mentions",
  TOO_LONG: "too-long",
  REQUIRED: "empty",
  INVALID_CHARACTER: "invalid-character",
};

/// 后端的原因码（`traits[]`、`errors[]`）→ reason；认不出的交给 `mutate` 记日志按 `unavailable` 处理
function classify(problem: Problem): Exclude<CommentFailure, CommonFailure> | null {
  const has = (trait: string) => problem.traits.has(trait);
  if (problem.status === 429) {
    const reason = has("DAILY_LIMIT_REACHED") ? "daily-limit" : "too-frequent";
    return { ok: false, reason, retryAfterSeconds: problem.retryAfterSeconds };
  }
  if (problem.status === 409) {
    if (has("EMAIL_UNVERIFIED")) return { ok: false, reason: "email-unverified" };
    if (has("ACCOUNT_RESTRICTED")) return { ok: false, reason: "account-restricted" };
    if (has("REPLY_TARGET_GONE")) return { ok: false, reason: "reply-target-gone" };
  }
  if (problem.status === 404) {
    if (has("SUBJECT_UNAVAILABLE")) return { ok: false, reason: "subject-gone" };
    if (has("COMMENT_NOT_FOUND")) return { ok: false, reason: "comment-gone" };
  }
  if (problem.status === 422) {
    const reason = BODY_ERRORS[problem.errors.find((error) => error.field === "body")?.code ?? ""];
    if (reason) return { ok: false, reason };
  }
  if (problem.status === 403) {
    return { ok: false, reason: "forbidden" };
  }
  return null;
}

/// 发表根评论（`parentId` 为 null）或回复。成功回完整条目，页面直接插入
export async function publishComment(
  subject: Subject,
  input: { body: string; parentId: string | null },
): Promise<CommentResult> {
  if (
    !isSubject(subject?.type, subject?.key) ||
    typeof input?.body !== "string" ||
    (input.parentId !== null && (typeof input.parentId !== "string" || !isEntityId(input.parentId)))
  ) {
    return UNAVAILABLE;
  }
  const result = await mutate(
    `/discussions/${subject.type}/${subject.key}/comments`,
    { method: "POST", json: { body: input.body, parentId: input.parentId } },
    commentSchema,
    classify,
  );
  return result.ok ? { ok: true, comment: result.data } : result;
}

/// 编辑自己的评论
export async function editComment(commentId: string, body: string): Promise<CommentResult> {
  if (typeof commentId !== "string" || !isEntityId(commentId) || typeof body !== "string") {
    return UNAVAILABLE;
  }
  const result = await mutate(
    `/comments/${commentId}`,
    { method: "PUT", json: { body } },
    commentSchema,
    classify,
  );
  return result.ok ? { ok: true, comment: result.data } : result;
}

/// 删除自己的评论：有回复留占位，没有回复整条去掉（连带级联去掉的占位祖先）
export async function deleteComment(commentId: string): Promise<DeleteCommentResult> {
  if (typeof commentId !== "string" || !isEntityId(commentId)) {
    return UNAVAILABLE;
  }
  const result = await mutate(
    `/comments/${commentId}`,
    { method: "DELETE" },
    deleteResultSchema,
    classify,
  );
  return result.ok
    ? { ok: true, placeholder: result.data.placeholder, removedIds: result.data.removedIds }
    : result;
}
