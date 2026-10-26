import { z } from "zod";

/// interaction 响应体（`/patra-interaction/v3/api-docs`）里门户用到的部分。ID 一律是字符串（Long → String），
/// 时间是 UTC ISO 8601。

const id = z.string().min(1);

export const visibilitySchema = z.enum(["PRIVATE", "PUBLIC"]);
export type Visibility = z.infer<typeof visibilitySchema>;

/// 文献库条目的状态：`UNAVAILABLE` 是 catalog 软删或整行消失，显示「已失效」
export const itemStateSchema = z.enum(["ACTIVE", "UNAVAILABLE"]);
export type ItemState = z.infer<typeof itemStateSchema>;

export const collectionSchema = z.object({
  collectionId: id,
  name: z.string(),
  description: z.string().nullable(),
  visibility: visibilitySchema,
  itemCount: z.number().int().nonnegative(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type Collection = z.infer<typeof collectionSchema>;

/// 侧栏与收藏浮层共用：全部、未分类的篇数与本人的全部收藏夹（按更新时间倒序）
export const libraryOverviewSchema = z.object({
  total: z.number().int().nonnegative(),
  unfiled: z.number().int().nonnegative(),
  collections: z.array(collectionSchema),
});
export type LibraryOverview = z.infer<typeof libraryOverviewSchema>;

/// 文献库条目。`key` 是 catalog 文献 ID，catalog 整行消失后为 null；`title` 只在已失效时有（登记表快照）；
/// `addedAt` 只在收藏夹视图里有（加入该夹的时间）；`collectionIds` 是本人全部收藏夹里含这条的那些
export const libraryItemSchema = z.object({
  itemId: id,
  type: z.string(),
  key: z.string().nullable(),
  state: itemStateSchema,
  title: z.string().nullable(),
  note: z.string().nullable(),
  collectedAt: z.string(),
  addedAt: z.string().nullable(),
  collectionIds: z.array(id),
});
export type LibraryItem = z.infer<typeof libraryItemSchema>;

/// 批量「我的状态」的一行：没登记过、key 不合法的都是 `collected=false`
export const itemStatusSchema = z.object({
  key: z.string(),
  collected: z.boolean(),
  itemId: id.nullable(),
  collectedAt: z.string().nullable(),
  collectionIds: z.array(id),
  hasNote: z.boolean(),
});
export type ItemStatus = z.infer<typeof itemStatusSchema>;

export const statusResponseSchema = z.object({ items: z.array(itemStatusSchema) });

export const collectResultSchema = z.object({ created: z.boolean(), item: libraryItemSchema });
export type CollectResult = z.infer<typeof collectResultSchema>;

export const addToCollectionResultSchema = z.object({
  added: z.number().int(),
  alreadyIn: z.number().int(),
  skippedFull: z.number().int(),
  skippedNotInLibrary: z.number().int(),
});
export type AddToCollectionResult = z.infer<typeof addToCollectionResultSchema>;

/// 收藏夹所有者：已注销时 `deleted=true`，用户名、昵称、头像都为 null
export const ownerSchema = z.object({
  userId: id,
  username: z.string().nullable(),
  displayName: z.string().nullable(),
  avatarUrl: z.string().nullable(),
  avatarPalette: z.number().int(),
  deleted: z.boolean(),
});
export type Owner = z.infer<typeof ownerSchema>;

/// 公开收藏夹头部。`owner` 只在本人视角、identity 不可用时为 null
export const publicCollectionSchema = z.object({
  collectionId: id,
  name: z.string(),
  description: z.string().nullable(),
  visibility: visibilitySchema,
  itemCount: z.number().int().nonnegative(),
  updatedAt: z.string(),
  owner: ownerSchema.nullable(),
  viewerIsOwner: z.boolean(),
});
export type PublicCollection = z.infer<typeof publicCollectionSchema>;

/// 公开收藏夹的条目：不带备注
export const publicItemSchema = z.object({
  itemId: id,
  type: z.string(),
  key: z.string().nullable(),
  state: itemStateSchema,
  title: z.string().nullable(),
  addedAt: z.string(),
});
export type PublicItem = z.infer<typeof publicItemSchema>;

export const publicCollectionCardSchema = z.object({
  collectionId: id,
  name: z.string(),
  description: z.string().nullable(),
  itemCount: z.number().int().nonnegative(),
  updatedAt: z.string(),
});
export type PublicCollectionCard = z.infer<typeof publicCollectionCardSchema>;

export const userCollectionsSchema = z.object({ collections: z.array(publicCollectionCardSchema) });

export const copyResultSchema = z.object({
  collectionId: id,
  name: z.string(),
  addedToLibrary: z.number().int(),
  alreadyInLibrary: z.number().int(),
  skippedUnavailable: z.number().int(),
});
export type CopyResult = z.infer<typeof copyResultSchema>;

/// 导出 / 复制引用的范围：顺序与同一排序下列表的顺序一致；已失效的不在 `keys` 里，只计数
export const exportKeysSchema = z.object({
  keys: z.array(z.string()),
  skippedUnavailable: z.number().int().nonnegative(),
});
export type ExportKeys = z.infer<typeof exportKeysSchema>;

export interface Page<T> {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  items: T[];
}

/// 分页包装 `{page, pageSize, total, totalPages, items}`，`page` 从 1 开始
export function pageSchema<T extends z.ZodType>(item: T) {
  return z.object({
    page: z.number().int(),
    pageSize: z.number().int(),
    total: z.number().int().nonnegative(),
    totalPages: z.number().int().nonnegative(),
    items: z.array(item),
  });
}
