/**
 * Patra portal 领域类型 — 与 handoff `data.jsx` 实际字段对齐
 */

export type ComposerMode = "keyword" | "pmid" | "doi" | "author";
// trending 后端暂无热度信号，本版只暴露 recent / cited（见 spec §范围边界）
export type FeedTab = "recent" | "cited";
// 后端 source 来自 18 种 provenance 展示名或回退 code，故为开放 string（不可用三值 union）
export type PaperSource = string;
export type TopicHeatTier = 1 | 2 | 3 | 4 | 5;

export interface SearchMode {
  id: ComposerMode;
  label: string;
  placeholder: string;
  mono: boolean;
}

export interface ExampleQuery {
  mode: ComposerMode;
  text: string;
}

export interface PortalStats {
  records: number;
  sources: number;
  lastIngestMin: number;
  todayAdded: number;
  todayDelta: number;
}

export interface Topic {
  term: string;
  heat: number;
  count: number;
  delta?: string;
}

/**
 * 期刊浏览端点 `GET /portal/venues` 单条响应结构（VenueBrowse）。
 * 对应后端 `VenueBrowseDTO`，字段为首页卡片字段的超集。
 */
export interface VenueBrowse {
  id: string;
  name: string;
  abbr: string;
  coverObjectKey: string | null;
  impactFactor: number | null;
  jcrQuartile: string | null; // Q1–Q4
  jcrSubject: string | null;
  casMajorCategory: string | null;
  casMajorQuartile: string | null;
  casIsTop: boolean | null;
  countryCode: string | null;
  citedByCount: number | null;
  foundedYear: number | null;
  isOpenAccess: boolean | null;
  isInDoaj: boolean | null;
  issnL: string | null;
}

export interface JcrRating {
  year: number;
  impactFactor: number | null;
  quartile: string | null;
  subject: string | null;
  jifRank: string | null; // 形如 "5/245"
  jifPercentile: number | null;
}

export interface CasRating {
  year: number;
  edition: string | null;
  majorCategory: string | null;
  majorQuartile: string | null;
  minorSubject: string | null;
  minorQuartile: string | null;
  isTop: boolean | null;
  isReview: boolean | null;
}

export interface ScopusRating {
  year: number;
  citeScore: number | null;
  sjr: number | null;
  snip: number | null;
  quartile: string | null;
  percentile: number | null;
}

export interface YearlyStat {
  year: number;
  worksCount: number | null;
  citedByCount: number | null;
  oaWorksCount: number | null;
}

export interface VenueIdentifier {
  type: string;
  value: string;
  primary: boolean;
}

/**
 * 期刊详情端点 `GET /portal/venues/{id}` 响应（VenueDetail）。
 * 对应后端 `PortalVenueDetailResponse`——扁平"最新值快照" + 评级列表，零运行时映射。
 * id 为 string（BE 用 String 避免 JS 超 2^53 精度损失）。
 */
export interface VenueDetail {
  id: string;
  title: string;
  abbreviatedTitle: string | null;
  venueType: string | null;
  issnL: string | null;
  countryCode: string | null;
  primaryLanguage: string | null;
  foundedYear: number | null;
  coverObjectKey: string | null;
  homepageUrl: string | null; // BE 恒 null → 主操作降级
  isOpenAccess: boolean | null;
  // 顶层"最新值"快照
  impactFactor: number | null;
  jcrQuartile: string | null;
  jcrSubject: string | null;
  casMajorCategory: string | null;
  casMajorQuartile: string | null;
  casIsTop: boolean | null;
  citeScore: number | null;
  hIndex: number | null;
  citedByCount: number | null;
  worksCount: number | null;
  frequency: string | null;
  medlineIndexed: boolean | null;
  oaType: string | null;
  apcUsd: number | null;
  isInDoaj: boolean | null;
  // 完整评级列表（深数据层明细 + 派生来源）
  jcrRatings: JcrRating[];
  casRatings: CasRating[];
  scopusRatings: ScopusRating[];
  yearlyStats: YearlyStat[];
  identifiers: VenueIdentifier[];
}

export interface Paper {
  id: string;
  title: string;
  journal: string | null;
  year: number | null;
  authors: string[];
  cites: number | null;
  bookmarks: number;
  doi: string | null;
  pmid: string | null;
  source: PaperSource;
  aiSummary: string | null;
  estimatedReadMin: number | null;
  kind: string | null;
  minutesAgo: number | null; // 后端提供，UI 暂未展示（留作"X 分钟前"标签）
  venueId: string | null; // 载体主键（String，避免 JS 超 2^53 精度损失）；无载体为 null
  evidenceLevel: EvidenceLevel; // 与详情端点同形；未分级为 UNKNOWN（derived=false）
  abstractSnippet: string | null; // 摘要可见纯文本前 300 码点；无摘要为 null
}

/** 证据等级（BE EvidenceLevelView：rank 0–5，越大越强；derived = 非 UNKNOWN） */
export interface EvidenceLevel {
  level: string;
  rank: number;
  label: string;
  derived: boolean;
}

/** 结构化摘要段落；label 为 BACKGROUND / METHODS / RESULTS 等枚举值，混合摘要的无标签段为 null */
export interface AbstractSection {
  label: string | null;
  text: string;
}

