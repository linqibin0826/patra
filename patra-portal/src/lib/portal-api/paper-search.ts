import { EVIDENCE_LABELS, languageName, typeName } from "@/lib/paper-labels";
import type {
  ActiveFilterChip,
  ComposerMode,
  EvidenceLevelCode,
  FacetOption,
  PaperSearchQuery,
  PaperSortId,
} from "@/types/portal";

// ---- 常量 ----

/** 每页条数（与 BE pageSize 一致，不进 URL） */
export const PAPER_PAGE_SIZE = 20;

/** 页码上限：BE offset = (page − 1) × pageSize 为 Integer，超出即 422 */
export const MAX_PAGE = Math.floor(2_147_483_647 / PAPER_PAGE_SIZE);

/** 文本参数长度上限（与 BE @Size(max = 200) 一致） */
export const MAX_TEXT_LENGTH = 200;

/** 已选期刊上限：每个期刊 id 渲染时要单独查一次刊名，超出即丢弃，防止构造的 URL 放大后端请求 */
export const MAX_VENUES = 20;

/** BE venue 为 Long：id 不得超过 Long.MAX_VALUE */
const LONG_MAX = 9_223_372_036_854_775_807n;

/** PMID：1–15 位数字（与 BE @Pattern 一致） */
const PMID_PATTERN = /^\d{1,15}$/;

/** 证据等级枚举名，强 → 弱 */
export const EVIDENCE_CODES: readonly EvidenceLevelCode[] = [
  "SYSTEMATIC_REVIEW",
  "RANDOMIZED_CONTROLLED_TRIAL",
  "COHORT_OR_CASE_CONTROL",
  "NON_SYSTEMATIC_REVIEW",
  "CASE_REPORT",
  "UNKNOWN",
];

/** 无任何条件的默认查询（浏览态） */
export const EMPTY_PAPER_QUERY: PaperSearchQuery = {
  q: "",
  author: "",
  pmid: "",
  doi: "",
  yearFrom: null,
  yearTo: null,
  type: [],
  evidence: [],
  venue: [],
  lang: [],
  oa: false,
  sort: "latest",
  page: 1,
};

type RawParam = string | string[] | undefined;

// ---- 解析 ----

function firstValue(raw: RawParam): string {
  const value = Array.isArray(raw) ? raw[0] : raw;
  return (value ?? "").trim();
}

function textValue(raw: RawParam): string {
  const value = firstValue(raw);
  return value.length <= MAX_TEXT_LENGTH ? value : "";
}

function listValues(raw: RawParam): string[] {
  if (raw === undefined) return [];
  const values = Array.isArray(raw) ? raw : [raw];
  return [...new Set(values.map((v) => v.trim()).filter((v) => v.length > 0))];
}

