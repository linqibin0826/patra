import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { TOPIC_CLOUD, topicTier } from "@/data/topics";
import { buildPapersHref } from "@/lib/portal-api/paper-search";
import { cn } from "@/lib/utils";
import type { TopicHeatTier } from "@/types/portal";

const TIER_CLASS: Record<TopicHeatTier, string> = {
  1: "font-serif italic font-medium text-clay-800 tracking-tight leading-none text-[clamp(28px,3.4vw,38px)]",
  2: "font-serif font-medium text-ink-900 tracking-tight leading-none text-[clamp(22px,2.4vw,28px)]",
  3: "font-sans font-semibold text-ink-800 text-[clamp(17px,1.6vw,20px)]",
  4: "font-sans font-medium text-ink-700 text-md",
  5: "font-sans font-normal text-ink-600 text-sm",
};

export function TopicCloud() {
  return (
    <section data-section="topic-cloud" className="bg-clay-50/60">
      <div className="container mx-auto max-w-[1200px] px-6 py-14 max-[880px]:py-10">
        <div className="mb-2 flex items-end justify-between gap-3 max-[880px]:flex-col max-[880px]:items-start">
          <div>
            <SectionEyebrow className="mb-0">此刻热议 · trending now</SectionEyebrow>
            <h2 className="mt-1 font-serif text-3xl font-medium leading-tight tracking-tight text-ink-900">
              医学界正在讨论<em className="italic text-clay-700">什么</em>
            </h2>
            <p className="mt-1 text-sm text-fg-3">
              基于过去 72 小时的检索、被引与抓取频次合成的关键词热度。点选任一词条进入文献检索。
            </p>
          </div>
          <span className="font-mono text-xs text-fg-3">截至 17:42 · 滑动 72h</span>
        </div>

        <div className="mt-6 border-y border-clay-100 py-7 max-[880px]:py-5">
          <ul className="flex flex-wrap items-baseline gap-x-5 gap-y-5 leading-none max-[880px]:gap-4">
            {TOPIC_CLOUD.map((t) => {
              const tier = topicTier(t.heat);
              return (
                <li key={t.term}>
                  <Link
                    href={buildPapersHref({ q: t.term })}
                    title={`${t.count.toLocaleString()} 条相关文献`}
                    className={cn(
                      "-mx-1 inline-flex items-baseline gap-1.5 rounded-sm px-1 hover:bg-paper-200",
                      TIER_CLASS[tier],
                    )}
                  >
                    <span>{t.term}</span>
                    {tier === 1 && t.delta && (
                      <span className="font-mono text-xs font-semibold text-clay-600">
                        ↗ {t.delta}
                      </span>
                    )}
                  </Link>
                </li>
              );
            })}
          </ul>
        </div>

        <div className="mt-8 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-4 font-mono text-2xs uppercase tracking-caps text-fg-3">
            <span
              aria-hidden
              data-topic-heat-bar
              className="h-3 w-24 rounded-sm bg-[linear-gradient(to_right,var(--color-ink-700),var(--color-ink-400),var(--color-clay-300),var(--color-clay-600))]"
            />
            <span>冷 → 热</span>
            <span>↗ Δ 较上周</span>
          </div>
          <button
            type="button"
            disabled
            aria-disabled="true"
            title="功能即将上线"
            className="inline-flex items-center gap-1.5 text-sm text-clay-700 hover:text-clay-800 disabled:cursor-not-allowed disabled:opacity-60"
          >
            浏览全部主题 <ArrowRight className="h-3.5 w-3.5" strokeWidth={1.5} />
          </button>
        </div>
      </div>
    </section>
  );
}
