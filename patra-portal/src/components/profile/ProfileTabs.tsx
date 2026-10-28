import Link from "next/link";
import type { PublicProfile } from "@/lib/identity/public-profile";
import { type ProfileTab, visibleProfileTabs } from "@/lib/profile/tabs";

const TABS: Record<ProfileTab, { label: string; path: string }> = {
  about: { label: "关于", path: "" },
  collections: { label: "收藏夹", path: "/collections" },
  experiences: { label: "投稿经验", path: "/experiences" },
};

/// 主页标签栏（简报 §3.15、§3.17）：「关于」「收藏夹」「投稿经验」三条路由，按 `tabs` 渲染，下划线样式，不带个数。
/// 只有「关于」一个标签时不渲染
export function ProfileTabs({
  username,
  tabs,
  current,
}: {
  username: string;
  tabs: readonly ProfileTab[];
  current: ProfileTab;
}) {
  if (tabs.length <= 1) {
    return null;
  }
  const tab =
    "-mb-px flex h-11 items-center border-b-2 border-transparent px-1 text-base text-fg-2 transition-colors hover:text-fg-1 aria-[current=page]:border-clay-500 aria-[current=page]:font-medium aria-[current=page]:text-fg-1";
  return (
    <nav aria-label="主页分区" className="mx-auto max-w-page px-gutter">
      <div className="flex gap-6 border-b border-border-default">
        {tabs.map((key) => (
          <Link
            key={key}
            href={`/u/${username}${TABS[key].path}`}
            aria-current={current === key ? "page" : undefined}
            className={tab}
          >
            {TABS[key].label}
          </Link>
        ))}
      </div>
    </nav>
  );
}

/// 「关于」页上的标签栏：按 `visibleProfileTabs` 的判定渲染，「任一标签有内容」才出（只有「关于」时不出、
/// 不占位）。包在 Suspense 里，不拖慢主页
export async function ProfileTabsGate({
  profile,
  self,
}: {
  profile: PublicProfile;
  self: boolean;
}) {
  const tabs = await visibleProfileTabs(profile, self, "about");
  return <ProfileTabs username={profile.username} tabs={tabs} current="about" />;
}
