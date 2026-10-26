import "server-only";
import { z } from "zod";
import { logUnexpectedResponse } from "@/lib/auth/gateway-response";
import { clearSessionCookie } from "@/lib/auth/session-cookie";
import type { FieldError } from "@/lib/auth/types";
import { isSessionRejected, type Problem } from "@/lib/identity/problem";
import { callInteraction, type InteractionReply } from "@/lib/interaction/request";
import { isEntityId } from "@/lib/library/params";

/// 各写操作都一样的失败：没有会话或会话失效（会话 Cookie 已清）、服务不可用
export type CommonFailure = { ok: false; reason: "signed-out" | "unavailable" };

export const UNAVAILABLE: CommonFailure = { ok: false, reason: "unavailable" };

/// 字段错误（422 带 `errors[]`）
export type InvalidFields = { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] };

/// 一次写操作：调 interaction，成功时按 `schema` 读响应体，失败时先归类公共失败，其余交给 `classify` 认，
/// 认不出的记日志按 `unavailable` 处理。会清 Cookie，只能在 Server Action 里用。
export async function mutate<T, F extends { ok: false } = never>(
  path: string,
  init: Parameters<typeof callInteraction>[1],
  schema: z.ZodType<T>,
  classify: (problem: Problem) => F | null,
): Promise<{ ok: true; data: T } | F | CommonFailure> {
  const endpoint = `/patra-interaction${path}`;
  const reply: InteractionReply = await callInteraction(path, init);
  if (reply.kind === "no-session") {
    return { ok: false, reason: "signed-out" };
  }
  if (reply.kind === "error") {
    return UNAVAILABLE;
  }
  if (reply.kind === "problem") {
    if (isSessionRejected(reply.problem)) {
      await clearSessionCookie();
      return { ok: false, reason: "signed-out" };
    }
    const failure = classify(reply.problem);
    if (failure) {
      return failure;
    }
    logUnexpectedResponse(endpoint, reply.problem.status, { code: reply.problem.code });
    return UNAVAILABLE;
  }
  const parsed = reply.res.status < 300 ? schema.safeParse(reply.body) : undefined;
  if (!parsed?.success) {
    logUnexpectedResponse(endpoint, reply.res.status, reply.body);
    return UNAVAILABLE;
  }
  return { ok: true, data: parsed.data };
}

/// 204 无响应体：读出来是 undefined
export const NO_CONTENT = z.undefined();

/// 没有要单独认的失败
export const NO_FAILURE = (): null => null;

/// 422 带字段错误时的结果
export function invalidFields(problem: Problem): InvalidFields | null {
  return problem.status === 422 && problem.errors.length > 0
    ? { ok: false, reason: "invalid-fields", fieldErrors: problem.errors }
    : null;
}

/// 409 且原因码里有 `trait`
export function conflictWith(problem: Problem, trait: string): boolean {
  return problem.status === 409 && problem.traits.has(trait);
}

/// 一批条目 ID：1 ~ 100 个，每个都是合格的实体 ID
export function isIdBatch(ids: unknown): ids is string[] {
  return (
    Array.isArray(ids) &&
    ids.length >= 1 &&
    ids.length <= 100 &&
    ids.every((id) => typeof id === "string" && isEntityId(id))
  );
}
