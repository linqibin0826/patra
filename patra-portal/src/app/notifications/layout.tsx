import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Footer } from "@/components/portal/Footer";
import { TopNav } from "@/components/portal/TopNav";

export const metadata: Metadata = { title: "提醒 · Patra", robots: { index: false } };

/// 提醒列表的公共布局：导航栏、内容、页脚。登录守卫在三个页面里（页面知道自己的路径，好拼 `next`）
export default function NotificationsLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <TopNav />
      <main>{children}</main>
      <Footer />
    </>
  );
}
