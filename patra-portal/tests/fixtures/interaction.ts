import type {
  Collection,
  CopyResult,
  ItemStatus,
  LibraryItem,
  LibraryOverview,
  PublicCollection,
  PublicItem,
} from "@/lib/interaction/schema";

/// interaction 响应体的测试夹具（字段形状见 `/patra-interaction/v3/api-docs`），按需覆盖

export function makeCollection(overrides: Partial<Collection> = {}): Collection {
  return {
    collectionId: "9",
    name: "组会",
    description: "每周四组会要讲的文献，按主题分好。",
    visibility: "PRIVATE",
    itemCount: 23,
    createdAt: "2026-10-20T02:00:00Z",
    updatedAt: "2026-10-24T02:00:00Z",
    ...overrides,
  };
}

export function makeOverview(overrides: Partial<LibraryOverview> = {}): LibraryOverview {
  return { total: 1284, unfiled: 312, collections: [makeCollection()], ...overrides };
}

export function makeLibraryItem(overrides: Partial<LibraryItem> = {}): LibraryItem {
  return {
    itemId: "501",
    type: "publication",
    key: "11",
    state: "ACTIVE",
    title: null,
    note: null,
    collectedAt: "2026-10-24T02:00:00Z",
    addedAt: null,
    collectionIds: [],
    ...overrides,
  };
}

export function makeItemStatus(overrides: Partial<ItemStatus> = {}): ItemStatus {
  return {
    key: "11",
    collected: false,
    itemId: null,
    collectedAt: null,
    collectionIds: [],
    hasNote: false,
    ...overrides,
  };
}

export function makePublicCollection(overrides: Partial<PublicCollection> = {}): PublicCollection {
  return {
    collectionId: "9",
    name: "组会",
    description: "每周四组会要讲的文献，按主题分好。",
    visibility: "PUBLIC",
    itemCount: 23,
    updatedAt: "2026-10-24T02:00:00Z",
    owner: {
      userId: "352303128713027814",
      username: "chen-yu",
      displayName: "陈宇",
      avatarUrl: null,
      avatarPalette: 1,
      deleted: false,
    },
    viewerIsOwner: false,
    ...overrides,
  };
}

export function makePublicItem(overrides: Partial<PublicItem> = {}): PublicItem {
  return {
    itemId: "501",
    type: "publication",
    key: "11",
    state: "ACTIVE",
    title: null,
    addedAt: "2026-10-24T02:00:00Z",
    ...overrides,
  };
}

export function makeCopyResult(overrides: Partial<CopyResult> = {}): CopyResult {
  return {
    collectionId: "77",
    name: "组会 (2)",
    addedToLibrary: 18,
    alreadyInLibrary: 5,
    skippedUnavailable: 0,
    ...overrides,
  };
}
