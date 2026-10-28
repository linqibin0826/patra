import type { DiscussionSort } from "@/lib/interaction/discussion-schema";
import { isEntityId } from "@/lib/library/params";

/// 讨论区当前看的是什么：列表（带排序）或单楼（目标评论）
export type DiscussionLocationView =
  | { mode: "list"; sort: DiscussionSort }
  | { mode: "single"; commentId: string };

/// 地址查询参数 → 视图：`comment` 是实体 ID 就是单楼；`sort=latest` 是最新，其余正序
export function viewOf(params: { get(name: string): string | null }): DiscussionLocationView {
  const comment = params.get("comment");
  if (comment && isEntityId(comment)) {
    return { mode: "single", commentId: comment };
  }
  return { mode: "list", sort: params.get("sort") === "latest" ? "latest" : "asc" };
}
