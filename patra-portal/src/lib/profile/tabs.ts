import "server-only";
import type { PublicProfile } from "@/lib/identity/public-profile";
import { fetchUserPublicCollections } from "@/lib/interaction/collections";
import { fetchUserExperiences } from "@/lib/interaction/experience";

/// 主页的标签：关于、收藏夹、投稿经验（顺序固定）
export type ProfileTab = "about" | "collections" | "experiences";

/// 主页标签栏上出现哪些标签（spec 7.1、简报 §3.17）。「关于」与当前所在的标签总在；其余本人视角总在，
/// 别人视角各按条件：收藏夹要有至少一个对他可见的公开收藏夹，投稿经验要有至少一条署名的经验（interaction
/// 对他只返回这些）。两次读并行，失败或空都不出
export async function visibleProfileTabs(
  profile: PublicProfile,
  self: boolean,
  current: ProfileTab,
): Promise<ProfileTab[]> {
  if (self) {
    return ["about", "collections", "experiences"];
  }
  const [collections, experiences] = await Promise.all([
    fetchUserPublicCollections(profile.userId),
    fetchUserExperiences(profile.userId, { limit: 1 }),
  ]);
  const shown: Record<ProfileTab, boolean> = {
    about: true,
    collections: collections.kind === "ok" && collections.data.length > 0,
    experiences: experiences.kind === "ok" && experiences.data.items.length > 0,
  };
  shown[current] = true;
  return (["about", "collections", "experiences"] as const).filter((tab) => shown[tab]);
}
