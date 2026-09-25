import type { EvidenceLevelCode, PaperSearchQuery } from "@/types/portal";

// ---- 常量 ----

/** 每页条数（与 BE pageSize 一致，不进 URL） */
export const PAPER_PAGE_SIZE = 20;

/** 页码上限：BE offset = (page − 1) × pageSize 为 Integer，超出即 422 */
export const MAX_PAGE = Math.floor(2_147_483_647 / PAPER_PAGE_SIZE);

/** 文本参数长度上限（与 BE @Size(max = 200) 一致） */
export const MAX_TEXT_LENGTH = 200;

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
    venue: listValues(sp.venue).filter(isVenueId),
    // BE 语言比较区分大小写，语言基码本身为小写
    lang: [...new Set(listValues(sp.lang).map((v) => v.toLowerCase()))],
    oa: firstValue(sp.oa) === "true",
    sort: firstValue(sp.sort).toLowerCase() === "year" ? "year" : "latest",
    page: pageValue(sp.page),
  };
}
