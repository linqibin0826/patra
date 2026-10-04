import { ArrowRight, Book, ExternalLink, Leaf } from "lucide-react";
import Link from "next/link";
import type { CSSProperties } from "react";
import { countryLabel } from "@/lib/country-name";
import { pickCover } from "@/lib/portal-api/cover-palette";
import { buildPapersHref } from "@/lib/portal-api/paper-search";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";
import type { VenueDetail } from "@/types/portal";

const ISSN_PLACEHOLDER = "XXXX-XXXX";

/// 期刊刊头：左侧书本式封面（书脊高光 + 内框压印 + 落影，与首页书架同款），右侧 OA 标记 → 显示级刊名 →
/// 缩写 → ISSN · 国家 · 创刊 → 官网 / 该刊文献。
export function JournalMasthead({ venue }: { venue: VenueDetail }) {
  const { bg, ink } = pickCover(venue.id);
  // 数据驱动的十六进制色经 CSS 变量传入
  const coverVars = { "--cover-bg": bg, "--cover-ink": ink } as CSSProperties;
  const word = venue.abbreviatedTitle?.trim() || venue.title;
  const issn = venue.issnL && venue.issnL !== ISSN_PLACEHOLDER ? venue.issnL : null;
  const factSep =
    "before:mx-3 before:text-ink-300 before:content-['·'] first:before:content-none first:before:mx-0";

  return (
    <header className="grid grid-cols-[168px_minmax(0,1fr)] items-start gap-9 max-[640px]:grid-cols-[108px_minmax(0,1fr)] max-[640px]:gap-5">
      {word ? (
        <div
          style={coverVars}
          className="anim-rise relative flex aspect-[3/4] items-center justify-center overflow-hidden rounded-[3px] bg-(--cover-bg) pr-3 pl-5 text-center text-(--cover-ink) shadow-cover before:absolute before:inset-y-0 before:left-0 before:w-4 before:bg-(image:--cover-spine) before:content-[''] after:absolute after:inset-y-2.5 after:right-2.5 after:left-5 after:border after:border-current after:opacity-25 after:content-['']"
        >
          <span className="font-serif text-[clamp(16px,4.4vw,24px)] leading-[1.05] font-medium tracking-tight whitespace-pre-line">
            {word}
          </span>
          {venue.foundedYear != null && (
            <span className="absolute right-3 bottom-4 left-5 text-center font-mono text-3xs uppercase tracking-spaced opacity-75">
              est. {venue.foundedYear}
            </span>
          )}
        </div>
      ) : (
        <div className="flex aspect-[3/4] items-center justify-center rounded-[3px] border border-dashed border-border-default bg-paper-200 text-fg-3">
          <span className="flex flex-col items-center gap-1.5 font-mono text-3xs uppercase tracking-caps">
            <Book size={22} /> 暂无封面
          </span>
        </div>
      )}

      <div className="flex min-w-0 flex-col gap-3">
        {venue.isOpenAccess ? (
          <span className="inline-flex shrink-0 items-center gap-1.5 self-start rounded-full border border-moss-500 bg-moss-50 px-2.5 py-0.5 font-mono text-3xs uppercase tracking-mono text-status-success-ink">
            <Leaf size={12} /> 开放获取
          </span>
        ) : (
          <span className="inline-flex shrink-0 items-center gap-1.5 self-start rounded-full border border-border-default bg-paper-50 px-2.5 py-0.5 font-mono text-3xs uppercase tracking-mono text-fg-3">
            订阅 · 混合 OA
          </span>
        )}
        <h1 className="anim-rise m-0 font-serif text-display-3 leading-heading font-medium tracking-tight text-balance text-fg-1 [--delay:80ms]">
          {venue.title}
        </h1>
        {venue.abbreviatedTitle && (
          <div className="font-mono text-sm tracking-mono text-fg-3">{venue.abbreviatedTitle}</div>
        )}
        <div className="flex flex-wrap items-center gap-y-1 font-sans text-md text-fg-2">
          {issn && <span className={`font-mono text-sm ${factSep}`}>ISSN {issn}</span>}
          {venue.countryCode && <span className={factSep}>{countryLabel(venue.countryCode)}</span>}
          {venue.foundedYear != null && <span className={factSep}>创刊 {venue.foundedYear}</span>}
        </div>
        <div className="mt-3 flex flex-wrap gap-2.5">
          {venue.homepageUrl ? (
            <a
              href={venue.homepageUrl}
              target="_blank"
              rel="noopener noreferrer"
              className={btnPrimary}
            >
              访问期刊官网 <ExternalLink size={14} data-icon="trailing" />
            </a>
          ) : (
            <button
              type="button"
              disabled
              title="官网链接待采集"
              className={`${btnSecondary} opacity-55`}
            >
              官网链接待采集
            </button>
          )}
          <Link href={buildPapersHref({ venue: [venue.id] })} className={btnSecondary}>
            查看该刊文献 <ArrowRight size={14} data-icon="trailing" />
          </Link>
        </div>
      </div>
    </header>
  );
}
