import type { Metadata } from "next";
import { notFound, redirect, unstable_rethrow } from "next/navigation";
import { Suspense } from "react";
import { Footer } from "@/components/portal/Footer";
import { TopNav } from "@/components/portal/TopNav";
import { ProfileAbout } from "@/components/profile/ProfileAbout";
import { ProfileHeader } from "@/components/profile/ProfileHeader";
import { ProfileTabsGate } from "@/components/profile/ProfileTabs";
import { ProfileVisibilityNotice } from "@/components/profile/ProfileVisibilityNotice";
import { lookupSession } from "@/lib/auth/current-user";
import { logRequestError } from "@/lib/auth/gateway-response";
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

/// 本人的专业背景是不是只给自己看。只用来加「仅你可见」标注：读不到时记日志、不加标注，不让整页出错。
async function readProfessionalSelfOnly(): Promise<boolean> {
  try {
    const privacy = await fetchMyPrivacy();
    return privacy !== "signed-out" && privacy.professionalVisibility === "SELF";
  } catch (error) {
    // 静态渲染时 Next 用抛错切到动态渲染，这个信号必须原样抛出去
    unstable_rethrow(error);
    logRequestError("/patra-identity/me/privacy", error);
    return false;
  }
}

/// 公开主页（简报 §3.13、spec §10）：旧用户名临时跳到新名（RSC 的 `redirect` 是 307，90 天内可能改回）；
/// 对请求者不存在时 404。主页与当前会话同时取；本人视角（当前用户名与主页相同）再取一次隐私设置，用来标注
/// 专业背景「仅你可见」。头部之下是「关于 · 收藏夹」标签栏（别人视角要先问 interaction，放在自己的 Suspense 里）。
export default async function UserPage({ params }: UserPageProps) {
  const [lookup, session] = await Promise.all([
    fetchPublicProfile((await params).username),
    lookupSession(),
  ]);
  if (lookup.kind === "moved") {
    redirect(`/u/${lookup.username}`);
  }
  if (lookup.kind === "not-found") {
    notFound();
  }
  const { profile } = lookup;
  const viewer = session.status === "authenticated" ? session.user : null;
  const self = viewer?.username === profile.username;
  const professionalSelfOnly = self && (await readProfessionalSelfOnly());
  return (
    <>
      <TopNav />
      {self && (
        <ProfileVisibilityNotice audience={profile.audience} emailVerified={viewer.emailVerified} />
      )}
      <main>
        <ProfileHeader profile={profile} self={self} linksPending={self && !viewer.emailVerified} />
        <Suspense fallback={null}>
          <ProfileTabsGate profile={profile} self={self} />
        </Suspense>
        <ProfileAbout profile={profile} self={self} professionalSelfOnly={professionalSelfOnly} />
      </main>
      <Footer />
    </>
  );
}
