import { HydrationBoundary } from "@tanstack/react-query";
import type { Metadata } from "next";
import type { ReactNode } from "react";
import { LibraryShell } from "@/components/library/LibraryShell";
import { Footer } from "@/components/portal/Footer";
import { TopNav } from "@/components/portal/TopNav";
import { prefetchLibrary } from "@/lib/library/server";

export const metadata: Metadata = { robots: { index: false } };

/// 文献库的公共布局：导航栏、外壳（h1、侧栏 / 视图切换）、页脚。登录守卫在各页面里（页面知道自己的路径，
/// 好拼 `next`）。概览（侧栏要的）在这里预取，和页面共用一次请求
export default async function LibraryLayout({ children }: { children: ReactNode }) {
  const state = await prefetchLibrary({ overview: true });
  return (
    <>
      <TopNav />
      <main>
        <HydrationBoundary state={state}>
          <LibraryShell>{children}</LibraryShell>
        </HydrationBoundary>
      </main>
      <Footer />
    </>
  );
}
