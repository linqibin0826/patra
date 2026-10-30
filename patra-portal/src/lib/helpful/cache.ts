import type {
  InfiniteData,
  InvalidateQueryFilters,
  Query,
  QueryClient,
} from "@tanstack/react-query";
import type { SingleData, ThreadsData } from "@/lib/discussion/cache";
import type { Comment, Thread } from "@/lib/interaction/discussion-schema";
import type { ExperiencePage, UserExperiencePage } from "@/lib/interaction/experience-schema";
import type { HelpfulResult, HelpfulTarget } from "@/lib/interaction/helpful-schema";
import { queryKeys } from "@/lib/query-keys";

/// 有帮助的结果写回缓存（决策 D，工程 spec 第 6 节）：按目标 ID 改所有已缓存的副本，标为过期但不重取（点了不重排）。
/// 都是纯函数 + `setQueriesData`，不改入参；不先取消在途请求（会打断用户正在点的「加载更多」）

const withValue = <T extends { helpfulCount: number; helpful: boolean | null }>(
  item: T,
  value: HelpfulResult,
): T => ({ ...item, helpful: value.helpful, helpfulCount: value.helpfulCount });

function threadWith(thread: Thread, commentId: string, value: HelpfulResult): Thread {
  const pick = (comment: Comment) =>
    comment.commentId === commentId ? withValue(comment, value) : comment;
  return { ...thread, root: pick(thread.root), replies: thread.replies.map(pick) };
}

/// 讨论列表（各页的楼）或单楼视图里，这条评论（根评论或回复）的有帮助
export function withCommentHelpful<T extends ThreadsData | SingleData>(
  data: T,
  commentId: string,
  value: HelpfulResult,
): T {
  if ("pages" in data) {
    return {
      ...data,
      pages: data.pages.map((page) => ({
        ...page,
        items: page.items.map((thread) => threadWith(thread, commentId, value)),
      })),
    };
  }
  return "missing" in data ? data : { ...data, thread: threadWith(data.thread, commentId, value) };
}

/// 期刊页经验列表里这条经验的有帮助
export function withJournalPostHelpful(
  data: InfiniteData<ExperiencePage, string | null>,
  postId: string,
  value: HelpfulResult,
): InfiniteData<ExperiencePage, string | null> {
  return {
    ...data,
    pages: data.pages.map((page) => ({
      ...page,
      items: page.items.map((item) => (item.postId === postId ? withValue(item, value) : item)),
    })),
  };
}

/// 主页「投稿经验」列表里这条经验的有帮助
export function withUserPostHelpful(
  data: InfiniteData<UserExperiencePage, string | null>,
  postId: string,
  value: HelpfulResult,
): InfiniteData<UserExperiencePage, string | null> {
  return {
    ...data,
    pages: data.pages.map((page) => ({
      ...page,
      items: page.items.map((item) =>
        item.experience.postId === postId
          ? { ...item, experience: withValue(item.experience, value) }
          : item,
      ),
    })),
  };
}

/// 讨论区的列表（`[..., "threads", sort]`）与单楼（`[..., "single", id]`）；对话、修改历史、联想不含有帮助
const isThreadView = (query: Query) =>
  query.queryKey[3] === "threads" || query.queryKey[3] === "single";
/// 期刊页经验列表（`["experience", "journal", id, "list", sort]`）
const isJournalList = (query: Query) =>
  query.queryKey[1] === "journal" && query.queryKey[3] === "list";
/// 主页「投稿经验」列表（`["experience", "user", userId]`）
const isUserList = (query: Query) => query.queryKey[1] === "user";

/// 把点 / 取消的结果写进所有含这个目标的缓存
export function patchHelpful(
  client: QueryClient,
  target: HelpfulTarget,
  value: HelpfulResult,
): void {
  if (target.type === "comment") {
    client.setQueriesData<ThreadsData | SingleData>(
      { queryKey: queryKeys.discussion, predicate: isThreadView },
      (data) => (data ? withCommentHelpful(data, target.id, value) : data),
    );
    return;
  }
  client.setQueriesData<InfiniteData<ExperiencePage, string | null>>(
    { queryKey: queryKeys.experience, predicate: isJournalList },
    (data) => (data ? withJournalPostHelpful(data, target.id, value) : data),
  );
  client.setQueriesData<InfiniteData<UserExperiencePage, string | null>>(
    { queryKey: queryKeys.experience, predicate: isUserList },
    (data) => (data ? withUserPostHelpful(data, target.id, value) : data),
  );
}

/// 点 / 取消成功之后：含这类目标的列表标为过期、不重取。正在看的列表不动（不重排），切排序、重新挂载时按新数字取
export function markHelpfulStale(client: QueryClient, target: HelpfulTarget): void {
  const filters: InvalidateQueryFilters[] =
    target.type === "comment"
      ? [{ queryKey: queryKeys.discussion, predicate: isThreadView }]
      : [
          { queryKey: queryKeys.experience, predicate: isJournalList },
          { queryKey: queryKeys.experience, predicate: isUserList },
        ];
  for (const filter of filters) {
    void client.invalidateQueries({ ...filter, refetchType: "none" });
  }
}

/// 点的时候经验已被删除：期刊页与主页的经验列表立刻重取（正在看的那份里这条随之消失）
export function refetchPostLists(client: QueryClient): void {
  void client.invalidateQueries({ queryKey: queryKeys.experience, predicate: isJournalList });
  void client.invalidateQueries({ queryKey: queryKeys.experience, predicate: isUserList });
}
