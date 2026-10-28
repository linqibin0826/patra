import { useSearchParams } from "next/navigation";
import { type DiscussionLocationView, viewOf } from "@/lib/discussion/location";
import type { DiscussionSort } from "@/lib/interaction/discussion-schema";

export type { DiscussionLocationView };

/// 改地址的查询参数（保留路径、其余参数与片段），不触发服务端重新渲染：Next 把原生 history 同步进 `useSearchParams`
function writeQuery(change: (query: URLSearchParams) => void, how: "push" | "replace") {
  const query = new URLSearchParams(window.location.search);
  change(query);
  const search = query.toString();
  const url = `${window.location.pathname}${search ? `?${search}` : ""}${window.location.hash}`;
  if (how === "push") {
    window.history.pushState(null, "", url);
  } else {
    window.history.replaceState(null, "", url);
  }
}

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
        (query) => (sort === "latest" ? query.set("sort", "latest") : query.delete("sort")),
        "replace",
      ),
    openSingle: (commentId) => writeQuery((query) => query.set("comment", commentId), "push"),
    showAll: () => writeQuery((query) => query.delete("comment"), "replace"),
  };
}
