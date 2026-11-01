// patra-learn/src/app/notes/page.tsx
// 「我的批注」页：批注只在读者浏览器里，构建时只把条目标题与类型交给客户端组件（分组、导出用）
import type { Metadata } from "next";
import { NotesView } from "@/components/notes-view";
import { plainTitle, wrap } from "@/components/ui";
import { getCatalog } from "@/lib/content/catalog";

export const metadata: Metadata = { title: "我的批注" };

export default function NotesPage() {
  const entries = Object.fromEntries(
    getCatalog().entries.map((e) => [
      e.slug,
      { title: plainTitle(e.meta.title), type: e.meta.type },
    ]),
  );
  return (
    <main className={`${wrap} pt-4 pb-30`}>
      <NotesView entries={entries} />
    </main>
  );
}
