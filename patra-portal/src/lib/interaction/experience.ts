import "server-only";
import { cache } from "react";
import {
  type ExperiencePage,
  type ExperienceSort,
  experiencePageSchema,
  type PostDetail,
  postDetailSchema,
  type Revision,
  revisionsSchema,
  type Summary,
  type SummaryWindow,
  summarySchema,
  summaryWindowSchema,
  type UserExperiencePage,
  userExperiencePageSchema,
} from "@/lib/interaction/experience-schema";
import { type Read, readPublic } from "@/lib/interaction/read";
import { isEntityId } from "@/lib/library/params";

/// 投稿经验的服务端读（PAP-122 交接第 3 节）。都是登录可选：有会话就带（`mine`、本人的匿名经验、作者主页
/// 可见性），令牌被拒时按访客重取。参数不合格直接 `not-found`，不请求网关

const NOT_FOUND = { kind: "not-found" } as const;

/// 期刊页每页 10 条（后端上限）
const JOURNAL_PAGE_SIZE = 10;

function withCursor(path: string, cursor: string | undefined): string {
  return cursor ? `${path}&cursor=${encodeURIComponent(cursor)}` : path;
}

/// 期刊的一页经验：最新投稿（投稿日期倒序）或最有帮助，不受统计窗口影响
export async function fetchJournalExperiences(
  journalId: string,
  sort: ExperienceSort,
  cursor?: string,
): Promise<Read<ExperiencePage>> {
  if (!isEntityId(journalId)) {
    return NOT_FOUND;
  }
  return readPublic(
    withCursor(`/journals/${journalId}/posts?limit=${JOURNAL_PAGE_SIZE}&sort=${sort}`, cursor),
    experiencePageSchema,
  );
}

/// 期刊页「同行经验」汇总（读取时计算，写完立刻反映）
export async function fetchExperienceSummary(
  journalId: string,
  window: SummaryWindow,
): Promise<Read<Summary>> {
  if (!isEntityId(journalId) || !summaryWindowSchema.safeParse(window).success) {
    return NOT_FOUND;
  }
  return readPublic(`/journals/${journalId}/posts/summary?window=${window}`, summarySchema);
}

/// 经验详情：已删除也是 200（`deleted: true`）。用 `cache()` 包住，`generateMetadata` 与页面共用一次请求
export const fetchPost = cache(async (id: string): Promise<Read<PostDetail>> => {
  return isEntityId(id) ? readPublic(`/posts/${id}`, postDetailSchema) : NOT_FOUND;
});

/// 修改历史：当前版本在前
export async function fetchPostRevisions(id: string): Promise<Read<{ items: Revision[] }>> {
  return isEntityId(id) ? readPublic(`/posts/${id}/revisions`, revisionsSchema) : NOT_FOUND;
}

/// 主页「投稿经验」：本人看含匿名的；别人看只有署名的，主页对他不可见时是空页
export async function fetchUserExperiences(
  userId: string,
  options: { cursor?: string; limit: number },
): Promise<Read<UserExperiencePage>> {
  if (!isEntityId(userId)) {
    return NOT_FOUND;
  }
  return readPublic(
    withCursor(`/users/${userId}/posts?limit=${options.limit}`, options.cursor),
    userExperiencePageSchema,
  );
}
