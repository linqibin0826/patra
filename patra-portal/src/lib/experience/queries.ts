import {
  keepPreviousData,
  type QueryClient,
  useInfiniteQuery,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import type {
  ExperiencePage,
  ExperienceSort,
  Revision,
  Summary,
  SummaryWindow,
  UserExperiencePage,
} from "@/lib/interaction/experience-schema";
import { keepPreviousWhileCached } from "@/lib/keep-previous";
import { queryKeys } from "@/lib/query-keys";

/// 门户自己的 Route Handler 的响应：非 2xx 抛错（查询进入 error，界面显示降级）
async function readOk<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new Error(`${res.url} 失败：${res.status}`);
  }
  return (await res.json()) as T;
}

/// 只在出错时随窗口聚焦重取：interaction 恢复后切回页面经验区自动恢复（release spec D9），正常时不多发请求
const refetchWhenFailed = (query: { state: { status: string } }): "always" | false =>
  query.state.status === "error" ? "always" : false;

const cursorQuery = (cursor: string | null) =>
  cursor ? `?${new URLSearchParams({ cursor })}` : "";

/// 期刊页「同行经验」汇总。失败不自动重试，「重试」由用户点或切回页面时触发
export function useExperienceSummary(journalId: string, window: SummaryWindow) {
  return useQuery({
    queryKey: queryKeys.experienceSummary(journalId, window),
    queryFn: async () =>
      readOk<Summary>(await fetch(`/api/journals/${journalId}/posts/summary?window=${window}`)),
    // 切窗口时先留着上一份（卡片与切换控件不闪没），取到再换
    placeholderData: keepPreviousData,
    retry: false,
    refetchOnWindowFocus: refetchWhenFailed,
  });
}

/// 期刊页经验列表：每页 10 条，游标翻页，按排序分别缓存
export function useJournalExperiences(journalId: string, sort: ExperienceSort) {
  const client = useQueryClient();
  return useInfiniteQuery({
    queryKey: queryKeys.experienceList(journalId, sort),
    queryFn: async ({ pageParam }) => {
      const query = new URLSearchParams({ sort });
      if (pageParam) {
        query.set("cursor", pageParam);
      }
      return readOk<ExperiencePage>(await fetch(`/api/journals/${journalId}/posts?${query}`));
    },
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    // 换排序时先留着当前列表（工具行与列表不闪没），取到再换
    placeholderData: (previous, previousQuery) =>
      keepPreviousWhileCached(client, previous, previousQuery),
    retry: false,
    refetchOnWindowFocus: refetchWhenFailed,
  });
}

/// 主页「投稿经验」标签：每页 20 条
export function useUserExperiences(userId: string) {
  return useInfiniteQuery({
    queryKey: queryKeys.experienceUser(userId),
    queryFn: async ({ pageParam }) =>
      readOk<UserExperiencePage>(
        await fetch(`/api/users/${userId}/posts${cursorQuery(pageParam)}`),
      ),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
    retry: false,
    refetchOnWindowFocus: refetchWhenFailed,
  });
}

/// 修改历史：打开对话框时才取。经验已删除（404）是 `{ gone: true }`，不是错误
export function usePostRevisions(postId: string, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.experienceRevisions(postId),
    queryFn: async (): Promise<{ items: Revision[] } | { gone: true }> => {
      const res = await fetch(`/api/posts/${postId}/revisions`);
      return res.status === 404 ? { gone: true } : readOk<{ items: Revision[] }>(res);
    },
    enabled,
    retry: false,
  });
}

/// 发布之后（期刊页）：先取消这本期刊在途的汇总与列表请求（发布之前发出、之后才回来的旧数据会盖掉新结果，
/// PAP-124 交接第 5 节），再重取汇总、把列表重置回第一页（新经验按投稿日期排，不一定在第一页）
export async function afterPublish(client: QueryClient, journalId: string): Promise<void> {
  const journal = queryKeys.experienceJournal(journalId);
  await client.cancelQueries({ queryKey: journal });
  await Promise.all([
    client.invalidateQueries({ queryKey: [...journal, "summary"] }),
    client.resetQueries({ queryKey: queryKeys.experienceListAll(journalId) }),
  ]);
}

/// 编辑、删除之后（经验页）：这本期刊的汇总与列表、作者主页的列表、这条的修改历史先取消在途请求，再标为过期
/// （下次去期刊页、主页、开修改历史时重取）。期刊已失效（没有 key）、作者对访客不可知时跳过对应的那份
export async function afterEditOrDelete(
  client: QueryClient,
  target: { journalId: string | null; authorUserId: string | null; postId: string },
): Promise<void> {
  const keys = [
    target.journalId ? queryKeys.experienceJournal(target.journalId) : null,
    target.authorUserId ? queryKeys.experienceUser(target.authorUserId) : null,
    queryKeys.experienceRevisions(target.postId),
  ].filter((key) => key !== null);
  for (const queryKey of keys) {
    await client.cancelQueries({ queryKey });
  }
  await Promise.all(keys.map((queryKey) => client.invalidateQueries({ queryKey })));
}
