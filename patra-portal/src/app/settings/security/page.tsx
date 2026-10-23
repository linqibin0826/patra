import type { Metadata } from "next";
import { ChangePasswordCard } from "@/components/settings/security/ChangePasswordCard";
import { SessionsCard } from "@/components/settings/security/SessionsCard";
import { requireUser } from "@/lib/auth/require-user";

export const metadata: Metadata = { title: "安全 · 设置 · Patra" };

const PAGE = "/settings/security";

/// 设置 · 安全（简报 §3.11）：改密码与登录设备两张卡片。设备列表在浏览器里查询（spec §4.3），这里不取数。
export default async function SecuritySettingsPage() {
  await requireUser(PAGE);
  return (
    <div className="flex flex-col gap-8 max-[880px]:gap-6">
      <ChangePasswordCard />
      <SessionsCard />
    </div>
  );
}
