import { DiscussionCache } from "@/components/discussion/DiscussionCache";
import { DiscussionView } from "@/components/discussion/DiscussionView";
import { lookupSession } from "@/lib/auth/current-user";
import type { DiscussionCopy } from "@/lib/discussion/copy";
import type { DiscussionLocationView } from "@/lib/discussion/location";
import { prefetchDiscussion } from "@/lib/discussion/server";
import type { ViewerSeed } from "@/lib/discussion/viewer";
import type { Subject } from "@/lib/interaction/discussion-schema";
import { publicUrl } from "@/lib/public-url";

export interface DiscussionEntryProps {
  subject: Subject;
  /// 首屏取哪一份（页面按地址的 `comment`、`sort` 解析）
  view: DiscussionLocationView;
  copy: DiscussionCopy;
  /// 对象页的路径（`/papers/1`、`/posts/9`）
  basePath: string;
}

/// 讨论区的服务端入口（RSC）：取首屏（已登录时带会话）与访客，交给客户端的 `DiscussionView`。
/// 会话读不到时按访客给初值，客户端的当前用户取回后以它为准
export async function Discussion({ subject, view, copy, basePath }: DiscussionEntryProps) {
  const [state, session, shareBase] = await Promise.all([
    prefetchDiscussion(subject, view),
    lookupSession(),
    publicUrl(basePath),
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
      <DiscussionView
        subject={subject}
        copy={copy}
        viewer={viewer}
        now={Date.now()}
        basePath={basePath}
        shareBase={shareBase}
      />
    </DiscussionCache>
  );
}
