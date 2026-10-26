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
import {
  type AddToCollectionResult,
  addToCollectionResultSchema,
  type Collection,
  type CopyResult,
  collectionSchema,
  copyResultSchema,
  type Visibility,
} from "@/lib/interaction/schema";
import { isEntityId } from "@/lib/library/params";

/// 新建、编辑收藏夹的入参：简介为空发 null
export interface CollectionInput {
  name: string;
  description: string;
}

export type SaveCollectionResult =
  | { ok: true; collection: Collection }
  | InvalidFields
  | { ok: false; reason: "name-taken" | "collection-limit" | "gone" }
  | CommonFailure;

const GONE = { ok: false, reason: "gone" } as const;

function isInput(input: unknown): input is CollectionInput {
  return (
    typeof input === "object" &&
    input !== null &&
    typeof (input as CollectionInput).name === "string" &&
    typeof (input as CollectionInput).description === "string"
  );
}

function body(input: CollectionInput) {
  return {
    name: input.name,
    description: input.description.trim() === "" ? null : input.description,
  };
}

function classifySave(problem: Problem) {
  return (
    invalidFields(problem) ??
    (conflictWith(problem, "COLLECTION_NAME_TAKEN")
      ? ({ ok: false, reason: "name-taken" } as const)
      : conflictWith(problem, "COLLECTION_LIMIT_REACHED")
        ? ({ ok: false, reason: "collection-limit" } as const)
        : problem.status === 404
          ? GONE
          : null)
  );
}

/// 新建收藏夹（私有）
export async function createCollection(input: CollectionInput): Promise<SaveCollectionResult> {
  if (!isInput(input)) {
    return UNAVAILABLE;
  }
  const result = await mutate(
    "/me/collections",
    { method: "POST", json: body(input) },
    collectionSchema,
    classifySave,
  );
  return result.ok ? { ok: true, collection: result.data } : result;
}

/// 改名称与简介（整体替换）
export async function updateCollection(
  id: string,
  input: CollectionInput,
): Promise<SaveCollectionResult> {
  if (typeof id !== "string" || !isEntityId(id) || !isInput(input)) {
    return UNAVAILABLE;
  }
  const result = await mutate(
    `/me/collections/${id}`,
    { method: "PUT", json: body(input) },
    collectionSchema,
    classifySave,
  );
  return result.ok ? { ok: true, collection: result.data } : result;
}

export type SetVisibilityResult =
  | { ok: true; collection: Collection }
  | { ok: false; reason: "email-unverified" | "account-restricted" | "gone" }
  | CommonFailure;

/// 设为公开 / 私有。设为公开要邮箱已验证、账号正常：两个原因都在时先报账号（与后端判定顺序一致）
export async function setVisibility(
  id: string,
  visibility: Visibility,
): Promise<SetVisibilityResult> {
  if (typeof id !== "string" || !isEntityId(id) || !["PRIVATE", "PUBLIC"].includes(visibility)) {
    return UNAVAILABLE;
  }
  const result = await mutate(
    `/me/collections/${id}/visibility`,
    { method: "PUT", json: { visibility } },
    collectionSchema,
    (problem: Problem) =>
      conflictWith(problem, "ACCOUNT_RESTRICTED")
        ? ({ ok: false, reason: "account-restricted" } as const)
        : conflictWith(problem, "EMAIL_UNVERIFIED")
          ? ({ ok: false, reason: "email-unverified" } as const)
          : problem.status === 404
            ? GONE
            : null,
  );
  return result.ok ? { ok: true, collection: result.data } : result;
}

/// 删除收藏夹（幂等）：条目留在文献库
export async function deleteCollection(id: string): Promise<{ ok: true } | CommonFailure> {
  if (typeof id !== "string" || !isEntityId(id)) {
    return UNAVAILABLE;
  }
  const result = await mutate<undefined, never>(
    `/me/collections/${id}`,
    { method: "DELETE" },
    NO_CONTENT,
    NO_FAILURE,
  );
  return result.ok ? { ok: true } : result;
}

export type AddToCollectionActionResult =
  | { ok: true; result: AddToCollectionResult }
  | { ok: false; reason: "gone" }
  | CommonFailure;

/// 放进收藏夹（1 ~ 100 条）：夹满不报错，看 `skippedFull`
export async function addToCollection(
  id: string,
  itemIds: string[],
): Promise<AddToCollectionActionResult> {
  if (typeof id !== "string" || !isEntityId(id) || !isIdBatch(itemIds)) {
    return UNAVAILABLE;
  }
  const result = await mutate(
    `/me/collections/${id}/items`,
    { method: "POST", json: { itemIds } },
    addToCollectionResultSchema,
    (problem: Problem) => (problem.status === 404 ? GONE : null),
  );
  return result.ok ? { ok: true, result: result.data } : result;
}

/// 移出收藏夹（1 ~ 100 条）：不在夹里的忽略
export async function removeFromCollection(
  id: string,
  itemIds: string[],
): Promise<{ ok: true } | { ok: false; reason: "gone" } | CommonFailure> {
  if (typeof id !== "string" || !isEntityId(id) || !isIdBatch(itemIds)) {
    return UNAVAILABLE;
  }
  const result = await mutate(
    `/me/collections/${id}/items?itemIds=${itemIds.join(",")}`,
    { method: "DELETE" },
    NO_CONTENT,
    (problem: Problem) => (problem.status === 404 ? GONE : null),
  );
  return result.ok ? { ok: true } : result;
}

export type CopyCollectionResult =
  | { ok: true; result: CopyResult }
  | { ok: false; reason: "not-visible" | "library-full" | "collection-limit" }
  | CommonFailure;

/// 把别人的公开收藏夹复制成自己的（简报 §3.14）：整体成败；复制前一刻已不可见是 `not-visible`
export async function copyCollection(id: string): Promise<CopyCollectionResult> {
  if (typeof id !== "string" || !isEntityId(id)) {
    return UNAVAILABLE;
  }
  const result = await mutate(
    `/collections/${id}/copies`,
    { method: "POST" },
    copyResultSchema,
    (problem: Problem) =>
      problem.status === 404
        ? ({ ok: false, reason: "not-visible" } as const)
        : conflictWith(problem, "LIBRARY_FULL")
          ? ({ ok: false, reason: "library-full" } as const)
          : conflictWith(problem, "COLLECTION_LIMIT_REACHED")
            ? ({ ok: false, reason: "collection-limit" } as const)
            : null,
  );
  return result.ok ? { ok: true, result: result.data } : result;
}
