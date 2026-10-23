import type { Metadata } from "next";
import { Footer } from "@/components/portal/Footer";
import { NotFoundState } from "@/components/portal/status/NotFoundState";
import { TopNav } from "@/components/portal/TopNav";

/// 走 not-found 时 Next 只读 layout 与这里的元数据，页面的 `generateMetadata` 不参与；`noindex` 由 Next 在
/// 404 时自动加
export const metadata: Metadata = { title: "没有找到这个用户 · Patra" };

/// 公开主页的 404（简报 §3.13）：所有原因同一个画面、同一句话
export default function UserNotFound() {
  return (
    <>
      <TopNav />
      <main>
        <NotFoundState kind="user" />
      </main>
      <Footer />
    </>
  );
}
