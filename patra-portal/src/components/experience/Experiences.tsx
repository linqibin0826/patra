import { DiscussionCache } from "@/components/discussion/DiscussionCache";
import { ExperienceArea } from "@/components/experience/ExperienceArea";
import { lookupSession } from "@/lib/auth/current-user";
import type { ViewerSeed } from "@/lib/discussion/viewer";
import { prefetchJournalExperiences } from "@/lib/experience/server";
import type { ExperienceSort } from "@/lib/interaction/experience-schema";

/// 经验区的服务端入口（RSC）：并行取首屏（汇总与列表第一页，已登录时带会话）与访客，交给客户端的
/// `ExperienceArea`。首屏数据经 `DiscussionCache` 放进缓存，只作初值（PAP-124 交接第 5 节）
export async function Experiences({
  journalId,
  journalTitle,
  sort,
}: {
  journalId: string;
  journalTitle: string;
  sort: ExperienceSort;
}) {
  const [state, session] = await Promise.all([
    prefetchJournalExperiences(journalId, sort),
    lookupSession(),
  ]);
  const viewer: ViewerSeed =
    session.status === "authenticated"
      ? {
          status: "authenticated",
          emailVerified: session.user.emailVerified,
          email: session.user.email,
          avatar: {
            displayName: session.user.displayName,
            username: session.user.username,
            avatarUrl: session.user.avatarUrl,
            avatarPalette: session.user.avatarPalette,
          },
        }
      : { status: "anonymous" };
  return (
    <DiscussionCache state={state}>
      <ExperienceArea
        journalId={journalId}
        journalTitle={journalTitle}
        viewer={viewer}
        now={Date.now()}
      />
    </DiscussionCache>
  );
}
