import Link from "next/link";
import type { PublicProfile } from "@/lib/identity/public-profile";
import { fetchUserPublicCollections } from "@/lib/interaction/collections";

/// 主页标签栏（简报 §3.15）：「关于」「收藏夹」两条路由，下划线样式，不带个数
export function ProfileTabs({
  username,
  current,
}: {
  username: string;
  current: "about" | "collections";
}) {
  const tab =
    "-mb-px flex h-11 items-center border-b-2 border-transparent px-1 text-base text-fg-2 transition-colors hover:text-fg-1 aria-[current=page]:border-clay-500 aria-[current=page]:font-medium aria-[current=page]:text-fg-1";
  return (
    <nav aria-label="主页分区" className="mx-auto max-w-page px-gutter">
      <div className="flex gap-6 border-b border-border-default">
        <Link
          href={`/u/${username}`}
          aria-current={current === "about" ? "page" : undefined}
          className={tab}
        >
          关于
        </Link>
        <Link
          href={`/u/${username}/collections`}
          aria-current={current === "collections" ? "page" : undefined}
          className={tab}
        >
          收藏夹
        </Link>
      </div>
    </nav>
  );
}

/// 「关于」页上的标签栏：本人视角总出；别人视角只有至少一个对他可见的公开收藏夹时出，空或失败都不出、不占位
/// （只有「关于」一个标签时不渲染标签栏）。包在 Suspense 里，不拖慢主页
export async function ProfileTabsGate({
  profile,
  self,
}: {
  profile: PublicProfile;
  self: boolean;
}) {
  if (!self) {
    const read = await fetchUserPublicCollections(profile.userId);
    if (read.kind !== "ok" || read.data.length === 0) {
      return null;
    }
  }
  return <ProfileTabs username={profile.username} current="about" />;
}
