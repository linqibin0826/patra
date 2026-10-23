import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Footer } from "@/components/portal/Footer";
import { TopNav } from "@/components/portal/TopNav";
import { SettingsShell } from "@/components/settings/SettingsShell";
import { lookupSession } from "@/lib/auth/current-user";

export const metadata: Metadata = { robots: { index: false } };

/// 设置页的公共布局：导航栏、设置外壳、页脚。登录守卫在各页面里（页面知道自己的路径，好拼 `next`）；
/// 这里只取用户名给外壳，`lookupSession` 有缓存，和页面共用一次网关请求。
export default async function SettingsLayout({ children }: { children: ReactNode }) {
  const session = await lookupSession();
  const username = session.status === "authenticated" ? session.user.username : null;
  return (
    <>
      <TopNav />
      <main>
        <SettingsShell username={username}>{children}</SettingsShell>
      </main>
      <Footer />
    </>
  );
}
