import type { Metadata } from "next";
import { ChangePasswordCard } from "@/components/settings/security/ChangePasswordCard";
import { LoginMethodsCard } from "@/components/settings/security/LoginMethodsCard";
import { SessionsCard } from "@/components/settings/security/SessionsCard";
import { readOAuthEnabled } from "@/lib/auth/oauth-host";
import { parseOAuthNotice } from "@/lib/auth/oauth-notice";
import { requireUser } from "@/lib/auth/require-user";

export const metadata: Metadata = { title: "安全 · 设置 · Patra" };

const PAGE = "/settings/security";

/// 设置 · 安全（简报 §3.11，v0.19 画板 Security）：登录方式、改密码、登录设备。没有密码的账号没有改密码卡片：当前密码
/// 填不了，设置密码在登录方式卡片里。登录方式和设备列表在浏览器里查询（spec §4.3），这里不取数。
export default async function SecuritySettingsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const user = await requireUser(PAGE);
  return (
    <div className="flex flex-col gap-8 max-[880px]:gap-6">
      <LoginMethodsCard
        oauthEnabled={await readOAuthEnabled()}
        email={user.email}
        emailVerified={user.emailVerified}
        notice={parseOAuthNotice(await searchParams)}
      />
      {user.hasPassword && <ChangePasswordCard />}
      <SessionsCard />
    </div>
  );
}
