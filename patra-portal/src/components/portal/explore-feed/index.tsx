import { ArrowRight } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import type { ReactNode } from "react";
import { PaperCard } from "@/components/portal/PaperCard";
import { fetchFeed } from "@/lib/portal-api/publications";
import type { FeedTab, Paper } from "@/types/portal";
import { ExploreFeedEmpty } from "./empty";
import { ExploreFeedTabs } from "./tabs";

export async function ExploreFeed({ tab }: { tab: FeedTab }) {
  let papers: Paper[];
  try {
    const page = await fetchFeed(tab);
    papers = page.items;
  } catch {
    return (
      <FeedSection>
        <ExploreFeedTabs currentTab={tab} />
        <div className="pt-6">
          <ExploreFeedEmpty reason="error" />
        </div>
      </FeedSection>
    );
  }

  return (
    <FeedSection>
      <ExploreFeedTabs currentTab={tab} />
      <div className="pt-6">
        {papers.length === 0 ? (
          <ExploreFeedEmpty reason="empty" />
        ) : (
          <>
            <div className="grid grid-cols-2 gap-5 max-[880px]:grid-cols-1">
              {papers.map((p) => (
                <PaperCard key={p.id} paper={p} />
              ))}
            </div>
            <div className="mt-8 flex justify-center">
              <Link
                href="/papers"
                className="inline-flex items-center gap-1.5 rounded-md border border-(--border-default) bg-paper-50 px-4 py-2 text-sm font-medium text-(--fg-1) transition-colors hover:bg-paper-200"
              >
                查看更多文献
                <ArrowRight className="h-3.5 w-3.5" strokeWidth={1.5} aria-hidden />
              </Link>
            </div>
          </>
        )}
      </div>
    </FeedSection>
  );
}

function FeedSection({ children }: { children: ReactNode }) {
  return (
    <section
      data-section="explore-feed"
      className="container mx-auto max-w-[1200px] px-6 py-14 max-[880px]:py-10"
    >
      <div className="mb-6">
        <span className="inline-flex items-center gap-1.5 font-sans text-2xs font-semibold uppercase tracking-caps text-fg-3">
          <Image src="/brand/patra-mark.svg" alt="" aria-hidden width={4} height={14} />
          文献流
        </span>
        <h2 className="mt-1 font-serif text-3xl font-medium leading-tight tracking-tight text-ink-900">
          值得读一读的文献
        </h2>
        <p className="mt-1 text-sm text-fg-3">
          每篇都带一段 AI 速读 —— 由 Patra 在采集时生成，仅作为线索，不能替代阅读原文。
        </p>
      </div>
      {children}
    </section>
  );
}
