import { ArrowRight, ExternalLink } from "lucide-react";
import Link from "next/link";
import { buildPapersHref } from "@/lib/portal-api/paper-search";
import type { JournalMetrics } from "@/lib/portal-api/venue-derive";
import { btnBlock, btnPrimary } from "@/lib/portal-ui";
import type { VenueDetail } from "@/types/portal";

const CARD_LABEL =
  "mb-4 flex items-center gap-2 font-mono text-2xs uppercase tracking-mono text-fg-3";

/// 期刊详情右侧栏：本刊速览（核心指标 + 官网）→ 该刊文献入口（跳 /papers?venue=id）。
export function JournalRail({ venue, metrics }: { venue: VenueDetail; metrics: JournalMetrics }) {
  const { jcr, cas, bibliometric } = metrics;
  const stats: { k: string; v: string }[] = [];
  if (jcr?.impactFactor != null) {
    stats.push({ k: "影响因子", v: jcr.impactFactor.toFixed(1) });
  }
  if (jcr?.quartile) {
    stats.push({ k: "JCR 分区", v: jcr.quartile });
  }
  if (cas) {
    stats.push({ k: "中科院", v: `${cas.majorCategory ?? "—"} ${cas.majorQuartile ?? ""}`.trim() });
  }
  if (bibliometric?.hIndex != null) {
    stats.push({ k: "h-index", v: bibliometric.hIndex.toLocaleString() });
  }
  if (bibliometric?.citedByCount != null) {
    stats.push({ k: "被引总数", v: bibliometric.citedByCount.toLocaleString() });
  }
  if (venue.foundedYear != null) {
    stats.push({ k: "创刊", v: String(venue.foundedYear) });
  }

  return (
    <>
      <div className="rounded-xl border border-clay-200 bg-clay-50 p-5">
        <div className={CARD_LABEL}>
          <span aria-hidden className="h-3 w-1 rounded-[1px] bg-clay-600" />
          本刊速览
        </div>
        <dl className="m-0 flex flex-col">
          {stats.map((s) => (
            <div
              key={s.k}
              className="flex items-baseline justify-between gap-3 border-t border-clay-100 py-3 first:border-t-0 first:pt-0 last:pb-0"
            >
              <dt className="font-sans text-sm text-fg-3">{s.k}</dt>
              <dd className="m-0 text-right font-sans text-md font-semibold tabular-nums text-ink-900">
                {s.v}
              </dd>
            </div>
          ))}
        </dl>
        {venue.homepageUrl && (
          <a
            href={venue.homepageUrl}
            target="_blank"
            rel="noopener noreferrer"
            className={`${btnPrimary} ${btnBlock} mt-5`}
          >
            访问官网 <ExternalLink size={14} />
          </a>
        )}
      </div>

      <Link
        href={buildPapersHref({ venue: [venue.id] })}
        className="group/papers flex items-center justify-between gap-4 rounded-xl border border-border-default bg-paper-50 p-5 transition-colors duration-200 hover:border-ink-500"
      >
        <span className="flex flex-col gap-1.5">
          <span className="font-mono text-2xs uppercase tracking-mono text-fg-3">文献</span>
          <span className="font-serif text-xl leading-snug font-medium text-ink-900">
            浏览该刊文献
          </span>
          <span className="text-sm text-fg-3">按年份、类型与证据等级继续收敛</span>
        </span>
        <span
          aria-hidden
          className="flex size-10 shrink-0 items-center justify-center rounded-full bg-ink-900 text-paper-50 transition-transform duration-300 ease-out-expo motion-safe:group-hover/papers:translate-x-1"
        >
          <ArrowRight className="size-4" strokeWidth={1.75} />
        </span>
      </Link>
    </>
  );
}
