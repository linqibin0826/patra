import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { DiscussionCache } from "@/components/discussion/DiscussionCache";
import { Footer } from "@/components/portal/Footer";
import { TopNav } from "@/components/portal/TopNav";
import { ProfileExperiences } from "@/components/profile/ProfileExperiences";
import { ProfileHeader } from "@/components/profile/ProfileHeader";
import { ProfileTabs } from "@/components/profile/ProfileTabs";
import { ProfileVisibilityNotice } from "@/components/profile/ProfileVisibilityNotice";
import { lookupSession } from "@/lib/auth/current-user";
import { prefetchUserExperiences } from "@/lib/experience/server";
import { fetchPublicProfile } from "@/lib/identity/public-profile";
import { visibleProfileTabs } from "@/lib/profile/tabs";
import { publicUrl } from "@/lib/public-url";

interface UserExperiencesPageProps {
  params: Promise<{ username: string }>;
}

/// 标题 `昵称 (@用户名) 的投稿经验 · Patra`、canonical；`indexable` 为 false 时 `noindex`（与主页一致）
export async function generateMetadata({ params }: UserExperiencesPageProps): Promise<Metadata> {
  const lookup = await fetchPublicProfile((await params).username);
  if (lookup.kind !== "found") {
    return {};
  }
  const { profile } = lookup;
  return {
    title: `${profile.displayName} (@${profile.username}) 的投稿经验 · Patra`,
    alternates: { canonical: await publicUrl(`/u/${profile.username}/experiences`) },
    ...(profile.indexable ? {} : { robots: { index: false } }),
  };
}

/// 主页「投稿经验」标签（简报 §3.17、spec 7.2）：取主页与旧名跳转同 `/u/[username]`；人已经在这个标签上，标签栏
/// 总渲染，其余标签按条件。列表首屏服务端取（访客已登录就带会话：本人能看到自己的匿名经验），客户端接管翻页
export default async function UserExperiencesPage({ params }: UserExperiencesPageProps) {
  const [lookup, session] = await Promise.all([
    fetchPublicProfile((await params).username),
    lookupSession(),
  ]);
  if (lookup.kind === "moved") {
    redirect(`/u/${lookup.username}/experiences`);
  }
  if (lookup.kind === "not-found") {
    notFound();
  }
  const { profile } = lookup;
  const viewer = session.status === "authenticated" ? session.user : null;
  const self = viewer?.username === profile.username;
  const [state, tabs] = await Promise.all([
    prefetchUserExperiences(profile.userId),
    visibleProfileTabs(profile, self, "experiences"),
  ]);
  return (
    <>
      <TopNav />
      {self && (
        <ProfileVisibilityNotice audience={profile.audience} emailVerified={viewer.emailVerified} />
      )}
      <main>
        <ProfileHeader profile={profile} self={self} linksPending={self && !viewer.emailVerified} />
        <ProfileTabs username={profile.username} tabs={tabs} current="experiences" />
        <div className="mx-auto max-w-page px-gutter pt-8 pb-30">
          <DiscussionCache state={state}>
            <ProfileExperiences userId={profile.userId} self={self} now={Date.now()} />
          </DiscussionCache>
        </div>
      </main>
      <Footer />
    </>
  );
}
