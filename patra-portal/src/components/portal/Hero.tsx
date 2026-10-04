import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { Composer, type ComposerSubmitEvent } from "@/components/portal/Composer";
import { Venation } from "@/components/portal/Venation";
import { PORTAL_STATS } from "@/data/portal-stats";

interface HeroProps {
  onComposerSubmit?: (event: ComposerSubmitEvent) => void;
}

/// 标题行外框：遮罩上移入场时裁掉行外部分；底部留白容纳下划线
const LINE = "block overflow-hidden pb-[0.1em] -mb-[0.1em]";

/// 首页 Hero：刊头条 → 三行海报级标题（逐行遮罩入场，「可被检索」陶土色 + 手绘下划线描出）→ 导语 → 检索框；
/// 右侧「图 1」叶脉按主脉 → 二级脉 → 细脉的次序描线入场。全部动效只在 motion-safe 下发生。
export function Hero({ onComposerSubmit }: HeroProps) {
  return (
    <section
      data-section="hero"
      className="relative isolate overflow-hidden bg-[radial-gradient(ellipse_70%_60%_at_88%_18%,var(--clay-50),transparent_70%)]"
    >
      <div className="mx-auto max-w-page px-gutter pt-8 pb-20 max-md:pb-14">
        {/* 刊头条 */}
        <div className="anim-fade flex items-center justify-between gap-4 border-y border-t-ink-900 border-b-border-default py-2.5 max-sm:py-2">
          <p className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-fg-3 max-sm:gap-x-3">
            <span className="inline-flex items-baseline gap-1.5 whitespace-nowrap">
              <b className="font-mono font-semibold tabular-nums text-ink-900">
                {PORTAL_STATS.records.toLocaleString()}
              </b>
              条文献
            </span>
            <span className="inline-flex items-baseline gap-1.5 whitespace-nowrap">
              <b className="font-mono font-semibold tabular-nums text-ink-900">
                {PORTAL_STATS.sources}
              </b>
              数据源
            </span>
            <span className="inline-flex items-baseline gap-1.5 whitespace-nowrap max-sm:hidden">
              更新于
              <b className="font-mono font-semibold tabular-nums text-ink-900">
                {PORTAL_STATS.lastIngestMin} 分钟前
              </b>
            </span>
          </p>
          <div className="inline-flex items-center gap-2 whitespace-nowrap font-mono text-2xs uppercase tracking-caps text-status-success-ink before:size-1.5 before:rounded-full before:bg-moss-500 before:content-[''] motion-safe:before:animate-[patra-live-pulse_1.6s_ease-in-out_infinite]">
            实时同步
          </div>
        </div>

        <div className="relative grid grid-cols-1 gap-x-8 pt-16 max-lg:pt-12 max-sm:pt-9 lg:grid-cols-12">
          <div className="relative z-10 lg:col-span-8">
            <h1 className="font-serif text-display-1 leading-display font-semibold tracking-tight text-fg-1">
              <span className={LINE}>
                <span className="anim-mask [--delay:60ms]">医学文献，</span>
              </span>
              <span className={LINE}>
                <span className="anim-mask [--delay:160ms]">
                  <span className="relative text-clay-700">
                    可被检索
                    <svg
                      aria-hidden
                      viewBox="0 0 400 24"
                      preserveAspectRatio="none"
                      className="absolute -bottom-[0.02em] left-[-2%] h-[0.16em] w-[104%] overflow-visible text-clay-500"
                    >
                      <path
                        d="M4 15 C 70 8, 150 5, 232 9 S 352 19, 396 8"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth={4}
                        strokeLinecap="round"
                        pathLength={1}
                        vectorEffect="non-scaling-stroke"
                        className="anim-draw [--delay:950ms] [--dur:900ms]"
                      />
                    </svg>
                  </span>
                  ，
                </span>
              </span>
              <span className={LINE}>
                <span className="anim-mask [--delay:260ms]">可被引用。</span>
              </span>
            </h1>

            <p className="anim-rise mt-9 max-w-[33em] font-serif text-xl leading-relaxed break-keep text-fg-2 [--delay:480ms] max-sm:mt-6 max-sm:text-lg">
              Patra 汇集 PubMed、Europe PMC、Crossref 等 {PORTAL_STATS.sources}{" "}
              个来源的学术文献，提供统一检索、出处追溯与 AI 辅助速读。门户对所有访客开放浏览。
            </p>
          </div>

          {/* 图 1：叶脉。脱离文档流：桌面在右侧立起、向下伸到检索框旁（检索框 z-10 压在其上）；
              窄屏退到标题背后作淡底纹，图注隐藏 */}
          <figure
            data-ornament
            className="pointer-events-none absolute top-[-12px] right-[-1%] m-0 flex flex-col items-end max-lg:top-0 max-lg:right-[-16%] max-lg:opacity-30 max-sm:right-[-38%] max-sm:opacity-20"
          >
            <Venation
              id="hero-leaf"
              animated
              className="h-[clamp(420px,46vw,680px)] w-auto -rotate-[16deg] max-sm:h-[400px]"
            />
            <figcaption className="anim-fade mt-1 mr-2 max-w-[24em] font-mono text-2xs leading-relaxed tracking-mono text-balance text-fg-3 [--delay:1600ms] max-lg:hidden">
              <span className="text-accent-strong">图 1</span>　叶脉——十个来源，汇入同一根主脉。
            </figcaption>
          </figure>
        </div>

        <div className="anim-rise relative z-10 mt-12 max-w-[940px] [--delay:620ms] max-sm:mt-8 lg:max-w-[min(940px,calc(100%-17rem))]">
          <Composer onSubmit={onComposerSubmit} />
          <p className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-fg-3">
            <span>或者，直接浏览</span>
            <Link
              href="/papers"
              className="group/link inline-flex items-center gap-1 font-medium text-ink-900"
            >
              <span className="link-draw pb-px">全部文献</span>
              <ArrowRight
                className="size-3.5 transition-transform duration-300 ease-out-expo motion-safe:group-hover/link:translate-x-0.5"
                strokeWidth={1.75}
                aria-hidden
              />
            </Link>
            <Link
              href="/journals"
              className="group/link inline-flex items-center gap-1 font-medium text-ink-900"
            >
              <span className="link-draw pb-px">全部期刊</span>
              <ArrowRight
                className="size-3.5 transition-transform duration-300 ease-out-expo motion-safe:group-hover/link:translate-x-0.5"
                strokeWidth={1.75}
                aria-hidden
              />
            </Link>
          </p>
        </div>
      </div>
    </section>
  );
}
