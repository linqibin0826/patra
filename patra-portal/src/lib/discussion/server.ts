import "server-only";
import { type DehydratedState, dehydrate, QueryClient } from "@tanstack/react-query";
import type { SingleData, ThreadsData } from "@/lib/discussion/cache";
import { type DiscussionLocationView, viewOf } from "@/lib/discussion/location";
import { fetchSingleThread, fetchThreads } from "@/lib/interaction/discussion";
import type { Subject } from "@/lib/interaction/discussion-schema";
import { queryKeys } from "@/lib/query-keys";

/// 页面的 `searchParams` → 讨论区视图（同客户端的 `useDiscussionLocation`）；同名参数出现多次时不认
export function parseDiscussionView(
  searchParams: Record<string, string | string[] | undefined>,
): DiscussionLocationView {
  return viewOf({
    get: (name) => {
      const value = searchParams[name];
      return typeof value === "string" ? value : null;
    },
  });
}

/// 讨论区首屏（决策 F）：服务端取到的放进一个临时 QueryClient，脱水后交给客户端（`DiscussionCache`，只作初值）。
/// 取不到的不放：客户端挂载后经 Route Handler 再取一次，再失败才显示「暂不可用」。单楼的目标不存在放 `missing`
export async function prefetchDiscussion(
  subject: Subject,
  view: DiscussionLocationView,
): Promise<DehydratedState> {
  const client = new QueryClient();
  if (view.mode === "list") {
    const read = await fetchThreads(subject, view.sort);
    if (read.kind === "ok") {
      const data: ThreadsData = { pages: [read.data], pageParams: [null] };
      client.setQueryData(queryKeys.discussionThreads(subject.type, subject.key, view.sort), data);
    }
  } else {
    const read = await fetchSingleThread(subject, view.commentId);
    if (read.kind === "ok" || read.kind === "not-found") {
      const data: SingleData = read.kind === "ok" ? read.data : { missing: true };
      client.setQueryData(
        queryKeys.discussionSingle(subject.type, subject.key, view.commentId),
        data,
      );
    }
  }
  return dehydrate(client);
}
