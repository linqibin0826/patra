import { fireEvent, screen } from "@testing-library/react";
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
// 埋点另有用例（tests/app/papers/analytics-events.test.tsx）
vi.mock("@/lib/analytics/server/context", () => ({ readPageFacts: vi.fn(async () => null) }));
vi.mock("@/lib/analytics/client/queue", () => ({ track: vi.fn() }));
vi.mock("@/lib/analytics/insight", () => ({ fetchArticleStats: vi.fn(async () => new Map()) }));

import { track } from "@/lib/analytics/client/queue";
import { fetchArticleStats } from "@/lib/analytics/insight";
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

describe("PaperResults · 埋点", () => {
  it("点结果：list_type 为 search，位置跨页连续（第 2 页第 1 篇是 21）", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue({ ...pageOf([makePaper()], 45), page: 2 });
    vi.mocked(fetchPaperStats).mockResolvedValue(new Map());
    await renderResults({ ...EMPTY_PAPER_QUERY, q: "osbpl6", page: 2 });
    fireEvent.click(screen.getByRole("link", { name: /demyelination/ }));
    expect(track).toHaveBeenCalledWith(
      "article_clicked",
      expect.objectContaining({
        article_id: "352303128713027974",
        list_type: "search",
        position: 21,
        list_id: expect.stringMatching(/^[0-9a-f]{8}-[0-9a-f]{4}-7/),
      }),
    );
  });
});

describe("PaperResults · 看过（v0.18）", () => {
  it("一屏批量取看过人数，写在收藏之前", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([makePaper()], 1));
    vi.mocked(fetchPaperStats).mockResolvedValue(
      new Map([["352303128713027974", { collectorCount: 23, discussionCount: 0 }]]),
    );
    vi.mocked(fetchArticleStats).mockResolvedValue(
      new Map([["352303128713027974", { viewerCount: 1284, fulltextReaderCount: 9, hot: false }]]),
    );
    await renderResults({ ...EMPTY_PAPER_QUERY, q: "osbpl6" });
    expect(fetchArticleStats).toHaveBeenCalledWith(["352303128713027974"]);
    expect(screen.getByText("1,284 人看过")).toBeInTheDocument();
    expect(screen.queryByText(/读过全文/)).toBeNull();
  });

  it("行为数据服务取不到：只少看过", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([makePaper()], 1));
    vi.mocked(fetchPaperStats).mockResolvedValue(
      new Map([["352303128713027974", { collectorCount: 23, discussionCount: 0 }]]),
    );
    vi.mocked(fetchArticleStats).mockResolvedValue(null);
    await renderResults({ ...EMPTY_PAPER_QUERY, q: "osbpl6" });
    expect(screen.getByText("23 人收藏")).toBeInTheDocument();
    expect(screen.queryByText(/人看过/)).toBeNull();
  });
});

describe("PaperResults · 近期热读（v0.18）", () => {
  const second = makePaper({ id: "22", title: "Second paper", kind: "Review" });

  it("search.hot：在热读榜上的带标记，不在的不带；排序不变", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([makePaper(), second], 2));
    vi.mocked(fetchPaperStats).mockResolvedValue(new Map());
    vi.mocked(fetchArticleStats).mockResolvedValue(
      new Map([
        ["352303128713027974", { viewerCount: 5, fulltextReaderCount: 1, hot: true }],
        ["22", { viewerCount: 1, fulltextReaderCount: 0, hot: false }],
      ]),
    );
    await renderResults({ ...EMPTY_PAPER_QUERY, q: "x" });
    const [first, other] = screen.getAllByRole("article");
    expect(first).toHaveTextContent("近期热读");
    expect(other).not.toHaveTextContent("近期热读");
    expect(first).toHaveTextContent(/demyelination/);
  });

  it("search.hot.down：取不到时一个标记都没有", async () => {
    vi.mocked(fetchPublicationSearch).mockResolvedValue(pageOf([makePaper()], 1));
    vi.mocked(fetchPaperStats).mockResolvedValue(new Map());
    vi.mocked(fetchArticleStats).mockResolvedValue(null);
    await renderResults({ ...EMPTY_PAPER_QUERY, q: "x" });
    expect(screen.queryByText("近期热读")).toBeNull();
  });
});
