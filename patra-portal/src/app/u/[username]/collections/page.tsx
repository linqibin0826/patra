import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Footer } from "@/components/portal/Footer";
import { TopNav } from "@/components/portal/TopNav";
import { ProfileCollections } from "@/components/profile/ProfileCollections";
import { ProfileHeader } from "@/components/profile/ProfileHeader";
import { ProfileTabs } from "@/components/profile/ProfileTabs";
import { ProfileVisibilityNotice } from "@/components/profile/ProfileVisibilityNotice";
import { lookupSession } from "@/lib/auth/current-user";
import { fetchPublicProfile } from "@/lib/identity/public-profile";
import { fetchUserPublicCollections } from "@/lib/interaction/collections";
import { visibleProfileTabs } from "@/lib/profile/tabs";

interface UserCollectionsPageProps {
  params: Promise<{ username: string }>;
}

/// 标题 `昵称 (@用户名) 的收藏夹 · Patra`；`indexable` 为 false 时 `noindex`（与主页一致）
export async function generateMetadata({ params }: UserCollectionsPageProps): Promise<Metadata> {
  const lookup = await fetchPublicProfile((await params).username);
  if (lookup.kind !== "found") {
    return {};
  }
  const { profile } = lookup;
  return {
    title: `${profile.displayName} (@${profile.username}) 的收藏夹 · Patra`,
    ...(profile.indexable ? {} : { robots: { index: false } }),
  };
}

/// 主页「收藏夹」标签（简报 §3.15）：取主页与旧名跳转同 `/u/[username]`；人已经在这个标签上，标签栏总渲染，
/// 其余标签按 `visibleProfileTabs` 的条件。
/// 公开收藏夹按主页的用户 ID 取，访客已登录就带会话
export default async function UserCollectionsPage({ params }: UserCollectionsPageProps) {
  const [lookup, session] = await Promise.all([
    fetchPublicProfile((await params).username),
    lookupSession(),
  ]);
  if (lookup.kind === "moved") {
    redirect(`/u/${lookup.username}/collections`);
  }
  if (lookup.kind === "not-found") {
    notFound();
  }
  const { profile } = lookup;
  const viewer = session.status === "authenticated" ? session.user : null;
  const self = viewer?.username === profile.username;
  const [collections, tabs] = await Promise.all([
    fetchUserPublicCollections(profile.userId),
    visibleProfileTabs(profile, self, "collections"),
  ]);
  return (
    <>
      <TopNav />
      {self && (
        <ProfileVisibilityNotice audience={profile.audience} emailVerified={viewer.emailVerified} />
      )}
      <main>
        <ProfileHeader profile={profile} self={self} linksPending={self && !viewer.emailVerified} />
        <ProfileTabs username={profile.username} tabs={tabs} current="collections" />
        <div className="mx-auto max-w-page px-gutter pt-8 pb-30">
          <ProfileCollections username={profile.username} self={self} collections={collections} />
        </div>
      </main>
      <Footer />
    </>
  );
}
