import "server-only";
import { type DehydratedState, dehydrate, QueryClient } from "@tanstack/react-query";
import {
  fetchExperienceSummary,
  fetchJournalExperiences,
  fetchUserExperiences,
} from "@/lib/interaction/experience";
import type { ExperienceSort } from "@/lib/interaction/experience-schema";
import { queryKeys } from "@/lib/query-keys";

/// 主页「投稿经验」每页条数（与 Route Handler 一致）
export const USER_PAGE_SIZE = 20;

/// 期刊页经验区首屏（决策 F）：汇总（近 3 年）与列表第一页（按地址的排序）并行取，取到的放进临时 QueryClient 脱水，交给客户端
/// （`DiscussionCache`，只作初值）。取不到的不放：客户端挂载后经 Route Handler 再取一次，再失败才显示「暂不可用」
export async function prefetchJournalExperiences(
  journalId: string,
  sort: ExperienceSort,
): Promise<DehydratedState> {
  const client = new QueryClient();
  const [summary, list] = await Promise.all([
    fetchExperienceSummary(journalId, "3y"),
    fetchJournalExperiences(journalId, sort),
  ]);
  if (summary.kind === "ok") {
    client.setQueryData(queryKeys.experienceSummary(journalId, "3y"), summary.data);
  }
  if (list.kind === "ok") {
    client.setQueryData(queryKeys.experienceList(journalId, sort), {
      pages: [list.data],
      pageParams: [null],
    });
  }
  return dehydrate(client);
}

/// 主页「投稿经验」首屏（每页 20 条）：取到才放进缓存，取不到由客户端再取一次，再失败显示错误块
export async function prefetchUserExperiences(userId: string): Promise<DehydratedState> {
  const client = new QueryClient();
  const read = await fetchUserExperiences(userId, { limit: USER_PAGE_SIZE });
  if (read.kind === "ok") {
    client.setQueryData(queryKeys.experienceUser(userId), {
      pages: [read.data],
      pageParams: [null],
    });
  }
  return dehydrate(client);
}
