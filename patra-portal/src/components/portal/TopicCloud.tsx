import { ArrowRight, ArrowUpRight } from "lucide-react";
import Link from "next/link";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { TOPIC_CLOUD, topicTier } from "@/data/topics";
import { buildPapersHref } from "@/lib/portal-api/paper-search";
import { cn } from "@/lib/utils";
import type { TopicHeatTier } from "@/types/portal";

/// 热度前若干名单独成行（编号大字 + 增幅），其余进入词云
const LEAD_COUNT = 4;

/// 词云分级（tier 1 已单独成行，这里只用 2–5）
const TIER_CLASS: Record<Exclude<TopicHeatTier, 1>, string> = {
  2: "font-serif text-3xl font-medium tracking-tight text-fg-inverse-1",
  3: "font-serif text-xl font-medium text-fg-inverse-1",
  4: "font-sans text-md text-fg-inverse-2",
  5: "font-sans text-sm text-fg-inverse-3",
};

/// 首页「此刻热议」：墨色区块。左栏说明与冷热图例（桌面吸顶），右栏前四名编号大字 + 分级词云。
export function TopicCloud() {
  const lead = TOPIC_CLOUD.slice(0, LEAD_COUNT);
  const rest = TOPIC_CLOUD.slice(LEAD_COUNT);

  return (
    <section
      data-section="topic-cloud"
      className="relative isolate overflow-clip bg-bg-inverse text-fg-inverse-2"
    >
      <div className="mx-auto grid max-w-page grid-cols-1 gap-x-10 gap-y-12 px-gutter py-[clamp(80px,10vw,136px)] lg:grid-cols-12">
        <header className="self-start lg:sticky lg:top-24 lg:col-span-4">
          <SectionEyebrow index="01" tone="inverse" className="mb-5">
            此刻热议 · Trending now
          </SectionEyebrow>
          <h2 className="font-serif text-display-3 leading-heading font-medium tracking-tight break-keep text-fg-inverse-1">
            医学界
            <br />
            正在讨论<span className="text-accent-on-inverse">什么</span>
          </h2>
          <p className="mt-5 max-w-[30em] text-sm leading-relaxed break-keep text-fg-inverse-3">
            基于过去 72 小时的检索、被引与抓取频次合成的关键词热度。点选任一词条进入文献检索。
          </p>
          <div className="mt-8 flex flex-wrap items-center gap-x-4 gap-y-2 font-mono text-2xs uppercase tracking-caps text-fg-inverse-3">
            <span
              aria-hidden
              data-topic-heat-bar
              className="h-1.5 w-24 rounded-full bg-[linear-gradient(to_right,var(--ink-600),var(--ink-300),var(--clay-300))]"
            />
            <span>冷 → 热</span>
            <span>↗ Δ 较上周</span>
          </div>
          <p className="mt-3 font-mono text-2xs tracking-mono text-fg-inverse-3">
            截至 17:42 · 滑动 72h
          </p>
        </header>

        <div className="min-w-0 lg:col-span-8">
          <ol aria-label="热度前四" className="border-t border-border-inverse">
            {lead.map((t, i) => (
              <li key={t.term} className="reveal border-b border-border-inverse">
                <Link
                  href={buildPapersHref({ q: t.term })}
                  className="group/topic flex items-baseline gap-6 py-5 max-sm:gap-4 max-sm:py-4"
                >
                  <span aria-hidden className="w-7 shrink-0 font-mono text-xs text-fg-inverse-3">
                    {String(i + 1).padStart(2, "0")}
                  </span>
                  <span className="min-w-0 font-serif text-4xl leading-tight font-medium tracking-tight text-fg-inverse-1 transition-colors duration-300 group-hover/topic:text-accent-on-inverse max-sm:text-2xl">
                    {t.term}
                  </span>
                  <span className="ml-auto flex shrink-0 items-baseline gap-4 font-mono">
                    {t.delta && <span className="text-sm text-accent-on-inverse">↗ {t.delta}</span>}
                    <span className="text-xs tabular-nums text-fg-inverse-3 max-sm:hidden">
                      {t.count.toLocaleString()} 篇
                    </span>
                    <ArrowUpRight
                      aria-hidden
                      className="size-4 self-center text-fg-inverse-3 transition duration-300 ease-out-expo group-hover/topic:text-accent-on-inverse motion-safe:group-hover/topic:translate-x-0.5 motion-safe:group-hover/topic:-translate-y-0.5"
                      strokeWidth={1.5}
                    />
                  </span>
                </Link>
              </li>
            ))}
          </ol>

          <ul
            aria-label="更多热词"
            className="reveal mt-10 flex flex-wrap items-baseline gap-x-6 gap-y-4 leading-none max-sm:gap-x-4"
          >
            {rest.map((t) => {
              const tier = topicTier(t.heat);
              return (
                <li key={t.term}>
                  <Link
                    href={buildPapersHref({ q: t.term })}
                    title={`${t.count.toLocaleString()} 条相关文献`}
                    className={cn(
                      "link-draw pb-0.5 hover:text-accent-on-inverse",
                      TIER_CLASS[tier === 1 ? 2 : tier],
                    )}
                  >
                    {t.term}
                  </Link>
                </li>
              );
            })}
          </ul>

          <div className="mt-12 flex justify-end">
            <button
              type="button"
              disabled
              aria-disabled="true"
              title="功能即将上线"
              className="inline-flex items-center gap-1.5 text-sm text-fg-inverse-3 disabled:cursor-not-allowed"
            >
              浏览全部主题 <ArrowRight className="size-3.5" strokeWidth={1.5} />
              <span className="ml-1 rounded-full border border-border-inverse-strong px-2 py-px font-mono text-3xs tracking-mono">
                即将上线
              </span>
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}
