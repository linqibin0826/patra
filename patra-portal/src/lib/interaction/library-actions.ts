"use server";

import type { Problem } from "@/lib/identity/problem";
import {
  type CommonFailure,
  conflictWith,
  type InvalidFields,
  invalidFields,
  isIdBatch,
  mutate,
  NO_CONTENT,
  NO_FAILURE,
  UNAVAILABLE,
} from "@/lib/interaction/action-failure";
import { collectResultSchema, type LibraryItem, libraryItemSchema } from "@/lib/interaction/schema";
import { isEntityId } from "@/lib/library/params";

/// 收藏一篇的结果。不带文案，组件按 `reason` 选文案（简报 §4.1）。
/// - `library-full`：文献库已满 20,000 篇
/// - `gone`：文献不存在或已下线
/// - `catalog-unavailable`：首次收藏这篇时 catalog 不可用，可重试
export type CollectResult =
  | { ok: true; created: boolean; item: LibraryItem }
  | { ok: false; reason: "library-full" | "gone" | "catalog-unavailable" }
  | CommonFailure;

/// 收藏（`PUT /me/library/publication/{key}`）：已在库里也是成功，`created=false`
export async function collectPublication(key: string): Promise<CollectResult> {
  if (typeof key !== "string" || !isEntityId(key)) {
    return UNAVAILABLE;
  }
  const result = await mutate(
    `/me/library/publication/${key}`,
    { method: "PUT" },
    collectResultSchema,
    (problem: Problem) =>
      conflictWith(problem, "LIBRARY_FULL")
        ? ({ ok: false, reason: "library-full" } as const)
        : problem.status === 404
          ? ({ ok: false, reason: "gone" } as const)
          : problem.status === 503 && problem.code === "INTERACTION-0503"
            ? ({ ok: false, reason: "catalog-unavailable" } as const)
            : null,
  );
  return result.ok ? { ok: true, created: result.data.created, item: result.data.item } : result;
}

/// 从文献库移除（1 ~ 100 条，幂等）：同时移出所有收藏夹、删备注
export async function removeItems(itemIds: string[]): Promise<{ ok: true } | CommonFailure> {
  if (!isIdBatch(itemIds)) {
    return UNAVAILABLE;
  }
  const result = await mutate<undefined, never>(
    `/me/library/items?itemIds=${itemIds.join(",")}`,
    { method: "DELETE" },
    NO_CONTENT,
    NO_FAILURE,
  );
  return result.ok ? { ok: true } : result;
}

export type SaveNoteResult =
  | { ok: true; item: LibraryItem }
  | InvalidFields
  | { ok: false; reason: "gone" }
  | CommonFailure;

/// 保存备注：空白 = 删除（发 null）。不在库里是 `gone`
export async function saveNote(itemId: string, note: string): Promise<SaveNoteResult> {
  if (typeof itemId !== "string" || !isEntityId(itemId) || typeof note !== "string") {
    return UNAVAILABLE;
  }
  const result = await mutate(
    `/me/library/items/${itemId}/note`,
    { method: "PUT", json: { note: note.trim() === "" ? null : note } },
    libraryItemSchema,
    (problem: Problem) =>
      invalidFields(problem) ??
      (problem.status === 404 ? ({ ok: false, reason: "gone" } as const) : null),
  );
  return result.ok ? { ok: true, item: result.data } : result;
}
