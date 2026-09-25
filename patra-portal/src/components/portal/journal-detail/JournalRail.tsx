import { ExternalLink } from "lucide-react";
import Image from "next/image";
import type { JournalMetrics } from "@/lib/portal-api/venue-derive";
import { btnBlock, btnPrimary } from "@/lib/portal-ui";
import type { VenueDetail } from "@/types/portal";

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
      <div className="rounded-lg border border-clay-200 bg-clay-50 p-4">
        <div className="mb-3 flex items-center gap-2 font-mono text-[9.5px] uppercase tracking-[0.06em] text-fg-3">
          <Image src="/brand/patra-mark.svg" alt="" aria-hidden width={4} height={13} /> 本刊速览
        </div>
        <div className="flex flex-col">
          {stats.map((s) => (
            <div
              key={s.k}
              className="flex items-baseline justify-between gap-3 border-t border-border-subtle py-2.5 first:border-t-0"
            >
              <span className="font-sans text-sm text-fg-3">{s.k}</span>
              <span className="font-mono text-md font-medium tabular-nums text-ink-900">{s.v}</span>
            </div>
          ))}
        </div>
        {venue.homepageUrl && (
          <a
            href={venue.homepageUrl}
            target="_blank"
            rel="noopener noreferrer"
            className={`${btnPrimary} ${btnBlock} mt-3.5`}
          >
            访问官网 <ExternalLink size={14} />
          </a>
        )}
      </div>

      <div className="rounded-lg border border-border-default bg-paper-50 p-4">
        <div className="mb-3 font-mono text-[9.5px] uppercase tracking-[0.06em] text-fg-3">
          边界 · v0.5
        </div>
        <p className="m-0 font-sans text-sm leading-normal text-fg-3">
          本页用于认识与评估期刊，<b className="text-fg-2">不含该刊的文献列表</b>
          。文献浏览将在后续版本提供。
        </p>
      </div>
    </>
  );
}
