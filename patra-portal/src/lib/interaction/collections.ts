import "server-only";
import { cache } from "react";
import { attachPapers, type WithPaper } from "@/lib/interaction/attach-papers";
import { READ_TIMEOUT_MS, type Read, toRead } from "@/lib/interaction/read";
import { callInteraction } from "@/lib/interaction/request";
import {
  type Page,
  type PublicCollection,
  type PublicCollectionCard,
  type PublicItem,
  pageSchema,
  publicCollectionSchema,
  publicItemSchema,
  userCollectionsSchema,
} from "@/lib/interaction/schema";
import { isEntityId, type LibrarySort } from "@/lib/library/params";

export type PublicEntry = WithPaper<PublicItem>;

const OPTIONAL = { auth: "optional" } as const;

/// 公开收藏夹头部：访客已登录就带会话（可见性取决于访客，决策 H）。不存在、私有、所有者主页对访客不可见
/// 都是 `not-found`；identity 不可用是 503 → `unavailable`，不能当 404（简报 §3.17）。用 `cache()` 包住，
/// `generateMetadata` 与页面共用一次请求
export const fetchPublicCollection = cache(async (id: string): Promise<Read<PublicCollection>> => {
  if (!isEntityId(id)) {
    return { kind: "not-found" };
  }
  const path = `/collections/${id}`;
  return toRead(
    await callInteraction(path, { timeoutMs: READ_TIMEOUT_MS }, OPTIONAL),
    publicCollectionSchema,
    path,
  );
});

/// 公开收藏夹一页条目（不带备注），配好卡片
export async function fetchPublicCollectionPage(
  id: string,
  sort: LibrarySort,
  page: number,
): Promise<Read<Page<PublicEntry>>> {
  if (!isEntityId(id)) {
    return { kind: "not-found" };
  }
  const path = `/collections/${id}/items?sort=${sort}&page=${page}&size=50`;
  const read = toRead(
    await callInteraction(path, { timeoutMs: READ_TIMEOUT_MS }, OPTIONAL),
    pageSchema(publicItemSchema),
    path,
  );
  return read.kind === "ok" ? attachPapers(read.data) : read;
}

/// 主页「收藏夹」栏：该用户对这位访客可见的公开收藏夹（不可见时是空列表，不是 404）
export async function fetchUserPublicCollections(
  userId: string,
): Promise<Read<PublicCollectionCard[]>> {
  if (!isEntityId(userId)) {
    return { kind: "not-found" };
  }
  const path = `/users/${userId}/collections`;
  const read = toRead(
    await callInteraction(path, { timeoutMs: READ_TIMEOUT_MS }, OPTIONAL),
    userCollectionsSchema,
    path,
  );
  return read.kind === "ok" ? { kind: "ok", data: read.data.collections } : read;
}