/** 多值去重（忽略大小写，保留首次出现的写法）——与 BE lower(trim()) 的类型比较一致。 */
function uniqueIgnoreCase(values: string[]): string[] {
  const seen = new Set<string>();
  return values.filter((value) => {
    const key = value.toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function yearValue(raw: RawParam): number | null {
  const value = firstValue(raw);
  return /^[1-9]\d{3}$/.test(value) ? Number(value) : null;
}

function pageValue(raw: RawParam): number {
  const value = firstValue(raw);
  if (!/^\d{1,9}$/.test(value)) return 1;
  const page = Number(value);
  return page >= 1 && page <= MAX_PAGE ? page : 1;
}

function isVenueId(value: string): boolean {
  return /^[1-9]\d{0,18}$/.test(value) && BigInt(value) <= LONG_MAX;
}

function isEvidenceCode(value: string): value is EvidenceLevelCode {
  return (EVIDENCE_CODES as readonly string[]).includes(value);
}

/** PMID 是否合法（1–15 位数字）。检索条与首页搜索框共用。 */
export function isValidPmid(value: string): boolean {
  return PMID_PATTERN.test(value);
}

/**
 * 把 URL searchParams 解析为规范化的 PaperSearchQuery。
 * 按 BE 校验规则丢弃脏参数，保证发往 BE 的请求不会 422；
 * pmid / doi 出现时进入精确定位模式，只保留它（pmid 优先），其余条件、排序、页码回默认。
 */
export function parsePaperSearchQuery(sp: Record<string, RawParam>): PaperSearchQuery {
  const pmid = firstValue(sp.pmid);
  if (isValidPmid(pmid)) return { ...EMPTY_PAPER_QUERY, pmid };
  const doi = textValue(sp.doi);
  if (doi) return { ...EMPTY_PAPER_QUERY, doi };

  let yearFrom = yearValue(sp.yearFrom);
  let yearTo = yearValue(sp.yearTo);
  if (yearFrom !== null && yearTo !== null && yearFrom > yearTo) {
    [yearFrom, yearTo] = [yearTo, yearFrom];
  }

  return {
    q: textValue(sp.q),
    author: textValue(sp.author),
    pmid: "",
    doi: "",
    yearFrom,
    yearTo,
    type: uniqueIgnoreCase(listValues(sp.type)),
    evidence: [...new Set(listValues(sp.evidence).map((v) => v.toUpperCase()))].filter(
      isEvidenceCode,
    ),
    venue: listValues(sp.venue).filter(isVenueId).slice(0, MAX_VENUES),
    // BE 语言比较区分大小写，语言基码本身为小写
    lang: [...new Set(listValues(sp.lang).map((v) => v.toLowerCase()))],
    oa: firstValue(sp.oa) === "true",
    sort: firstValue(sp.sort).toLowerCase() === "year" ? "year" : "latest",
    page: pageValue(sp.page),
  };
}

// ---- 序列化 ----

/** 按固定顺序写入条件（sort / page 除外）；多值用重复参数，不拼逗号。 */
function appendConditions(params: URLSearchParams, query: PaperSearchQuery): void {
  if (query.q) params.set("q", query.q);
  if (query.author) params.set("author", query.author);
  if (query.pmid) params.set("pmid", query.pmid);
  if (query.doi) params.set("doi", query.doi);
  if (query.yearFrom !== null) params.set("yearFrom", String(query.yearFrom));
  if (query.yearTo !== null) params.set("yearTo", String(query.yearTo));
  for (const value of query.type) params.append("type", value);
  for (const value of query.evidence) params.append("evidence", value);
  for (const value of query.venue) params.append("venue", value);
  for (const value of query.lang) params.append("lang", value);
  if (query.oa) params.set("oa", "true");
}

/**
 * 把 PaperSearchQuery 序列化为 querystring（无前导 `?`）。
 * 默认值省略（sort=latest、page=1、空值），顺序稳定，可作 Suspense key。
 */
export function serializePaperSearchQuery(query: PaperSearchQuery): string {
  const params = new URLSearchParams();
  appendConditions(params, query);
  if (query.sort !== "latest") params.set("sort", query.sort);
  if (query.page > 1) params.set("page", String(query.page));
  return params.toString();
}

/** 查询对象 → `/papers` 站内链接。 */
export function papersHref(query: PaperSearchQuery): string {
  const qs = serializePaperSearchQuery(query);
  return qs ? `/papers?${qs}` : "/papers";
}

/** 按部分字段拼 `/papers` 链接（导航 / 首页搜索框 / 主题云 / 期刊详情等入口用）。 */
export function buildPapersHref(partial: Partial<PaperSearchQuery>): string {
  return papersHref({ ...EMPTY_PAPER_QUERY, ...partial });
}

/** BE `/portal/publications/search` query（无前导 ?）。 */
export function buildSearchApiQuery(query: PaperSearchQuery): string {
  const params = new URLSearchParams();
  appendConditions(params, query);
  params.set("sort", query.sort);
  params.set("page", String(query.page));
  params.set("pageSize", String(PAPER_PAGE_SIZE));
  return params.toString();
}

/** BE `/portal/publications/search/facets` query（无前导 ?）：只带条件。 */
export function buildFacetsApiQuery(query: PaperSearchQuery): string {
  const params = new URLSearchParams();
  appendConditions(params, query);
  return params.toString();
}

// ---- 状态判定 ----

/** 精确定位模式：pmid 或 doi 非空。 */
export function isExactLookup(query: PaperSearchQuery): boolean {
  return query.pmid !== "" || query.doi !== "";
}

/** 已选筛选项数（年份算 1 项；关键词 / 作者 / 精确定位不计入）——移动端角标与抽屉标题用。 */
export function filterCount(query: PaperSearchQuery): number {
  return (
    (query.yearFrom !== null || query.yearTo !== null ? 1 : 0) +
    query.type.length +
    query.evidence.length +
    query.venue.length +
    query.lang.length +
    (query.oa ? 1 : 0)
  );
}

/** 是否处于检索态（有任何检索或筛选条件；排序与页码不算）。 */
export function hasSearchConditions(query: PaperSearchQuery): boolean {
  return isExactLookup(query) || query.q !== "" || query.author !== "" || filterCount(query) > 0;
}

// ---- 状态变换（交互 → 下一个查询） ----

/** "近 N 年"快捷项 */
export const RECENT_YEAR_SPANS = [1, 3, 5] as const;

/** 提交关键词 / 作者：只替换该字段，清除 pmid / doi，保留其余筛选，回第 1 页。 */
export function submitTextSearch(
  query: PaperSearchQuery,
  field: "q" | "author",
  value: string,
): PaperSearchQuery {
  const base = { ...query, pmid: "", doi: "", page: 1 };
  const text = value.trim();
  return field === "q" ? { ...base, q: text } : { ...base, author: text };
}

/** 提交 PMID / DOI：精确定位，生成只含该字段的全新查询。 */
export function submitExactLookup(field: "pmid" | "doi", value: string): PaperSearchQuery {
  const text = value.trim();
  return field === "pmid"
    ? { ...EMPTY_PAPER_QUERY, pmid: text }
    : { ...EMPTY_PAPER_QUERY, doi: text };
}

function toggled<T extends string>(list: readonly T[], value: T): T[] {
  return list.includes(value) ? list.filter((v) => v !== value) : [...list, value];
}

/** 大小写不敏感的包含判断（类型与 BE lower(trim()) 比较一致）。 */
export function containsIgnoreCase(list: readonly string[], value: string): boolean {
  const key = value.toLowerCase();
  return list.some((v) => v.toLowerCase() === key);
}

function toggledIgnoreCase(list: readonly string[], value: string): string[] {
  const key = value.toLowerCase();
  return containsIgnoreCase(list, value)
    ? list.filter((v) => v.toLowerCase() !== key)
    : [...list, value];
}

/**
 * 筛选 / 年份 / 排序操作一律退出精确定位模式：否则下次解析时 pmid/doi 优先，
 * 刚做的操作会被丢弃（用户看到勾选后又恢复）。
 */
function leaveExactLookup(query: PaperSearchQuery): PaperSearchQuery {
  return isExactLookup(query) ? { ...query, pmid: "", doi: "" } : query;
}

/** 勾选 / 取消多值筛选项，回第 1 页；类型忽略大小写，语言统一小写；evidence 非法值原样返回。 */
export function toggleListValue(
  query: PaperSearchQuery,
  key: "type" | "evidence" | "venue" | "lang",
  value: string,
): PaperSearchQuery {
  const base = leaveExactLookup(query);
  switch (key) {
    case "type":
      return { ...base, type: toggledIgnoreCase(base.type, value), page: 1 };
    case "venue":
      return { ...base, venue: toggled(base.venue, value), page: 1 };
    case "lang":
      return { ...base, lang: toggled(base.lang, value.toLowerCase()), page: 1 };
    case "evidence": {
      const code = value.toUpperCase();
      return isEvidenceCode(code)
        ? { ...base, evidence: toggled(base.evidence, code), page: 1 }
        : query;
    }
  }
}

/** 切换"仅开放获取"，回第 1 页。 */
export function toggleOpenAccess(query: PaperSearchQuery): PaperSearchQuery {
  return { ...leaveExactLookup(query), oa: !query.oa, page: 1 };
}

/** 切换排序，回第 1 页。 */
export function withSort(query: PaperSearchQuery, sort: PaperSortId): PaperSearchQuery {
  return { ...leaveExactLookup(query), sort, page: 1 };
}

/** 清除全部条件（保留排序偏好）。 */
export function clearAllConditions(query: PaperSearchQuery): PaperSearchQuery {
  return { ...EMPTY_PAPER_QUERY, sort: query.sort };
}

/** 单选某一年（from = to = year）；已选中则取消。 */
export function selectExactYear(query: PaperSearchQuery, year: number): PaperSearchQuery {
  const active = query.yearFrom === year && query.yearTo === year;
  return {
    ...leaveExactLookup(query),
    yearFrom: active ? null : year,
    yearTo: active ? null : year,
    page: 1,
  };
}

/** "近 N 年"对应的 yearFrom（绝对年份，链接不随时间漂移）。 */
function recentYearsFrom(span: number, currentYear: number): number {
  return currentYear - span + 1;
}

/** "近 N 年"是否生效：yearFrom 恰为当年−N+1 且无上限。 */
export function isRecentYearsActive(
  query: PaperSearchQuery,
  span: number,
  currentYear: number,
): boolean {
  return query.yearTo === null && query.yearFrom === recentYearsFrom(span, currentYear);
}

/** 选中"近 N 年"（与逐年项互斥）；已选中则取消。 */
export function selectRecentYears(
  query: PaperSearchQuery,
  span: number,
  currentYear: number,
): PaperSearchQuery {
  const base = leaveExactLookup(query);
  if (isRecentYearsActive(query, span, currentYear)) {
    return { ...base, yearFrom: null, yearTo: null, page: 1 };
  }
  return { ...base, yearFrom: recentYearsFrom(span, currentYear), yearTo: null, page: 1 };
}

/** 门户"当前年份"：按 Asia/Shanghai 计算，由服务端算出后下发，保证两端一致。 */
export function currentPortalYear(now: Date = new Date()): number {
  const year = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
  }).format(now);
  return Number(year);
}

/** 年份 chip 文案；无年份条件时为 null。 */
export function yearChipLabel(
  yearFrom: number | null,
  yearTo: number | null,
  currentYear: number,
): string | null {
  if (yearFrom !== null && yearTo !== null) {
    return yearFrom === yearTo ? `${yearFrom} 年` : `${yearFrom}–${yearTo} 年`;
  }
  if (yearFrom !== null) {
    const span = currentYear - yearFrom + 1;
    return (RECENT_YEAR_SPANS as readonly number[]).includes(span)
      ? `近 ${span} 年`
      : `${yearFrom} 年起`;
  }
  if (yearTo !== null) return `${yearTo} 年及以前`;
  return null;
}

/**
 * facet 组可见项：折叠时取前 limit 项，已选但不在其中的项追加在后（保留计数，缺失记 0）；
 * 展开时全部可见，缺失的已选项同样以 0 追加。比较忽略大小写（展示用 facet 的规范写法）。
 */
export function visibleFacetOptions(
  options: readonly FacetOption[],
  selected: readonly string[],
  expanded: boolean,
  limit: number,
): FacetOption[] {
  const same = (a: string, b: string) => a.toLowerCase() === b.toLowerCase();
  const head = expanded ? [...options] : options.slice(0, limit);
  const extra = selected
    .filter((value) => !head.some((o) => same(o.value, value)))
    .map((value) => options.find((o) => same(o.value, value)) ?? { value, count: 0 });
  return [...head, ...extra];
}

// ---- chip ----

interface ChipContext {
  currentYear: number;
  venueNames: Readonly<Record<string, string>>;
}

/** 从查询推导已选条件 chip（每个 chip 带移除后的 next）。 */
export function derivePaperChips(
  query: PaperSearchQuery,
  ctx: ChipContext,
): ActiveFilterChip<PaperSearchQuery>[] {
  const chips: ActiveFilterChip<PaperSearchQuery>[] = [];
  if (query.q) {
    chips.push({
      group: "关键词",
      value: query.q,
      label: query.q,
      next: { ...query, q: "", page: 1 },
    });
  }
  if (query.author) {
    chips.push({
      group: "作者",
      value: query.author,
      label: query.author,
      next: { ...query, author: "", page: 1 },
    });
  }
  const yearLabel = yearChipLabel(query.yearFrom, query.yearTo, ctx.currentYear);
  if (yearLabel) {
    chips.push({
      group: "年份",
      value: "year",
      label: yearLabel,
      next: { ...query, yearFrom: null, yearTo: null, page: 1 },
    });
  }
  for (const value of query.type) {
    chips.push({
      group: "类型",
      value,
      label: typeName(value),
      next: toggleListValue(query, "type", value),
    });
  }
  for (const value of query.evidence) {
    chips.push({
      group: "证据等级",
      value,
      label: EVIDENCE_LABELS[value],
      next: toggleListValue(query, "evidence", value),
    });
  }
  for (const value of query.venue) {
    chips.push({
      group: "期刊",
      value,
      label: ctx.venueNames[value] ?? `期刊 #${value}`,
      next: toggleListValue(query, "venue", value),
    });
  }
  for (const value of query.lang) {
    chips.push({
      group: "语言",
      value,
      label: languageName(value),
      next: toggleListValue(query, "lang", value),
    });
  }
  if (query.oa) {
    chips.push({
      group: "开放获取",
      value: "oa",
      label: "仅开放获取",
      next: toggleOpenAccess(query),
    });
  }
  return chips;
}

// ---- 首页搜索框 ----

export type ComposerTarget =
  | { kind: "empty" }
  | { kind: "invalid"; message: string }
  | { kind: "ok"; href: string };

/**
 * 提交前校验（与 BE 约束一致）：PMID 须为 1–15 位数字，其余检索词不超过 200 字。
 * 合法返回 null；不合法返回提示文案——超长词若放行，解析层会丢弃它，用户会静默得到全库结果。
 */
export function validateSearchInput(mode: ComposerMode, text: string): string | null {
  if (mode === "pmid") return isValidPmid(text) ? null : "PMID 应为纯数字";
  return text.length <= MAX_TEXT_LENGTH ? null : `检索词最长 ${MAX_TEXT_LENGTH} 字`;
}

/** 首页 Composer 提交 → 跳转目标（与 /papers 解析规则同源）。 */
export function composerTarget(mode: ComposerMode, value: string): ComposerTarget {
  const text = value.trim();
  if (!text) return { kind: "empty" };
  const error = validateSearchInput(mode, text);
  if (error) return { kind: "invalid", message: error };
  switch (mode) {
    case "keyword":
      return { kind: "ok", href: buildPapersHref({ q: text }) };
    case "author":
      return { kind: "ok", href: buildPapersHref({ author: text }) };
    case "pmid":
      return { kind: "ok", href: buildPapersHref({ pmid: text }) };
    case "doi":
      return { kind: "ok", href: buildPapersHref({ doi: text }) };
  }
}
