import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { DeleteAccountCard } from "@/components/settings/account/DeleteAccountCard";
import { EmailCard } from "@/components/settings/account/EmailCard";
import { UsernameCard } from "@/components/settings/account/UsernameCard";
import { authPageHref } from "@/lib/auth/next-path";
import { requireUser } from "@/lib/auth/require-user";
import { fetchPendingEmailChange } from "@/lib/identity/account";

export const metadata: Metadata = { title: "账号 · 设置 · Patra" };

const PAGE = "/settings/account";

/// 设置 · 账号（简报 §3.10）：用户名、邮箱、注销三张卡片。待确认的改邮箱在服务端取好交给邮箱卡片；
/// 读取时会话已失效就去登录页。
export default async function AccountSettingsPage() {
  const user = await requireUser(PAGE);
  const pending = await fetchPendingEmailChange();
  if (pending === "signed-out") {
    redirect(authPageHref("/login", PAGE));
  }
  return (
    <div className="flex flex-col gap-8 max-[880px]:gap-6">
      <UsernameCard user={user} now={Date.now()} />
      <EmailCard user={user} pending={pending} />
      <DeleteAccountCard user={user} />
    </div>
  );
}
