"use server";

import type { Problem } from "@/lib/identity/problem";
import { type CommonFailure, mutate, UNAVAILABLE } from "@/lib/interaction/action-failure";
import {
  type HelpfulResult,
  type HelpfulTarget,
  helpfulResultSchema,
  helpfulTargetSchema,
} from "@/lib/interaction/helpful-schema";
import { isEntityId } from "@/lib/library/params";

/// 点、取消有帮助的失败（后端 spec 第 6 节）。不带文案，按钮按 `reason` 选文案（`lib/helpful/messages.ts`）
export type HelpfulFailure =
  | {
      ok: false;
      reason: "email-unverified" | "account-restricted" | "daily-limit" | "gone" | "self";
    }
  | CommonFailure;

export type HelpfulActionResult = { ok: true; result: HelpfulResult } | HelpfulFailure;

/// 后端的原因码 → reason；认不出的交给 `mutate` 记日志按 `unavailable` 处理
function classify(problem: Problem): Exclude<HelpfulFailure, CommonFailure> | null {
  const has = (trait: string) => problem.traits.has(trait);
  if (problem.status === 429 && has("DAILY_LIMIT_REACHED")) {
    return { ok: false, reason: "daily-limit" };
  }
  if (problem.status === 409) {
    if (has("EMAIL_UNVERIFIED")) return { ok: false, reason: "email-unverified" };
    if (has("ACCOUNT_RESTRICTED")) return { ok: false, reason: "account-restricted" };
  }
  if (problem.status === 404 && has("HELPFUL_TARGET_NOT_FOUND")) {
    return { ok: false, reason: "gone" };
  }
  if (problem.status === 403 && has("SELF_HELPFUL")) {
    return { ok: false, reason: "self" };
  }
  return null;
}

/// 点（`on` 为真，PUT）或取消（DELETE）有帮助。两边都幂等，返回操作后的状态，按钮用它校正乐观值
export async function setHelpful(target: HelpfulTarget, on: boolean): Promise<HelpfulActionResult> {
  if (
    !helpfulTargetSchema.safeParse(target).success ||
    !isEntityId(target.id) ||
    typeof on !== "boolean"
  ) {
    return UNAVAILABLE;
  }
  const result = await mutate(
    `/${target.type === "comment" ? "comments" : "posts"}/${target.id}/helpful`,
    { method: on ? "PUT" : "DELETE" },
    helpfulResultSchema,
    classify,
  );
  return result.ok ? { ok: true, result: result.data } : result;
}
