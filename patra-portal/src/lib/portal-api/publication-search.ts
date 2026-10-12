import "server-only";
import { cache } from "react";
import { gatewayFetch } from "@/lib/gateway";
import {
  buildFacetsApiQuery,
  buildSearchApiQuery,
  PAPER_PAGE_SIZE,
} from "@/lib/portal-api/paper-search";
import type {
  FacetOption,
  PageResult,
  Paper,
  PaperSearchQuery,
  PublicationFacets,
} from "@/types/portal";

/**
 * 服务端检索文献（`GET /portal/publications/search`）。仅 RSC 调用（server-only）。
 * 非 2xx 直接 throw 冒泡到全局 error.tsx；响应缺关键字段时降级为空页。
 */
export async function fetchPublicationSearch(query: PaperSearchQuery): Promise<PageResult<Paper>> {
  const res = await gatewayFetch(
    `/patra-catalog/portal/publications/search?${buildSearchApiQuery(query)}`,
  );
  if (!res.ok) {
    throw new Error(`文献检索失败：${res.status}`);
  }
  const data = (await res.json()) as PageResult<Paper>;
  if (!Array.isArray(data?.items) || typeof data?.total !== "number") {
    console.warn("[fetchPublicationSearch] 响应格式异常，降级返回空页", data);
    return { page: query.page, pageSize: PAPER_PAGE_SIZE, total: 0, totalPages: 0, items: [] };
  }
  return data;
}

const safeOptions = (value: unknown): FacetOption[] =>
  Array.isArray(value) ? (value as FacetOption[]) : [];
const safeNumber = (value: unknown): number => (typeof value === "number" ? value : 0);

/** 以 facets querystring 为缓存键：同一次渲染里信息行与筛选栏共用一次请求。 */
const facetsByQueryString = cache(async (qs: string): Promise<PublicationFacets> => {
  const res = await gatewayFetch(
    `/patra-catalog/portal/publications/search/facets${qs ? `?${qs}` : ""}`,
  );
  if (!res.ok) {
    throw new Error(`文献 facet 加载失败：${res.status}`);
  }
  const data = (await res.json()) as Partial<PublicationFacets>;
  return {
    years: safeOptions(data?.years),
    types: safeOptions(data?.types),
    evidence: safeOptions(data?.evidence),
    languages: safeOptions(data?.languages),
    openAccess: safeNumber(data?.openAccess),
    total: safeNumber(data?.total),
    lastSyncedAt: typeof data?.lastSyncedAt === "string" ? data.lastSyncedAt : null,
  };
});

/**
 * 服务端取 facets（`GET /portal/publications/search/facets`），计数随当前条件收窄。
 * React cache 在单次 RSC 渲染内去重；非 2xx 直接 throw。
 */
export function fetchPublicationFacets(query: PaperSearchQuery): Promise<PublicationFacets> {
  return facetsByQueryString(buildFacetsApiQuery(query));
}
