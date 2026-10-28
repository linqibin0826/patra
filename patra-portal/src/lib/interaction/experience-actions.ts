"use server";

import type { FieldError } from "@/lib/auth/types";
import type { Problem } from "@/lib/identity/problem";
import {
  type CommonFailure,
  mutate,
  NO_CONTENT,
  UNAVAILABLE,
} from "@/lib/interaction/action-failure";
import {
  type ExperienceRequest,
  experienceRequestSchema,
  type PostDetail,
  postDetailSchema,
} from "@/lib/interaction/experience-schema";
import { isEntityId } from "@/lib/library/params";

/// 写投稿经验的失败（spec 4.3）。不带文案，组件按 `reason` 选文案（`lib/experience/messages.ts`）
export type ExperienceFailure =
  | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] }
  | { ok: false; reason: "duplicate"; existingPostId: string | null }
  | { ok: false; reason: "daily-limit"; retryAfterSeconds: number | null }
  | { ok: false; reason: "email-unverified" | "account-restricted" }
  | { ok: false; reason: "journal-gone" }
  | { ok: false; reason: "post-gone" }
  | { ok: false; reason: "forbidden" }
  | CommonFailure;

export type ExperienceResult = { ok: true; detail: PostDetail } | ExperienceFailure;

/// 后端的原因码 → reason。404 在写时是期刊不在了，在改、删时是经验不在了（`notFound` 由调用处给）；
/// 认不出的交给 `mutate` 记日志按 `unavailable` 处理
function classifier(notFound: "journal-gone" | "post-gone") {
  return (problem: Problem): Exclude<ExperienceFailure, CommonFailure> | null => {
    const has = (trait: string) => problem.traits.has(trait);
    switch (problem.status) {
      case 422:
        return problem.errors.length > 0
          ? { ok: false, reason: "invalid-fields", fieldErrors: problem.errors }
          : null;
      case 409:
        if (has("DUPLICATE_SUBMISSION")) {
          return { ok: false, reason: "duplicate", existingPostId: problem.existingPostId };
        }
        if (has("EMAIL_UNVERIFIED")) return { ok: false, reason: "email-unverified" };
        if (has("ACCOUNT_RESTRICTED")) return { ok: false, reason: "account-restricted" };
        return null;
      case 429:
        return has("DAILY_LIMIT_REACHED")
          ? { ok: false, reason: "daily-limit", retryAfterSeconds: problem.retryAfterSeconds }
          : null;
      case 404:
        return { ok: false, reason: notFound };
      case 403:
        return { ok: false, reason: "forbidden" };
      default:
        return null;
    }
  };
}

const validRequest = (request: unknown): request is ExperienceRequest =>
  experienceRequestSchema.safeParse(request).success;

/// 在期刊下写一条经验。成功回详情（含新 ID）
export async function publishExperience(
  journalId: string,
  request: ExperienceRequest,
): Promise<ExperienceResult> {
  if (typeof journalId !== "string" || !isEntityId(journalId) || !validRequest(request)) {
    return UNAVAILABLE;
  }
  const result = await mutate(
    `/journals/${journalId}/posts`,
    { method: "POST", json: request },
    postDetailSchema,
    classifier("journal-gone"),
  );
  return result.ok ? { ok: true, detail: result.data } : result;
}

/// 改自己的经验：整体替换（`anonymous` 不发，后端也忽略）
export async function editExperience(
  postId: string,
  request: ExperienceRequest,
): Promise<ExperienceResult> {
  if (typeof postId !== "string" || !isEntityId(postId) || !validRequest(request)) {
    return UNAVAILABLE;
  }
  const { anonymous: _, ...body } = request;
  const result = await mutate(
    `/posts/${postId}`,
    { method: "PUT", json: body },
    postDetailSchema,
    classifier("post-gone"),
  );
  return result.ok ? { ok: true, detail: result.data } : result;
}

/// 删自己的经验：幂等，不查资格
export async function deleteExperience(postId: string): Promise<{ ok: true } | ExperienceFailure> {
  if (typeof postId !== "string" || !isEntityId(postId)) {
    return UNAVAILABLE;
  }
  const result = await mutate(
    `/posts/${postId}`,
    { method: "DELETE" },
    NO_CONTENT,
    classifier("post-gone"),
  );
  return result.ok ? { ok: true } : result;
}