/** 作者（order 为 1-based 排序，有意义）；affiliation 为首机构 */
export interface Author {
  order: number;
  first: boolean;
  corresponding: boolean;
  name: string;
  affiliation: string | null;
}

/** MeSH 主题词；major 标记主要主题概念 */
export interface MeshHeading {
  descriptorUi: string;
  term: string;
  major: boolean;
}

/** 资助信息；字段均可能为 null */
export interface Funding {
  funder: string | null;
  grantId: string | null;
  country: string | null;
}

/** 出版日期；type 如 received/accepted/epublished/published，date 为 "yyyy-MM-dd" */
export interface PublicationDate {
  type: string;
  date: string; // LocalDate 序列化为 "yyyy-MM-dd"
}

/**
 * 文献详情端点 `GET /portal/publications/{id}` 响应（PaperDetail）。
 * 对应后端 `PortalPublicationDetailResponse`——扁平 + 列表，零运行时映射。
 * source/fullTextUrl 为真实数据；bookmarks 恒 0、estimatedReadMin 恒 null（占位，无数据源）。
 */
export interface PaperDetail {
  id: string;
  title: string;
  originalTitle: string | null;
  venueId: string | null;
  venueName: string | null;
  publicationYear: number | null;
  evidenceLevel: EvidenceLevel;
  abstractType: string | null;
  abstractSections: AbstractSection[];
  abstractPlainText: string | null;
  doi: string | null;
  pmid: string | null;
  pmcid: string | null;
  pii: string | null;
  primaryType: string | null;
  publicationTypes: string[];
  citationCount: number | null;
  numberOfReferences: number | null;
  conflictOfInterest: string | null;
  isOa: boolean | null;
  oaStatus: string | null;
  authors: Author[];
  meshHeadings: MeshHeading[];
  keywords: string[];
  funding: Funding[];
  dates: PublicationDate[];
  aiSummary: string | null;
  source: string | null;
  fullTextUrl: string | null;
  bookmarks: number | null;
  estimatedReadMin: number | null;
}

/**
 * 后端 `dev.linqibin.commons.query.PageResult` 的序列化形态。
 * 字段：page / pageSize / total / totalPages / items（非 content/totalElements）。
 */
export interface PageResult<T> {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  items: T[];
}

// ---- 期刊浏览检索 ----

export type VenueSortId = "if" | "cas" | "az" | "cited";

export interface VenueBrowseQuery {
  q: string;
  sort: VenueSortId;
  page: number; // 1-based
  subject: string[];
  jcr: string[];
  cas: string[];
  casTop: boolean;
  oa: boolean;
  doaj: boolean;
  country: string[];
}

/** 筛选维度子集（不含 page/sort）——facet 取数与稳定 Suspense 边界用 */
export type VenueBrowseFilters = Omit<VenueBrowseQuery, "page" | "sort">;

export interface FacetOption {
  value: string;
  count: number;
}

export interface VenueBrowseFacets {
  subject: FacetOption[];
  jcr: FacetOption[];
  cas: FacetOption[];
  country: FacetOption[];
  casTop: number;
  oa: number;
  doaj: number;
}

export type VenueBrowsePage = PageResult<VenueBrowse>;

/** 已选筛选 chip（供 client 组件渲染 + 生成移除后的 query） */
export interface ActiveFilterChip<Q = VenueBrowseQuery> {
  group: string; // 中文组名，如 "学科" / "年份"
  value: string; // 原始值（用于移除定位与 React key）
  label: string; // 展示文案
  next: Q; // 移除该项后的 query
}

// ---- 文献检索 ----

/** 文献检索排序：latest = 最近更新（默认），year = 出版年份降序 */
export type PaperSortId = "latest" | "year";

/** 证据等级枚举名（与 BE EvidenceLevel 一致，强 → 弱） */
export type EvidenceLevelCode =
  | "SYSTEMATIC_REVIEW"
  | "RANDOMIZED_CONTROLLED_TRIAL"
  | "COHORT_OR_CASE_CONTROL"
  | "NON_SYSTEMATIC_REVIEW"
  | "CASE_REPORT"
  | "UNKNOWN";

/**
 * `/papers` 查询状态（URL 即状态）。字段名与 BE `/portal/publications/search` 参数一一对应。
 * 精确定位模式：pmid 或 doi 非空时其余字段恒为默认值（由 parsePaperSearchQuery 保证）。
 */
export interface PaperSearchQuery {
  q: string;
  author: string;
  pmid: string;
  doi: string;
  yearFrom: number | null;
  yearTo: number | null;
  type: string[];
  evidence: EvidenceLevelCode[];
  venue: string[]; // 期刊 id 保持字符串（Long 超出 JS 安全整数）
  lang: string[];
  oa: boolean;
  sort: PaperSortId;
  page: number; // 1-based
}

/** BE `/portal/publications/search/facets` 响应 */
export interface PublicationFacets {
  years: FacetOption[];
  types: FacetOption[];
  evidence: FacetOption[];
  languages: FacetOption[];
  openAccess: number;
  total: number;
  lastSyncedAt: string | null; // ISO-8601 Instant；空库为 null
}

/** 期刊候选（`/api/venues/suggest` 响应项） */
export interface VenueSuggestion {
  id: string;
  name: string;
  abbr: string;
}
