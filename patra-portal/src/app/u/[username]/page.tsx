import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Footer } from "@/components/portal/Footer";
import { TopNav } from "@/components/portal/TopNav";
import { ProfileAbout } from "@/components/profile/ProfileAbout";
import { ProfileHeader } from "@/components/profile/ProfileHeader";
import { ProfileVisibilityNotice } from "@/components/profile/ProfileVisibilityNotice";
import { lookupSession } from "@/lib/auth/current-user";
import { fetchMyPrivacy } from "@/lib/identity/profile";
import { fetchPublicProfile } from "@/lib/identity/public-profile";

interface UserPageProps {
  params: Promise<{ username: string }>;
}

/// 标题 `昵称 (@用户名) · Patra`；`indexable` 为 false 时 `noindex`。404 与旧名跳转的元数据不在这里。
export async function generateMetadata({ params }: UserPageProps): Promise<Metadata> {
  const lookup = await fetchPublicProfile((await params).username);
  if (lookup.kind !== "found") {
    return {};
  }
  const { profile } = lookup;
  return {
    title: `${profile.displayName} (@${profile.username}) · Patra`,
    ...(profile.indexable ? {} : { robots: { index: false } }),
  };
}

/// 公开主页（简报 §3.13、spec §10）：旧用户名临时跳到新名（RSC 的 `redirect` 是 307，90 天内可能改回）；
/// 对请求者不存在时 404。本人视角（当前用户名与主页相同）多取一次隐私设置，用来标注专业背景「仅你可见」。
export default async function UserPage({ params }: UserPageProps) {
  const lookup = await fetchPublicProfile((await params).username);
  if (lookup.kind === "moved") {
    redirect(`/u/${lookup.username}`);
  }
  if (lookup.kind === "not-found") {
    notFound();
  }
  const { profile } = lookup;
  const session = await lookupSession();
  const viewer = session.status === "authenticated" ? session.user : null;
  const self = viewer?.username === profile.username;
  const privacy = self ? await fetchMyPrivacy() : null;
  const professionalSelfOnly =
    privacy !== null && privacy !== "signed-out" && privacy.professionalVisibility === "SELF";
  return (
    <>
      <TopNav />
      {self && (
        <ProfileVisibilityNotice audience={profile.audience} emailVerified={viewer.emailVerified} />
      )}
      <main>
        <ProfileHeader profile={profile} self={self} linksPending={self && !viewer.emailVerified} />
        <ProfileAbout profile={profile} self={self} professionalSelfOnly={professionalSelfOnly} />
      </main>
      <Footer />
    </>
  );
}
