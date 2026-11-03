import type { SearchPerformed } from "@/lib/analytics/generated/events";
import { EMPTY_PAPER_QUERY, isExactLookup } from "@/lib/portal-api/paper-search";
import type { PaperSearchQuery } from "@/types/portal";

/// 契约上限（`events/search_performed.schema.json`）
const MAX_QUERY = 200;
const MAX_ITEMS = 20;
const ENTITY_ID = /^[1-9][0-9]{0,18}$/;

/// 去空、去重、每项截长、最多 20 项
function list(values: readonly string[], maxLength: number): string[] {
  return [
    ...new Set(values.map((value) => value.slice(0, maxLength)).filter((v) => v !== "")),
  ].slice(0, MAX_ITEMS);
}

/// 门户的检索条件 → `search_performed` 的属性（spec §4.2）。精确定位时 `query` 是 PMID / DOI 本身；
/// 没用到的筛选键不出现，`oa` 只在为真时出现；超出契约上限的截断
export function searchPerformedProperties(
  query: PaperSearchQuery,
  searchId: string,
  resultCount: number,
): SearchPerformed {
  const pmid = query.pmid.trim();
  const doi = query.doi.trim();
  const mode = pmid ? "exact_pmid" : doi ? "exact_doi" : "keyword";
  const filters: SearchPerformed["filters"] = {};
  const author = query.author.trim().slice(0, MAX_QUERY);
  if (author) filters.author = author;
  if (query.yearFrom !== null) filters.year_from = query.yearFrom;
  if (query.yearTo !== null) filters.year_to = query.yearTo;
  const arrays = {
    type: list(query.type, 100),
    evidence: list(query.evidence, 32),
    lang: list(query.lang, 16),
    venue: list(query.venue, 19).filter((id) => ENTITY_ID.test(id)),
  };
  for (const [key, values] of Object.entries(arrays) as [keyof typeof arrays, string[]][]) {
    if (values.length > 0) filters[key] = values;
  }
  if (query.oa) filters.oa = true;
  return {
    search_id: searchId,
    mode,
    query: (mode === "exact_pmid" ? pmid : mode === "exact_doi" ? doi : query.q.trim()).slice(
      0,
      MAX_QUERY,
    ),
    filters,
    sort: query.sort,
    page: query.page,
    result_count: resultCount,
  };
}

/// 无任何条件的浏览态（没有检索词、没有筛选、没有精确定位、第 1 页、默认排序）：不算一次检索
export function isBrowseState(query: PaperSearchQuery): boolean {
  return (
    !isExactLookup(query) &&
    query.q.trim() === "" &&
    query.author.trim() === "" &&
    query.yearFrom === null &&
    query.yearTo === null &&
    query.type.length === 0 &&
    query.evidence.length === 0 &&
    query.venue.length === 0 &&
    query.lang.length === 0 &&
    !query.oa &&
    query.page === 1 &&
    query.sort === EMPTY_PAPER_QUERY.sort
  );
}
