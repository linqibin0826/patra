import type { Metadata } from "next";
import { Footer } from "@/components/portal/Footer";
import { NotFoundState } from "@/components/portal/status/NotFoundState";
import { TopNav } from "@/components/portal/TopNav";

export const metadata: Metadata = { title: "没有找到这个收藏夹 · Patra", robots: { index: false } };

/// 不存在、私有、所有者主页对你不可见（含封禁、冷静期、未验证、仅登录用户而你没登录）同一屏同一句（简报 §3.14）
export default function PublicCollectionNotFound() {
  return (
    <>
      <TopNav />
      <main>
        <NotFoundState kind="collection" />
      </main>
      <Footer />
    </>
  );
}
