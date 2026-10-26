/// 收藏、文献库、收藏夹的文案（简报 §3、§4，逐字）

/// 上限（release spec 2.1）
export const LIMITS = {
  libraryItems: 20_000,
  collections: 200,
  collectionItems: 5_000,
  name: 100,
  description: 500,
  note: 500,
  export: 5_000,
  cite: 100,
} as const;

/// 收藏失败时浮层头部的文案；`retry` 为是否给「重试」
export const COLLECT_FAILURE: Record<
  "library-full" | "gone" | "catalog-unavailable" | "unavailable",
  { text: string; retry: boolean }
> = {
  "library-full": { text: "文献库已满 20,000 篇，先移除一些再收藏", retry: false },
  gone: { text: "这篇文献已下线，无法收藏", retry: false },
  "catalog-unavailable": { text: "暂时无法收藏这篇文献，请稍后再试", retry: true },
  unavailable: { text: "没有收藏成功", retry: true },
};

/// 移除前的确认文案（简报 §4.1 `popover.remove-confirm`）
export function removeConfirmText(hasNote: boolean, collections: number): string {
  if (hasNote && collections > 0) {
    return `移除后，这篇的备注会删除，也会移出 ${collections} 个收藏夹。`;
  }
  return hasNote ? "移除后，这篇的备注会删除。" : `移除后，这篇会移出 ${collections} 个收藏夹。`;
}

/// 收藏夹名称的前端校验错误
export const NAME_ERRORS = {
  required: "请输入名称",
  tooLong: "名称最多 100 个字",
  taken: "已有同名收藏夹",
} as const;
