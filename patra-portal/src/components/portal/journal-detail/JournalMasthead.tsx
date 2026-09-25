import { ArrowRight, Book, ExternalLink, Leaf } from "lucide-react";
import Link from "next/link";
import type { CSSProperties } from "react";
import { pickCover } from "@/lib/portal-api/cover-palette";
import { buildPapersHref } from "@/lib/portal-api/paper-search";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";
import type { VenueDetail } from "@/types/portal";

const ISSN_PLACEHOLDER = "XXXX-XXXX";

export function JournalMasthead({ venue }: { venue: VenueDetail }) {
  const { bg, ink } = pickCover(venue.id);
  const coverVars = { "--cover-bg": bg, "--cover-ink": ink } as CSSProperties;
  const word = venue.abbreviatedTitle?.trim() || venue.title;
  const issn = venue.issnL && venue.issnL !== ISSN_PLACEHOLDER ? venue.issnL : null;
  const factSep =
    "before:mx-2.5 before:text-ink-300 before:content-['·'] first:before:content-['']";

  return (
    <header className="grid grid-cols-[132px_minmax(0,1fr)] items-start gap-7 max-[540px]:grid-cols-[92px_1fr] max-[540px]:gap-[18px]">
      {word ? (
        <div
          style={coverVars}
          className="relative flex aspect-[3/4] items-center justify-center overflow-hidden rounded-lg border border-border-default bg-(--cover-bg) p-2.5 text-center text-(--cover-ink) before:absolute before:inset-[7px] before:border before:border-current before:opacity-20 before:content-['']"
        >
          <span className="whitespace-pre-line font-serif font-medium leading-[1.05] tracking-tight text-[clamp(16px,4.4vw,22px)]">
            {word}
          </span>
          {venue.foundedYear != null && (
            <span className="absolute right-1.5 bottom-2.5 left-1.5 text-center font-mono text-[8px] uppercase tracking-[0.14em] opacity-60">
              est. {venue.foundedYear}
            </span>
          )}
        </div>
      ) : (
        <div className="flex aspect-[3/4] items-center justify-center rounded-lg border border-dashed border-border-default bg-paper-200 text-ink-400">
          <span className="flex flex-col items-center gap-1.5 font-mono text-[9px] uppercase tracking-[0.08em]">
            <Book size={22} /> 暂无封面
          </span>
        </div>
      )}

      <div className="flex min-w-0 flex-col gap-2.5">
        {venue.isOpenAccess ? (
          <span className="inline-flex shrink-0 items-center gap-1.5 self-start rounded-full border border-moss-500 bg-moss-50 px-2 py-0.5 font-mono text-[10px] uppercase tracking-[0.04em] text-moss-500">
            <Leaf size={12} /> 开放获取
          </span>
        ) : (
          <span className="inline-flex shrink-0 items-center gap-1.5 self-start rounded-full border border-border-default bg-paper-100 px-2 py-0.5 font-mono text-[10px] uppercase tracking-[0.04em] text-fg-3">
            订阅 · 混合 OA
          </span>
        )}
        <h1 className="m-0 font-serif font-medium leading-[1.08] tracking-[-0.02em] text-fg-1 text-balance text-[clamp(26px,3.6vw,38px)]">
          {venue.title}
        </h1>
        {venue.abbreviatedTitle && (
          <div className="font-mono text-sm tracking-[0.02em] text-fg-3">
            {venue.abbreviatedTitle}
          </div>
        )}
        <div className="flex flex-wrap items-center gap-y-1 font-sans text-md text-fg-2">
          {issn && <span className={`font-mono text-sm ${factSep}`}>ISSN {issn}</span>}
          {venue.countryCode && <span className={factSep}>{venue.countryCode}</span>}
          {venue.foundedYear != null && <span className={factSep}>创刊 {venue.foundedYear}</span>}
        </div>
        <div className="mt-1.5 flex flex-wrap gap-2.5">
          {venue.homepageUrl ? (
            <a
              href={venue.homepageUrl}
              target="_blank"
              rel="noopener noreferrer"
              className={btnPrimary}
            >
              访问期刊官网 <ExternalLink size={14} />
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
            查看该刊文献 <ArrowRight size={14} />
          </Link>
        </div>
      </div>
    </header>
  );
}
