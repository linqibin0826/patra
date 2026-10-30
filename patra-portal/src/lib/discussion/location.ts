import type { DiscussionSort } from "@/lib/interaction/discussion-schema";
import { isEntityId } from "@/lib/library/params";

/// 讨论区当前看的是什么：列表（带排序）或单楼（目标评论）
export type DiscussionLocationView =
  | { mode: "list"; sort: DiscussionSort }
  | { mode: "single"; commentId: string };

/// 地址或请求里的 `sort` → 根评论排序：`latest`、`helpful` 认，其余正序
export function parseDiscussionSort(value: string | null): DiscussionSort {
  return value === "latest" || value === "helpful" ? value : "asc";
}

/// 地址查询参数 → 视图：`comment` 是实体 ID 就是单楼；否则是列表，排序见 `parseDiscussionSort`
export function viewOf(params: { get(name: string): string | null }): DiscussionLocationView {
  const comment = params.get("comment");
  if (comment && isEntityId(comment)) {
    return { mode: "single", commentId: comment };
  }
  return { mode: "list", sort: parseDiscussionSort(params.get("sort")) };
}
