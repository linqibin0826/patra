import type { Metadata } from "next";
import { ReadingHistoryView } from "@/components/history/ReadingHistoryView";
import { Footer } from "@/components/portal/Footer";
import { TopNav } from "@/components/portal/TopNav";
import { requireUser } from "@/lib/auth/require-user";

export const metadata: Metadata = { title: "阅读历史 · Patra", robots: { index: false } };

/// 阅读历史（简报 v0.18 §3.7）：只有自己能看。列表在浏览器里按页取，删除、清空走 Server Action
export default async function HistoryPage() {
  await requireUser("/history");
  return (
    <>
      <TopNav />
      <main>
        <ReadingHistoryView />
      </main>
      <Footer />
    </>
  );
}
