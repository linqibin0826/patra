import "server-only";
import { cache } from "react";
import {
  buildVenuesFacetsApiQuery,
  buildVenuesPageApiQuery,
  DEFAULT_PAGE_SIZE,
} from "@/lib/portal-api/venue-browse";
import type {
  PageResult,
  VenueBrowse,
  VenueBrowseFacets,
  VenueBrowseFilters,
  VenueBrowsePage,
  VenueBrowseQuery,
  VenueDetail,
  VenueSuggestion,
} from "@/types/portal";

/** 服务端 fetch 超时（ms）：慢后端不应无限阻塞 RSC 渲染线程 */
const FETCH_TIMEOUT_MS = 10_000;

/**
 * 在服务端拉取 portal 期刊榜（按影响因子降序，取前 pageSize 条）。
 * 仅 RSC / Route Handler 调用（server-only）。
 * 通过 gateway 访问 catalog，gateway 地址只在服务端可见。
 *
 * 端点：`GET /portal/venues?sort=impactFactor&pageSize=<n>`
 * 响应：`PageResult<VenueBrowse>`，期刊数组在 `.items`。
 *
 * 响应做最小运行时校验：若 payload 缺少 `items` 数组则降级返回空列表，避免调用方 `.length` 抛错。
 */
export async function fetchVenues(pageSize = 6): Promise<VenueBrowse[]> {
  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
  if (!baseUrl) {
    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
  }
  const url = `${baseUrl}/patra-catalog/portal/venues?sort=impactFactor&pageSize=${pageSize}`;
  const res = await fetch(url, {
    cache: "no-store",
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
  });
  if (!res.ok) {
    throw new Error(`期刊榜加载失败：${res.status}`);
  }
  const data = (await res.json()) as PageResult<VenueBrowse>;
  if (!Array.isArray(data?.items)) {
    console.warn("[fetchVenues] 响应缺少 items 字段，降级返回空列表", data);
    return [];
  }
  return data.items;
}

/** BE `/portal/venues/facets` 原始响应形状（键名与 FE VenueBrowseFacets 不同） */
interface VenueFacetsApiResponse {
  subjects: VenueBrowseFacets["subject"];
  jcrQuartiles: VenueBrowseFacets["jcr"];
  casQuartiles: VenueBrowseFacets["cas"];
  countries: VenueBrowseFacets["country"];
  casTop: number;
  openAccess: number;
  doaj: number;
}

/**
 * 在服务端拉取期刊分页列表（期刊浏览检索用）。仅 RSC / Route Handler 调用（server-only）。
 *
 * 端点：`GET /patra-catalog/portal/venues?<buildVenuesPageApiQuery>`
 * 响应：`PageResult<VenueBrowse>`（即 `VenueBrowsePage`）。
 * 列表恒 200（空集也是 200），非 2xx 直接 throw 冒泡到 error.tsx。
 */
export async function fetchVenuesPage(query: VenueBrowseQuery): Promise<VenueBrowsePage> {
  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
  if (!baseUrl) {
    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
  }
  const url = `${baseUrl}/patra-catalog/portal/venues?${buildVenuesPageApiQuery(query)}`;
  const res = await fetch(url, {
    cache: "no-store",
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
  });
  if (!res.ok) {
    throw new Error(`期刊列表加载失败：${res.status}`);
  }
  const data = (await res.json()) as VenueBrowsePage;
  // 最小运行时校验：缺关键字段时降级返回空页，避免 JournalGrid/Pagination 在 .map/.length 抛错
  if (!Array.isArray(data?.items) || typeof data?.total !== "number") {
    console.warn("[fetchVenuesPage] 响应格式异常，降级返回空页", data);
    return { page: query.page, pageSize: DEFAULT_PAGE_SIZE, total: 0, totalPages: 0, items: [] };
  }
  return data;
}

/**
 * 在服务端拉取期刊 facet 统计（筛选面板用）。仅 RSC / Route Handler 调用（server-only）。
 *
 * 端点：`GET /patra-catalog/portal/venues/facets?<buildVenuesFacetsApiQuery>`
 * 键名归一：BE `subjects/jcrQuartiles/casQuartiles/countries/openAccess` → FE `subject/jcr/cas/country/oa`。
 * 非 2xx 直接 throw 冒泡到 error.tsx。
 */
