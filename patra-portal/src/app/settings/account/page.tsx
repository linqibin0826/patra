import type { Metadata } from "next";
import { UsernameCard } from "@/components/settings/account/UsernameCard";
import { requireUser } from "@/lib/auth/require-user";

export const metadata: Metadata = { title: "账号 · 设置 · Patra" };

const PAGE = "/settings/account";

/// 设置 · 账号（简报 §3.10）：用户名、邮箱、注销三张卡片。
export default async function AccountSettingsPage() {
  const user = await requireUser(PAGE);
  return (
    <div className="flex flex-col gap-8 max-[880px]:gap-6">
      <UsernameCard user={user} now={Date.now()} />
    </div>
  );
}
