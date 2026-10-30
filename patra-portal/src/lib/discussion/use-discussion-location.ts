import { useSearchParams } from "next/navigation";
import { type DiscussionLocationView, viewOf } from "@/lib/discussion/location";
import type { DiscussionSort } from "@/lib/interaction/discussion-schema";
import { writeQuery } from "@/lib/write-query";

export type { DiscussionLocationView };

/// 讨论区的视图以地址为准（spec 6.2）：排序与「查看全部讨论」替换当前历史，打开单楼新增一条（可以后退）
export function useDiscussionLocation(): {
  view: DiscussionLocationView;
  setSort: (sort: DiscussionSort) => void;
  openSingle: (commentId: string) => void;
  showAll: () => void;
} {
  const view = viewOf(useSearchParams());
  return {
    view,
    setSort: (sort) =>
      writeQuery(
        (query) => (sort === "asc" ? query.delete("sort") : query.set("sort", sort)),
        "replace",
      ),
    openSingle: (commentId) => writeQuery((query) => query.set("comment", commentId), "push"),
    showAll: () => writeQuery((query) => query.delete("comment"), "replace"),
  };
}
