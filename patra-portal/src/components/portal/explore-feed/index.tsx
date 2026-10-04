import { ArrowRight } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { PaperCard } from "@/components/portal/PaperCard";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { fetchFeed } from "@/lib/portal-api/publications";
import type { FeedTab, Paper } from "@/types/portal";
import { ExploreFeedEmpty } from "./empty";
import { ExploreFeedTabs } from "./tabs";

/// 头条右侧并列的条目数
const SIDE_COUNT = 3;

export async function ExploreFeed({ tab }: { tab: FeedTab }) {
  let papers: Paper[];
  try {
    const page = await fetchFeed(tab);
    papers = page.items;
  } catch {
    return (
      <FeedSection tabs={<ExploreFeedTabs currentTab={tab} />}>
        <ExploreFeedEmpty reason="error" />
      </FeedSection>
    );
  }

  return (
    <FeedSection tabs={<ExploreFeedTabs currentTab={tab} />}>
      {papers.length === 0 ? <ExploreFeedEmpty reason="empty" /> : <FeedLayout papers={papers} />}
    </FeedSection>
  );
}

/// 编辑排版：第 1 篇为头条（左 7 栏），2–4 篇并列右 5 栏，其余进入两栏编号列表。
function FeedLayout({ papers }: { papers: Paper[] }) {
  const [first, ...rest] = papers;
  const side = rest.slice(0, SIDE_COUNT);
  const more = rest.slice(SIDE_COUNT);
  return (
    <>
      <div className="grid grid-cols-1 lg:grid-cols-12 lg:gap-x-12">
        {first && (
          <div className="max-lg:pb-8 lg:col-span-7">
            <PaperCard paper={first} variant="lead" index={1} />
          </div>
        )}
        <ol className="flex flex-col lg:col-span-5">
          {side.map((p, i) => (
            <li key={p.id} className="flex-1">
              <PaperCard paper={p} index={i + 2} />
            </li>
          ))}
        </ol>
      </div>

      {/* 窄屏只露前 8 篇（头条 + 并列 3 篇 + 列表前 4 篇），其余交给「查看更多文献」，避免首页过长 */}
      {more.length > 0 && (
        <ol className="mt-4 grid grid-cols-2 gap-x-12 max-md:grid-cols-1">
          {more.map((p, i) => (
            <li key={p.id} className="reveal max-md:[&:nth-child(n+5)]:hidden">
              <PaperCard paper={p} index={i + 2 + SIDE_COUNT} />
            </li>
          ))}
        </ol>
      )}

      <div className="mt-14 flex justify-center">
        <Link
          href="/papers"
          className="group/more inline-flex h-12 items-center gap-2 rounded-full border border-ink-900 px-7 text-sm font-semibold text-ink-900 transition-colors duration-300 hover:bg-ink-900 hover:text-paper-50"
        >
          查看更多文献
          <ArrowRight
            className="size-4 motion-safe:transition-transform duration-300 ease-out-expo motion-safe:group-hover/more:translate-x-1"
            strokeWidth={1.75}
            aria-hidden
          />
        </Link>
      </div>
    </>
  );
}

/// 区块外壳：编号眉题 + 标题 + 说明，右侧（窄屏下方）放 tab。骨架屏复用同一外壳，避免布局跳动。
export function FeedSection({ children, tabs }: { children: ReactNode; tabs: ReactNode }) {
  return (
    <section data-section="explore-feed" className="mx-auto max-w-page px-gutter">
      <div className="mb-10 flex items-end justify-between gap-x-10 gap-y-6 border-t border-border-default pt-[clamp(64px,8vw,112px)] max-md:flex-col max-md:items-start">
        <div>
          <SectionEyebrow index="03" className="mb-5">
            文献流
          </SectionEyebrow>
          <h2 className="font-serif text-display-3 leading-heading font-medium tracking-tight text-ink-900">
            值得读一读的文献
          </h2>
          <p className="mt-4 max-w-[40em] text-sm leading-relaxed break-keep text-fg-3">
            最近入库与高被引的文献。AI 速读由 Patra 在采集时生成，仅作为线索，不能替代阅读原文。
          </p>
        </div>
        <div className="shrink-0">{tabs}</div>
      </div>
      {children}
    </section>
  );
}
