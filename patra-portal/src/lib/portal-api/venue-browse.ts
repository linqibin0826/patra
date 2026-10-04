import { formatSubject } from "@/lib/subject-label";
import type {
  ActiveFilterChip,
  VenueBrowseFilters,
  VenueBrowseQuery,
  VenueSortId,
} from "@/types/portal";

// ---- 常量 ----

export const SORT_OPTIONS: { id: VenueSortId; label: string; desc?: boolean }[] = [
  { id: "if", label: "影响因子", desc: true },
  { id: "cas", label: "中科院分区" },
  { id: "az", label: "刊名 A–Z" },
  { id: "cited", label: "被引总数", desc: true },
];

export const JCR_QUARTILE_ORDER = ["Q1", "Q2", "Q3", "Q4"] as const;
export const CAS_ZONE_ORDER = ["1区", "2区", "3区", "4区"] as const;

export const SORT_CODE_MAP: Record<VenueSortId, string> = {
  if: "impact_factor",
  cas: "cas_quartile",
  az: "title",
  cited: "cited_by",
};

export const DEFAULT_PAGE_SIZE = 12;

// ---- 默认 query ----

const VALID_SORT_IDS = new Set<string>(["if", "cas", "az", "cited"]);

// ---- 内部工具 ----

/**
 * 把 sp 中的某个多值参数统一拆成去空字符串数组。
 * 支持 CSV 字符串、string[]、以及混合形态。
 */
function parseMultiValue(raw: string | string[] | undefined): string[] {
  if (!raw) return [];
  const values = Array.isArray(raw) ? raw : [raw];
  return values
    .flatMap((v) => v.split(","))
    .map((v) => v.trim())
    .filter((v) => v.length > 0);
}

function parseBool(raw: string | string[] | undefined): boolean {
  const v = Array.isArray(raw) ? raw[0] : raw;
  return v === "1" || v === "true";
}

function parsePageInt(raw: string | string[] | undefined): number {
  const v = Array.isArray(raw) ? raw[0] : raw;
  const n = parseInt(v ?? "", 10);
  return Number.isNaN(n) || n <= 0 ? 1 : n;
}

// ---- 公开纯函数 ----

/**
 * 把 URL searchParams（Record 形式）解析为规范化的 VenueBrowseQuery。
 * 支持 CSV 多值、重复参数、布尔、sort 白名单兜底等。
 */
export function parseVenueBrowseQuery(
  sp: Record<string, string | string[] | undefined>,
): VenueBrowseQuery {
  const sortRaw = Array.isArray(sp.sort) ? sp.sort[0] : sp.sort;
  const sort: VenueSortId = VALID_SORT_IDS.has(sortRaw ?? "") ? (sortRaw as VenueSortId) : "if";

  return {
    q: (Array.isArray(sp.q) ? sp.q[0] : sp.q) ?? "",
    sort,
    page: parsePageInt(sp.page),
    subject: parseMultiValue(sp.subject),
    jcr: parseMultiValue(sp.jcr),
    cas: parseMultiValue(sp.cas),
    casTop: parseBool(sp.casTop),
    oa: parseBool(sp.oa),
    doaj: parseBool(sp.doaj),
    country: parseMultiValue(sp.country),
  };
}

/// 已选筛选条件数（检索词、排序、页码不算）：多值维度按选项数计，布尔开关各计 1。
export function venueFilterCount(query: VenueBrowseQuery): number {
  return (
    query.subject.length +
    query.jcr.length +
    query.cas.length +
    query.country.length +
    (query.casTop ? 1 : 0) +
    (query.oa ? 1 : 0) +
    (query.doaj ? 1 : 0)
  );
}

/**
 * 把 VenueBrowseQuery 序列化为 querystring（无前导 `?`）。
 * 默认值省略：sort==="if"、page<=1、空数组、false、q==="" 均不写。
 * 顺序稳定，多值用逗号 join，往返一致。
 */
export function serializeVenueBrowseQuery(query: VenueBrowseQuery): string {
  const params = new URLSearchParams();

  if (query.q) params.set("q", query.q);
  if (query.sort !== "if") params.set("sort", query.sort);
  if (query.page > 1) params.set("page", String(query.page));
  if (query.subject.length > 0) params.set("subject", query.subject.join(","));
  if (query.jcr.length > 0) params.set("jcr", query.jcr.join(","));
  if (query.cas.length > 0) params.set("cas", query.cas.join(","));
  if (query.country.length > 0) params.set("country", query.country.join(","));
  if (query.casTop) params.set("casTop", "true");
  if (query.oa) params.set("oa", "true");
  if (query.doaj) params.set("doaj", "true");

  return params.toString();
}

/**
 * 去掉 page/sort，得到 VenueBrowseFilters（facet 取数与 Suspense key 用）。
 */
