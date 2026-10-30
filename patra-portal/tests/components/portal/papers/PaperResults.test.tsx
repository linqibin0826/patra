import { screen } from "@testing-library/react";
import { renderWithQuery } from "@tests/support/render-with-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { makePaper } from "@tests/fixtures/paper";
import { PaperResults } from "@/components/portal/papers/PaperResults";
import { PapersQueryProvider } from "@/components/portal/papers/PapersQueryProvider";
import { EMPTY_PAPER_QUERY } from "@/lib/portal-api/paper-search";
import type { PageResult, Paper, PaperSearchQuery } from "@/types/portal";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));
vi.mock("@/lib/portal-api/publication-search", () => ({
  fetchPublicationSearch: vi.fn(),
  fetchPublicationFacets: vi.fn(),
}));

vi.mock("@/lib/interaction/stats", () => ({ fetchPaperStats: vi.fn() }));

import { fetchPaperStats } from "@/lib/interaction/stats";
import { fetchPublicationSearch } from "@/lib/portal-api/publication-search";

function pageOf(items: Paper[], total = items.length): PageResult<Paper> {
  return { page: 1, pageSize: 20, total, totalPages: Math.ceil(total / 20), items };
}

async function renderResults(query: PaperSearchQuery, prefetched?: PageResult<Paper>) {
  const ui = await PaperResults({ query, prefetched });
  return renderWithQuery(<PapersQueryProvider query={query}>{ui}</PapersQueryProvider>);
}

describe("PaperResults", () => {
  beforeEach(() => {
    vi.mocked(fetchPublicationSearch).mockReset();
    vi.mocked(fetchPaperStats).mockReset().mockResolvedValue(new Map());
  });

  it("有结果：列表 + 标题高亮 + 分页链接保留条件", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([makePaper()], 45));
    const query = { ...EMPTY_PAPER_QUERY, q: "osbpl6" };
    const { container } = await renderResults(query);
    expect(screen.getByRole("list", { name: "文献列表" })).toBeInTheDocument();
    expect(container.querySelector("mark")?.textContent).toBe("OSBPL6");
    expect(screen.getByRole("link", { name: "2" })).toHaveAttribute(
      "href",
      "/papers?q=osbpl6&page=2",
    );
    expect(screen.getByText("第 1–20 篇 · 共 45 篇")).toBeInTheDocument();
  });

  it("prefetched 存在时不重复请求", async () => {
    await renderResults({ ...EMPTY_PAPER_QUERY, pmid: "1" }, pageOf([]));
    expect(fetchPublicationSearch).not.toHaveBeenCalled();
    expect(screen.getByRole("heading")).toHaveTextContent("未找到 PMID 1 对应的文献");
  });

  it("浏览态 0 条 → 库中暂无文献", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([]));
    await renderResults(EMPTY_PAPER_QUERY);
    expect(screen.getByRole("heading")).toHaveTextContent("库中暂无文献");
  });

  it("页码越界 → 该页没有内容", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue({ ...pageOf([], 5), page: 9 });
    await renderResults({ ...EMPTY_PAPER_QUERY, page: 9 });
    expect(screen.getByRole("heading")).toHaveTextContent("该页没有内容");
  });
});

describe("PaperResults · 公开计数", () => {
  beforeEach(() => {
    vi.mocked(fetchPublicationSearch).mockReset();
    vi.mocked(fetchPaperStats).mockReset();
  });

  it("should_fetch_stats_for_this_page_and_show_them_on_cards", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(
      pageOf([makePaper({ id: "11" }), makePaper({ id: "12" })]),
    );
    vi.mocked(fetchPaperStats).mockResolvedValue(
      new Map([["11", { collectorCount: 2, discussionCount: 5 }]]),
    );
    await renderResults({ ...EMPTY_PAPER_QUERY, q: "osbpl6" });
    expect(fetchPaperStats).toHaveBeenCalledWith(["11", "12"]);
    expect(screen.getByText("2 人收藏")).toBeInTheDocument();
    expect(screen.getAllByText(/人收藏/)).toHaveLength(1);
  });

  it("should_render_cards_without_stats_when_unavailable", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([makePaper({ id: "11" })]));
    vi.mocked(fetchPaperStats).mockResolvedValue(null);
    await renderResults({ ...EMPTY_PAPER_QUERY, q: "osbpl6" });
    expect(screen.getByRole("list", { name: "文献列表" })).toBeInTheDocument();
    expect(screen.queryByText(/人收藏|条讨论/)).not.toBeInTheDocument();
  });
});
