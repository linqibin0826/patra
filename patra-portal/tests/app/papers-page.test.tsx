import { makePaper } from "@tests/fixtures/paper";
import { isValidElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EMPTY_PAPER_QUERY } from "@/lib/portal-api/paper-search";
import type { PageResult, Paper } from "@/types/portal";

vi.mock("next/navigation", () => ({
  redirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/papers",
}));
vi.mock("@/lib/portal-api/publication-search", () => ({
  fetchPublicationSearch: vi.fn(),
  fetchPublicationFacets: vi.fn(),
}));

import PapersPage from "@/app/papers/page";
import { fetchPublicationSearch } from "@/lib/portal-api/publication-search";

function pageOf(items: Paper[], total = items.length): PageResult<Paper> {
  return { page: 1, pageSize: 20, total, totalPages: 1, items };
}

const call = (sp: Record<string, string>) => PapersPage({ searchParams: Promise.resolve(sp) });

describe("/papers page", () => {
  beforeEach(() => vi.mocked(fetchPublicationSearch).mockReset());

  it("PMID 命中 1 篇 → 服务端跳转详情（不渲染列表页）", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([makePaper()]));
    await expect(call({ pmid: "41605285", page: "2" })).rejects.toThrow(
      "NEXT_REDIRECT:/papers/352303128713027974",
    );
    // 精确定位归一为第 1 页，避免越界页码拿到空 items
    expect(fetchPublicationSearch).toHaveBeenCalledWith({ ...EMPTY_PAPER_QUERY, pmid: "41605285" });
  });

  it("DOI 未命中 → 不跳转，正常返回页面", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([]));
    const element = await call({ doi: "10.1/none" });
    expect(isValidElement(element)).toBe(true);
    expect(fetchPublicationSearch).toHaveBeenCalledTimes(1);
  });

  it("total=1 但 items 为空 → 不读空数组、不跳转", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([], 1));
    await expect(call({ pmid: "41605285" })).resolves.toBeTruthy();
  });

  it("普通检索：page 顶层不取数（交给 Suspense 内的结果区流式渲染）", async () => {
    await call({ q: "GLP-1" });
    expect(fetchPublicationSearch).not.toHaveBeenCalled();
  });
});
