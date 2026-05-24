import Image from "next/image";
import { Composer, type ComposerSubmitEvent } from "@/components/portal/Composer";
import { PORTAL_STATS } from "@/data/portal-stats";

interface HeroProps {
  onComposerSubmit?: (event: ComposerSubmitEvent) => void;
}

export function Hero({ onComposerSubmit }: HeroProps) {
  return (
    <section
      id="top"
      data-section="hero"
      className="relative overflow-hidden py-16 max-[880px]:py-10"
    >
      <div className="container mx-auto max-w-[1200px] px-6">
        {/* Masthead */}
        <div className="mb-10 flex items-center justify-between gap-4 border-y border-t-ink-900 border-b-border-default py-2 max-[880px]:mb-7 max-sm:py-1.5">
          <div className="flex flex-wrap gap-x-5 gap-y-1 text-[11.5px] text-fg-3 max-sm:gap-x-2.5">
            <span className="inline-flex items-baseline gap-1.5 whitespace-nowrap">
              <b className="font-mono text-xs font-semibold tabular-nums text-ink-900">
                {PORTAL_STATS.records.toLocaleString()}
              </b>
              条文献
            </span>
            <span className="inline-flex items-baseline gap-1.5 whitespace-nowrap">
              <b className="font-mono text-xs font-semibold tabular-nums text-ink-900">
                {PORTAL_STATS.sources}
              </b>
              数据源
            </span>
            <span className="inline-flex items-baseline gap-1.5 whitespace-nowrap max-sm:hidden">
              更新于
              <b className="font-mono text-xs font-semibold tabular-nums text-ink-900">
                {PORTAL_STATS.lastIngestMin} 分钟前
              </b>
            </span>
          </div>
          <div className="inline-flex items-center gap-1.5 whitespace-nowrap font-mono text-[10.5px] uppercase tracking-caps text-moss-500 before:h-1.5 before:w-1.5 before:rounded-full before:bg-moss-500 before:animate-[patra-live-pulse_1.6s_ease-in-out_infinite] before:content-['']">
            实时同步
          </div>
        </div>

        {/* Headline grid */}
        <div className="mb-7 grid grid-cols-[minmax(0,1fr)_auto] items-end gap-8 max-[880px]:grid-cols-1 max-[880px]:gap-2">
          <h1 className="m-0 font-serif text-balance font-medium leading-[1.04] tracking-tight text-fg-1 text-[clamp(36px,5.4vw,60px)]">
            医学文献，
            <br />
            <em className="font-medium italic text-clay-700">可被检索</em>
            ，可被引用。
          </h1>
          <div
            data-ornament
            className="shrink-0 self-end text-ink-900 opacity-90 max-[880px]:hidden"
          >
            <Image src="/brand/patra-mark.svg" alt="" aria-hidden width={72} height={280} />
          </div>
        </div>

        <p className="mb-9 max-w-[640px] font-serif leading-[1.55] text-fg-2 text-[clamp(16px,1.6vw,18px)] text-pretty max-[880px]:mb-6">
          Patra 汇集 PubMed、Europe PMC、Crossref 等 10 个来源的学术文献，提供统一检索、出处追溯与
          AI 辅助速读。门户对所有访客开放浏览。
        </p>

        <Composer onSubmit={onComposerSubmit} />
      </div>
    </section>
  );
}