export async function fetchVenuesFacets(filters: VenueBrowseFilters): Promise<VenueBrowseFacets> {
  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
  if (!baseUrl) {
    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
  }
  const url = `${baseUrl}/patra-catalog/portal/venues/facets?${buildVenuesFacetsApiQuery(filters)}`;
  const res = await fetch(url, {
    cache: "no-store",
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
  });
  if (!res.ok) {
    throw new Error(`期刊 facet 加载失败：${res.status}`);
  }
  const data = (await res.json()) as Partial<VenueFacetsApiResponse>;
  // 最小运行时校验：数组字段缺失/类型不符时降级为空，避免筛选面板 .map/.filter 抛错
  const safeOpts = (arr: unknown): VenueBrowseFacets["subject"] =>
    Array.isArray(arr) ? (arr as VenueBrowseFacets["subject"]) : [];
  const safeNum = (n: unknown): number => (typeof n === "number" ? n : 0);
  return {
    subject: safeOpts(data?.subjects),
    jcr: safeOpts(data?.jcrQuartiles),
    cas: safeOpts(data?.casQuartiles),
    country: safeOpts(data?.countries),
    casTop: safeNum(data?.casTop),
    oa: safeNum(data?.openAccess),
    doaj: safeNum(data?.doaj),
  };
}

/**
 * 在服务端拉取单个期刊详情（按数字 id）。仅 RSC 调用（server-only）。
 *
 * 端点：`GET /patra-catalog/portal/venues/{id}`，响应 `VenueDetail`（扁平 + 评级列表）。
 * 返回约定：
 * - 非数字 id（坏 URL）→ 直接 `null`，不触达 BE
 * - BE 404（刊不存在）→ `null`，由调用方转 `notFound()`
 * - 其他失败（5xx / 超时 / 网络）→ throw，冒泡到全局 error boundary
 */
export async function fetchVenueDetail(id: string): Promise<VenueDetail | null> {
  if (!/^\d+$/.test(id)) {
    return null;
  }
  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
  if (!baseUrl) {
    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
  }
  const url = `${baseUrl}/patra-catalog/portal/venues/${id}`;
  const res = await fetch(url, {
    cache: "no-store",
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
  });
  if (res.status === 404) {
    return null;
  }
  if (!res.ok) {
    throw new Error(`期刊详情加载失败：${res.status}`);
  }
  return (await res.json()) as VenueDetail;
}

/**
 * 期刊候选（文献页期刊 facet 的"搜索 → 选候选"）。仅 Route Handler 调用（server-only）。
 * 端点：`GET /portal/venues?q=&pageSize=8`，按影响因子默认排序；非 2xx throw。
 */
export async function fetchVenueSuggestions(q: string): Promise<VenueSuggestion[]> {
  const baseUrl = process.env.PATRA_GATEWAY_BASE_URL;
  if (!baseUrl) {
    throw new Error("PATRA_GATEWAY_BASE_URL 未配置");
  }
  const params = new URLSearchParams({ q, pageSize: "8" });
  const res = await fetch(`${baseUrl}/patra-catalog/portal/venues?${params}`, {
    cache: "no-store",
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
  });
  if (!res.ok) {
    throw new Error(`期刊候选加载失败：${res.status}`);
  }
  const data = (await res.json()) as Partial<PageResult<VenueBrowse>>;
  return (Array.isArray(data?.items) ? data.items : []).map((v) => ({
    id: v.id,
    name: v.name,
    abbr: v.abbr,
  }));
}

/** 单个刊名（chip 与已选期刊行用）：查不到或失败返回 null，不让一个刊名拖垮整页。React cache 单次渲染去重。 */
const fetchVenueTitle = cache(async (id: string): Promise<string | null> => {
  try {
    return (await fetchVenueDetail(id))?.title ?? null;
  } catch {
    return null;
  }
});

/** 批量取刊名 → `{id: 刊名}`；查不到的 id 不出现在结果中（调用方回退为"期刊 #id"）。 */
export async function fetchVenueTitles(ids: readonly string[]): Promise<Record<string, string>> {
  const entries = await Promise.all(
    ids.map(async (id) => [id, await fetchVenueTitle(id)] as const),
  );
  return Object.fromEntries(
    entries.filter((entry): entry is readonly [string, string] => entry[1] !== null),
  );
}
