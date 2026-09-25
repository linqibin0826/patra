import Link from "next/link";
import { DisclosureSection } from "@/components/portal/DisclosureSection";
import { IdentifierChip } from "@/components/portal/IdentifierChip";
import { JournalMasthead } from "@/components/portal/journal-detail/JournalMasthead";
import { JournalPositioning } from "@/components/portal/journal-detail/JournalPositioning";
import { JournalRail } from "@/components/portal/journal-detail/JournalRail";
import { MetricBadge } from "@/components/portal/journal-detail/MetricBadge";
import { RatingTable } from "@/components/portal/journal-detail/RatingTable";
import { TrendChart } from "@/components/portal/journal-detail/TrendChart";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import {
  deriveMetricCards,
  deriveMetrics,
  deriveSubjectAreas,
  deriveYearlyStats,
} from "@/lib/portal-api/venue-derive";
import type { VenueDetail } from "@/types/portal";

const ISSN_PLACEHOLDER = "XXXX-XXXX";
const DL = "grid grid-cols-[max-content_1fr] gap-x-5 gap-y-2.5 max-[540px]:grid-cols-1";
const DT = "whitespace-nowrap font-sans text-sm text-fg-3";
const DD = "m-0 font-sans text-md text-fg-1";
const DD_MONO = "m-0 font-mono text-sm text-fg-1";

export function JournalDetailView({ venue }: { venue: VenueDetail }) {
  const metrics = deriveMetrics(venue);
  const cards = deriveMetricCards(metrics);
  const subjects = deriveSubjectAreas(venue);
  const yearly = deriveYearlyStats(venue);
  const { bibliometric } = metrics;
  const idents = venue.identifiers.filter((i) => i.value && i.value !== ISSN_PLACEHOLDER);
  const hasBiblio = bibliometric != null || yearly.length > 0;

  return (
    <div className="pb-6">
      <nav
        aria-label="面包屑"
        className="sticky top-14 z-30 border-b border-border-default bg-bg-sticky backdrop-blur"
      >
        <ol className="mx-auto flex h-11 max-w-[1200px] items-center gap-2 px-6 font-sans text-sm text-fg-3">
          <li>
            <Link
              href="/"
              className="rounded-sm px-1 py-0.5 text-fg-2 hover:bg-paper-200 hover:text-clay-700"
            >
              Patra
            </Link>
          </li>
          <li aria-hidden className="text-ink-300">
            /
          </li>
          <li className="max-[720px]:hidden">
            <Link
              href="/journals"
              className="rounded-sm px-1 py-0.5 text-fg-2 hover:bg-paper-200 hover:text-clay-700"
            >
              期刊
            </Link>
          </li>
          <li aria-hidden className="text-ink-300 max-[720px]:hidden">
            /
          </li>
          <li aria-current="page" className="truncate font-medium text-fg-1">
            {venue.abbreviatedTitle || venue.title}
          </li>
        </ol>
      </nav>

      <div className="mx-auto grid max-w-[1200px] grid-cols-[minmax(0,1fr)_340px] items-start gap-10 px-6 pt-8 max-[980px]:grid-cols-1 max-[980px]:gap-7 max-[980px]:pt-6">
        <div className="flex min-w-0 flex-col gap-7">
          <JournalMasthead venue={venue} />

          {cards.length > 0 && (
            <section aria-label="影响力速览">
              <SectionEyebrow>影响力速览</SectionEyebrow>
              <div className="grid grid-cols-3 gap-3 max-[540px]:grid-cols-2">
                {cards.map((c) => (
                  <MetricBadge key={c.key} card={c} />
                ))}
              </div>
            </section>
          )}

          <JournalPositioning venue={venue} subjects={subjects} />

          <section aria-label="深度数据">
            <SectionEyebrow>深度数据 · 按需展开</SectionEyebrow>
            <div>
              <DisclosureSection title="完整评级明细" count="JCR · CAS · Scopus" defaultOpen>
                <RatingTable metrics={metrics} />
              </DisclosureSection>

              {hasBiblio && (
                <DisclosureSection title="文献计量与趋势">
                  {bibliometric && (
                    <dl className={`${DL} ${yearly.length > 0 ? "mb-6" : ""}`}>
                      <dt className={DT}>h-index</dt>
                      <dd className={DD_MONO}>{bibliometric.hIndex?.toLocaleString() ?? "—"}</dd>
                      <dt className={DT}>i10-index</dt>
                      <dd className={DD_MONO}>{bibliometric.i10Index?.toLocaleString() ?? "—"}</dd>
                      <dt className={DT}>被引总数</dt>
                      <dd className={DD_MONO}>
                        {bibliometric.citedByCount?.toLocaleString() ?? "—"}
                      </dd>
                      <dt className={DT}>累计发文</dt>
                      <dd className={DD_MONO}>
                        {bibliometric.worksCount?.toLocaleString() ?? "—"}
                      </dd>
                    </dl>
                  )}
                  <TrendChart stats={yearly} />
                </DisclosureSection>
              )}

              <DisclosureSection title="收录与出版">
                <dl className={DL}>
                  <dt className={DT}>出版频率</dt>
                  <dd className={DD}>{venue.frequency ?? "—"}</dd>
                  <dt className={DT}>创刊年</dt>
                  <dd className={DD_MONO}>{venue.foundedYear ?? "—"}</dd>
                  <dt className={DT}>MEDLINE 索引</dt>
                  <dd className={DD}>
                    {venue.medlineIndexed == null
                      ? "—"
                      : venue.medlineIndexed
                        ? "当前收录中"
                        : "未收录"}
                  </dd>
                  <dt className={DT}>语言</dt>
                  <dd className={DD}>{venue.primaryLanguage ?? "—"}</dd>
                  <dt className={DT}>国家 / 地区</dt>
                  <dd className={DD}>{venue.countryCode ?? "—"}</dd>
                </dl>
              </DisclosureSection>

              <DisclosureSection title="开放获取细节">
                <dl className={DL}>
                  <dt className={DT}>OA 类型</dt>
                  <dd className={DD}>
                    {venue.isOpenAccess ? (venue.oaType ?? "开放获取") : "订阅 / 混合"}
                  </dd>
                  {venue.apcUsd != null && (
                    <>
                      <dt className={DT}>APC 费用</dt>
                      <dd className={DD_MONO}>US$ {venue.apcUsd.toLocaleString()}</dd>
                    </>
                  )}
                  <dt className={DT}>DOAJ 收录</dt>
                  <dd className={DD}>
                    {venue.isInDoaj == null ? "—" : venue.isInDoaj ? "是" : "否"}
                  </dd>
                </dl>
              </DisclosureSection>

              {idents.length > 0 && (
                <DisclosureSection title="关系与标识">
                  <div className="flex flex-wrap gap-2.5">
                    {idents.map((i) => (
                      <IdentifierChip key={`${i.type}-${i.value}`} label={i.type} value={i.value} />
                    ))}
                  </div>
                </DisclosureSection>
              )}
            </div>
          </section>
        </div>

        {/* sticky top = TopNav 56px + 面包屑 bar 44px + 16px 间距 = 116px */}
        <aside className="sticky top-[116px] flex flex-col gap-4 max-[980px]:static">
          <JournalRail venue={venue} metrics={metrics} />
        </aside>
      </div>
    </div>
  );
}