export function toFilters(query: VenueBrowseQuery): VenueBrowseFilters {
  const { page: _page, sort: _sort, ...filters } = query;
  return filters;
}

/**
 * 拼 BE `/portal/venues` query（无前导 ?）。
 * sort 使用 SORT_CODE_MAP 映射；多值逗号 CSV；boolean 仅 true 时带。
 */
export function buildVenuesPageApiQuery(query: VenueBrowseQuery): string {
  const params = new URLSearchParams();

  if (query.q) params.set("q", query.q);
  // sort=if（impact_factor）BE 可接受省略，但为明确起见仍可省略或写出
  // 按任务要求：if 可省略
  if (query.sort !== "if") params.set("sort", SORT_CODE_MAP[query.sort]);
  params.set("page", String(query.page));
  params.set("pageSize", String(DEFAULT_PAGE_SIZE));
  if (query.subject.length > 0) params.set("subject", query.subject.join(","));
  if (query.jcr.length > 0) params.set("jcr", query.jcr.join(","));
  if (query.cas.length > 0) params.set("cas", query.cas.join(","));
  if (query.casTop) params.set("casTop", "true");
  if (query.oa) params.set("oa", "true");
  if (query.doaj) params.set("doaj", "true");
  if (query.country.length > 0) params.set("country", query.country.join(","));

  return params.toString();
}

/**
 * 拼 `/portal/venues/facets` query（无前导 ?）。
 * 仅含 q + 筛选维度，无 page/sort/pageSize。
 */
export function buildVenuesFacetsApiQuery(filters: VenueBrowseFilters): string {
  const params = new URLSearchParams();

  if (filters.q) params.set("q", filters.q);
  if (filters.subject.length > 0) params.set("subject", filters.subject.join(","));
  if (filters.jcr.length > 0) params.set("jcr", filters.jcr.join(","));
  if (filters.cas.length > 0) params.set("cas", filters.cas.join(","));
  if (filters.casTop) params.set("casTop", "true");
  if (filters.oa) params.set("oa", "true");
  if (filters.doaj) params.set("doaj", "true");
  if (filters.country.length > 0) params.set("country", filters.country.join(","));

  return params.toString();
}

// ---- chip 配置 ----

interface ChipGroupConfig {
  group: string;
  getValues: (q: VenueBrowseQuery) => string[];
  getLabel?: (value: string) => string;
  makeNext: (q: VenueBrowseQuery, value: string) => VenueBrowseQuery;
}

const CHIP_GROUP_CONFIGS: ChipGroupConfig[] = [
  {
    group: "学科",
    getValues: (q) => q.subject,
    getLabel: formatSubject,
    makeNext: (q, value) => ({ ...q, subject: q.subject.filter((v) => v !== value), page: 1 }),
  },
  {
    group: "JCR 分区",
    getValues: (q) => q.jcr,
    makeNext: (q, value) => ({ ...q, jcr: q.jcr.filter((v) => v !== value), page: 1 }),
  },
  {
    group: "中科院分区",
    getValues: (q) => q.cas,
    makeNext: (q, value) => ({ ...q, cas: q.cas.filter((v) => v !== value), page: 1 }),
  },
  {
    group: "国家",
    getValues: (q) => q.country,
    makeNext: (q, value) => ({ ...q, country: q.country.filter((v) => v !== value), page: 1 }),
  },
];

const BOOL_CHIP_CONFIGS: {
  group: string;
  key: "casTop" | "oa" | "doaj";
  value: string;
  label: string;
}[] = [
  { group: "中科院 Top", key: "casTop", value: "casTop", label: "Top 期刊" },
  { group: "开放获取", key: "oa", value: "oa", label: "开放获取" },
  { group: "DOAJ", key: "doaj", value: "doaj", label: "DOAJ 收录" },
];

/**
 * 遍历 query 中所有已选筛选项，生成 ActiveFilterChip[]。
 * 每个 chip 含移除该项后的 next query。空则 []。
 */
export function deriveActiveChips(query: VenueBrowseQuery): ActiveFilterChip[] {
  const chips: ActiveFilterChip[] = [];

  for (const config of CHIP_GROUP_CONFIGS) {
    for (const value of config.getValues(query)) {
      chips.push({
        group: config.group,
        value,
        label: config.getLabel ? config.getLabel(value) : value,
        next: config.makeNext(query, value),
      });
    }
  }

  for (const boolConfig of BOOL_CHIP_CONFIGS) {
    if (query[boolConfig.key]) {
      chips.push({
        group: boolConfig.group,
        value: boolConfig.value,
        label: boolConfig.label,
        next: { ...query, [boolConfig.key]: false, page: 1 },
      });
    }
  }

  return chips;
}
