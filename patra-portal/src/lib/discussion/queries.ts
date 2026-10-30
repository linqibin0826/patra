import {
  keepPreviousData,
  type QueryClient,
  useInfiniteQuery,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import type { SingleData } from "@/lib/discussion/cache";
import type {
  Conversation,
  DiscussionSort,
  MentionSuggestion,
  Replies,
  Revisions,
  SingleThread,
  Subject,
  ThreadPage,
} from "@/lib/interaction/discussion-schema";
import { keepPreviousWhileCached } from "@/lib/keep-previous";
import { queryKeys } from "@/lib/query-keys";

/// 门户自己的 Route Handler 的响应：非 2xx 抛错（查询进入 error，界面显示降级）
async function readOk<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`${res.url} 失败：${res.status}`);
  }
  return (await res.json()) as T;
}

/// 只在出错时随窗口聚焦重取：interaction 恢复后切回页面讨论区自动恢复（release spec D9），正常时不多发请求
const refetchWhenFailed = (query: { state: { status: string } }): "always" | false =>
  query.state.status === "error" ? "always" : false;

/// 对象在门户接口路径里的一段：`publication/1`
const at = (subject: Subject) => `${subject.type}/${subject.key}`;

/// 讨论区列表：一页 20 楼，游标翻页。失败不自动重试，「重试」由用户点或切回页面时触发
export function useThreads(subject: Subject, sort: DiscussionSort) {
  const client = useQueryClient();
  return useInfiniteQuery({
    queryKey: queryKeys.discussionThreads(subject.type, subject.key, sort),
    queryFn: async ({ pageParam }) => {
      const query = new URLSearchParams({ sort });
      if (pageParam) {
        query.set("cursor", pageParam);
      }
      return readOk<ThreadPage>(await fetch(`/api/discussions/${at(subject)}/threads?${query}`));
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    // 切换排序时先留着上一份（排序控件与列表不闪没），取到再换
    placeholderData: (previous, previousQuery) =>
      keepPreviousWhileCached(client, previous, previousQuery),
    retry: false,
    refetchOnWindowFocus: refetchWhenFailed,
  });
}

/// 单楼视图：目标不存在（404）是 `{ missing: true }`，不是错误
export function useSingleThread(subject: Subject, commentId: string | null) {
  return useQuery({
    queryKey: queryKeys.discussionSingle(subject.type, subject.key, commentId ?? ""),
    queryFn: async (): Promise<SingleData> => {
      const res = await fetch(`/api/discussions/${at(subject)}/comments/${commentId}/thread`);
      return res.status === 404 ? { missing: true } : readOk<SingleThread>(res);
    },
    enabled: commentId !== null,
    retry: false,
    refetchOnWindowFocus: refetchWhenFailed,
  });
}

/// 「查看对话」：打开对话框时才取
export function useConversation(commentId: string | null) {
  return useQuery({
    queryKey: queryKeys.discussionConversation(commentId ?? ""),
    queryFn: async () =>
      readOk<Conversation>(await fetch(`/api/comments/${commentId}/conversation`)),
    enabled: commentId !== null,
    retry: false,
  });
}

/// 修改历史：打开对话框时才取
export function useRevisions(commentId: string | null) {
  return useQuery({
    queryKey: queryKeys.discussionRevisions(commentId ?? ""),
    queryFn: async () => readOk<Revisions>(await fetch(`/api/comments/${commentId}/revisions`)),
    enabled: commentId !== null,
    retry: false,
  });
}

/// @ 联想：`q` 为 null 时联想没打开，不取；同一个查询词 30 秒内不重取
export function useMentionSuggestions(subject: Subject, q: string | null, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.discussionMentions(subject.type, subject.key, q ?? ""),
    queryFn: async () => {
      const query = new URLSearchParams({ q: q ?? "" });
      return readOk<MentionSuggestion[]>(
        await fetch(`/api/discussions/${at(subject)}/mention-suggestions?${query}`),
      );
    },
    enabled: enabled && q !== null,
    staleTime: 30_000,
    // 换查询词时先留着上一批（参与者先显示），底部转圈
    placeholderData: keepPreviousData,
    retry: false,
  });
}

/// 「展开更多回复」的下一批
export async function fetchRepliesPage(rootId: string, cursor: string | null): Promise<Replies> {
  const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : "";
  return readOk<Replies>(await fetch(`/api/comments/${rootId}/replies${query}`));
}

/// 删除后对账用：按任一评论取它所在的整楼；整楼没了返回 null
export async function fetchThreadOf(
  subject: Subject,
  commentId: string,
): Promise<SingleThread | null> {
  const res = await fetch(`/api/discussions/${at(subject)}/comments/${commentId}/thread`);
  return res.status === 404 ? null : readOk<SingleThread>(res);
}

/// 编辑原文（`@` 已换回当前用户名）
export async function fetchCommentSource(commentId: string): Promise<string> {
  return (await readOk<{ body: string }>(await fetch(`/api/comments/${commentId}/source`))).body;
}

/// 写操作成功之后：这个对象下别的视图（另一种排序、单楼）与对话、修改历史的缓存都标为过期，不立刻重取；
/// 下次挂载时重取，避免从单楼回到列表还看到删掉的评论
export function markDiscussionStale(client: QueryClient, subject: Subject): void {
  void client.invalidateQueries({
    queryKey: queryKeys.discussionSubject(subject.type, subject.key),
    refetchType: "none",
  });
  void client.invalidateQueries({
    queryKey: queryKeys.discussionConversationAll,
    refetchType: "none",
  });
  void client.invalidateQueries({
    queryKey: queryKeys.discussionRevisionsAll,
    refetchType: "none",
  });
}
