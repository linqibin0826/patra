// patra-learn/src/app/topics/page.tsx
// 主题列表：不含全由个人环境条目组成的主题，正在长的在前
import type { Metadata } from "next";
import { TopicCardGrid } from "@/components/home";
import { cx, meta, panelLabel, wrap } from "@/components/ui";
import { getCatalog } from "@/lib/content/catalog";
import { topicList } from "@/lib/site/views";

export const metadata: Metadata = { title: "主题" };

export default function TopicsPage() {
  const topics = topicList(getCatalog());
  return (
    <main className={cx(wrap, "flex flex-col gap-6 pt-2 pb-24")}>
      <header className="flex flex-col gap-2.5">
        <span className={cx(panelLabel, "text-teal")}>正在长的主题</span>
        <h1 className="m-0 font-bold font-display text-h1 text-ink leading-[1.3]">
          同一件事，在不同版本里又碰到
        </h1>
        <span className={meta}>每个主题按出处版本排，看它怎么一步步变成现在这样</span>
      </header>
      <TopicCardGrid topics={topics} />
    </main>
  );
}
