import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { AvatarCard } from "@/components/settings/profile/AvatarCard";
import { ProfileForm } from "@/components/settings/profile/ProfileForm";
import { authPageHref } from "@/lib/auth/next-path";
import { requireUser } from "@/lib/auth/require-user";
import { fetchMyPrivacy, fetchMyProfile } from "@/lib/identity/profile";

export const metadata: Metadata = { title: "个人资料 · 设置 · Patra" };

const PAGE = "/settings/profile";

/// 设置 · 个人资料（简报 §3.6）：头像卡片 + 资料表单。资料原值与隐私设置（「专业背景 · 当前：…」那一行要用）并行取；
/// 读取时会话已失效就去登录页。
export default async function ProfileSettingsPage() {
  const user = await requireUser(PAGE);
  const [profile, privacy] = await Promise.all([fetchMyProfile(), fetchMyPrivacy()]);
  if (profile === "signed-out" || privacy === "signed-out") {
    redirect(authPageHref("/login", PAGE));
  }
  // 头像即时生效、不进表单：卡片放在表单前面，卡片间距与表单内一致
  return (
    <div className="flex flex-col gap-8 max-[880px]:gap-6">
      <AvatarCard user={user} />
      <ProfileForm initial={profile} user={user} privacy={privacy} />
    </div>
  );
}
