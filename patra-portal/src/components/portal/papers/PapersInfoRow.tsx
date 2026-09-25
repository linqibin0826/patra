import { hasSearchConditions } from "@/lib/portal-api/paper-search";
import { fetchPublicationFacets } from "@/lib/portal-api/publication-search";
import type { PaperSearchQuery } from "@/types/portal";

const SYNC_DATE_FORMAT = new Intl.DateTimeFormat("sv-SE", {
  timeZone: "Asia/Shanghai",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

/** ISO Instant → yyyy-MM-dd（Asia/Shanghai）。 */
export function formatSyncDate(iso: string): string {
  return SYNC_DATE_FORMAT.format(new Date(iso));
}

/**
 * 文献页信息行（async RSC）：浏览态"共 N 篇 · 最近同步 yyyy-MM-dd"，检索态"共 N 篇"。
 * 与筛选栏共用一次 facets 请求（React cache）。"来源"本版不显示（BE 无该数据，spec 决策 5）。
 */
export async function PapersInfoRow({ query }: { query: PaperSearchQuery }) {
  const facets = await fetchPublicationFacets(query);
  const syncedAt = hasSearchConditions(query) ? null : facets.lastSyncedAt;

  return (
    <p className="text-sm text-fg-2">
      共{" "}
      <strong className="font-mono font-semibold text-ink-900">
        {facets.total.toLocaleString("en-US")}
      </strong>{" "}
      篇
      {syncedAt && (
        <>
          <span aria-hidden="true" className="mx-2 text-fg-4">
            ·
          </span>
          最近同步{" "}
          <time dateTime={syncedAt} className="font-mono">
            {formatSyncDate(syncedAt)}
          </time>
        </>
      )}
    </p>
  );
}
