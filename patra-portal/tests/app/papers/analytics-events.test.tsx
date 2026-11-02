import { FACTS } from "@tests/fixtures/analytics";
import { makePaper, makePaperDetail } from "@tests/fixtures/paper";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EMPTY_PAPER_QUERY } from "@/lib/portal-api/paper-search";
import type { PageResult, Paper } from "@/types/portal";

vi.mock("next/navigation", () => ({
  notFound: vi.fn(() => {
    throw new Error("NEXT_NOT_FOUND");
  }),
  redirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/papers",
}));
vi.mock("@/lib/analytics/server/context", () => ({ readPageFacts: vi.fn() }));
vi.mock("@/lib/analytics/server/send", () => ({ trackServerEvent: vi.fn() }));
vi.mock("@/lib/portal-api/publications", () => ({ fetchPublicationDetail: vi.fn() }));
vi.mock("@/lib/public-url", () => ({ publicUrl: vi.fn(async (path: string) => path) }));
vi.mock("@/lib/portal-api/publication-search", () => ({
  fetchPublicationSearch: vi.fn(),
  fetchPublicationFacets: vi.fn(),
}));
vi.mock("@/lib/interaction/stats", () => ({ fetchPaperStats: vi.fn(async () => new Map()) }));

import PapersPage from "@/app/papers/(list)/page";
import PaperDetailPage from "@/app/papers/[id]/page";
import { PaperResults } from "@/components/portal/papers/PaperResults";
import { readPageFacts } from "@/lib/analytics/server/context";
import { trackServerEvent } from "@/lib/analytics/server/send";
import { fetchPublicationSearch } from "@/lib/portal-api/publication-search";
import { fetchPublicationDetail } from "@/lib/portal-api/publications";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

function pageOf(items: Paper[], total = items.length): PageResult<Paper> {
  return { page: 1, pageSize: 20, total, totalPages: 1, items };
}

beforeEach(() => {
  vi.mocked(readPageFacts).mockReset().mockResolvedValue(FACTS);
  vi.mocked(trackServerEvent).mockReset();
  vi.mocked(fetchPublicationSearch).mockReset();
  vi.mocked(fetchPublicationDetail).mockReset();
});

describe("Article Viewed：文献详情页", () => {
  const open = (id: string) =>
    PaperDetailPage({ params: Promise.resolve({ id }), searchParams: Promise.resolve({}) });

  it("文献存在：发一条 article_viewed", async () => {
    vi.mocked(fetchPublicationDetail).mockResolvedValue(makePaperDetail({ id: "42" }));
    await open("42");
    expect(trackServerEvent).toHaveBeenCalledWith(
      { name: "article_viewed", properties: { article_id: "42" } },
      FACTS,
      { path: "/papers/42", type: "article" },
    );
  });

  it("文献不存在：不发", async () => {
    vi.mocked(fetchPublicationDetail).mockResolvedValue(null);
    await expect(open("42")).rejects.toThrow("NEXT_NOT_FOUND");
    expect(trackServerEvent).not.toHaveBeenCalled();
  });
});

describe("Search Performed：检索结果", () => {
  it("有结果：发一条，result_count 是总篇数", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([makePaper()], 45));
    await PaperResults({ query: { ...EMPTY_PAPER_QUERY, q: "sepsis" } });
    expect(trackServerEvent).toHaveBeenCalledTimes(1);
    const [event, facts, page] = vi.mocked(trackServerEvent).mock.calls[0] ?? [];
    expect(event).toMatchObject({
      name: "search_performed",
      properties: { mode: "keyword", query: "sepsis", result_count: 45 },
    });
    expect(event?.name === "search_performed" && event.properties.search_id).toMatch(UUID);
    expect(facts).toBe(FACTS);
    expect(page).toEqual({ path: "/papers", type: "search" });
  });

  it("没有结果：result_count 为 0", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([]));
    await PaperResults({ query: { ...EMPTY_PAPER_QUERY, q: "nothing" } });
    expect(vi.mocked(trackServerEvent).mock.calls[0]?.[0]).toMatchObject({
      properties: { result_count: 0 },
    });
  });

  it("无任何条件的浏览态不发", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([makePaper()]));
    await PaperResults({ query: EMPTY_PAPER_QUERY });
    expect(trackServerEvent).not.toHaveBeenCalled();
  });

  it("精确定位命中 1 篇直跳详情：跳转前发一条，result_count 为 1", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([makePaper()]));
    await expect(
      PapersPage({ searchParams: Promise.resolve({ pmid: "41605285" }) }),
    ).rejects.toThrow("NEXT_REDIRECT");
    expect(trackServerEvent).toHaveBeenCalledTimes(1);
    expect(vi.mocked(trackServerEvent).mock.calls[0]?.[0]).toMatchObject({
      name: "search_performed",
      properties: { mode: "exact_pmid", query: "41605285", result_count: 1 },
    });
  });
});
