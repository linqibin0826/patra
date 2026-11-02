import { fireEvent, screen } from "@testing-library/react";
import { renderWithQuery } from "@tests/support/render-with-query";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { makePaper } from "@tests/fixtures/paper";
import { ExploreFeed } from "@/components/portal/explore-feed";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/lib/portal-api/publications", () => ({
  fetchFeed: vi.fn(),
  fetchPublicationDetail: vi.fn(),
  fetchPublicationCards: vi.fn(),
}));

vi.mock("@/lib/interaction/stats", () => ({ fetchPaperStats: vi.fn(async () => new Map()) }));
vi.mock("@/lib/analytics/client/queue", () => ({ track: vi.fn() }));
vi.mock("@/lib/analytics/insight", () => ({
  fetchArticleStats: vi.fn(async () => new Map()),
  fetchHotReads: vi.fn(),
}));

import { track } from "@/lib/analytics/client/queue";
import { fetchArticleStats, fetchHotReads } from "@/lib/analytics/insight";
import { fetchPaperStats } from "@/lib/interaction/stats";
import { fetchFeed, fetchPublicationCards } from "@/lib/portal-api/publications";

describe("ExploreFeed 查看更多", () => {
  it("有文献时底部出现「查看更多文献」→ /papers", async () => {
    vi.mocked(fetchFeed).mockResolvedValue({
      page: 1,
      pageSize: 14,
      total: 1,
      totalPages: 1,
      items: [makePaper()],
    });
    renderWithQuery(await ExploreFeed({ tab: "recent" }));
    expect(screen.getByRole("link", { name: /查看更多文献/ })).toHaveAttribute("href", "/papers");
  });

  it("空流 / 取数失败时不显示", async () => {
    vi.mocked(fetchFeed).mockResolvedValue({
      page: 1,
      pageSize: 14,
      total: 0,
      totalPages: 0,
      items: [],
    });
    const { unmount } = renderWithQuery(await ExploreFeed({ tab: "recent" }));
    expect(screen.queryByRole("link", { name: /查看更多文献/ })).not.toBeInTheDocument();
    unmount();
    vi.mocked(fetchFeed).mockRejectedValue(new Error("boom"));
    renderWithQuery(await ExploreFeed({ tab: "recent" }));
    expect(screen.queryByRole("link", { name: /查看更多文献/ })).not.toBeInTheDocument();
  });
});

describe("ExploreFeed 区块说明", () => {
  it("不宣称「AI 速读在采集时生成」：速读尚未上线", async () => {
    vi.mocked(fetchFeed).mockResolvedValue({
      page: 1,
      pageSize: 14,
      total: 1,
      totalPages: 1,
      items: [makePaper()],
    });
    renderWithQuery(await ExploreFeed({ tab: "recent" }));
    expect(screen.getByText(/最近入库、高被引与近期读的人多的文献/)).not.toHaveTextContent(
      /采集时生成/,
    );
  });
});

describe("ExploreFeed · 公开计数", () => {
  const feedOf = (ids: string[]) => ({
    page: 1,
    pageSize: 14,
    total: ids.length,
    totalPages: 1,
    items: ids.map((id) => makePaper({ id })),
  });

  it("should_fetch_stats_for_the_feed_and_show_them_on_cards", async () => {
    vi.mocked(fetchFeed).mockResolvedValue(feedOf(["11", "12"]));
    vi.mocked(fetchPaperStats).mockResolvedValue(
      new Map([["12", { collectorCount: 0, discussionCount: 3 }]]),
    );
    renderWithQuery(await ExploreFeed({ tab: "recent" }));
    expect(fetchPaperStats).toHaveBeenCalledWith(["11", "12"]);
    expect(screen.getByRole("link", { name: "3 条讨论" })).toHaveAttribute(
      "href",
      "/papers/12#discussion",
    );
  });

  it("should_render_feed_without_stats_when_unavailable", async () => {
    vi.mocked(fetchFeed).mockResolvedValue(feedOf(["11"]));
    vi.mocked(fetchPaperStats).mockResolvedValue(null);
    renderWithQuery(await ExploreFeed({ tab: "recent" }));
    expect(screen.getByRole("link", { name: /查看更多文献/ })).toBeInTheDocument();
    expect(screen.queryByText(/人收藏|条讨论/)).not.toBeInTheDocument();
  });
});

describe("ExploreFeed 埋点", () => {
  it("切标签是另一个列表实例：list_id 不同", async () => {
    vi.mocked(track).mockReset();
    vi.mocked(fetchFeed).mockResolvedValue({
      page: 1,
      pageSize: 14,
      total: 1,
      totalPages: 1,
      items: [makePaper()],
    });
    const recent = await ExploreFeed({ tab: "recent" });
    const cited = await ExploreFeed({ tab: "cited" });
    /// 切标签时 Next 在同一位置换上新的服务端结果：用一个开关在同一位置换两棵树
    function Switcher() {
      const [tab, setTab] = useState<"recent" | "cited">("recent");
      return (
        <>
          <button type="button" onClick={() => setTab("cited")}>
            切到高被引
          </button>
          {tab === "recent" ? recent : cited}
        </>
      );
    }
    renderWithQuery(<Switcher />);
    fireEvent.click(screen.getAllByRole("link", { name: /demyelination/ })[0] as HTMLElement);
    fireEvent.click(screen.getByRole("button", { name: "切到高被引" }));
    fireEvent.click(screen.getAllByRole("link", { name: /demyelination/ })[0] as HTMLElement);
    const [first, second] = vi
      .mocked(track)
      .mock.calls.map(([, props]) => props as { list_id: string; list_type: string });
    expect(first?.list_type).toBe("feed_latest");
    expect(second?.list_type).toBe("feed_cited");
    expect(second?.list_id).not.toBe(first?.list_id);
  });

  it("点文献：article_clicked 的列表类型按标签、位置就是卡片序号", async () => {
    const second = makePaper({ id: "22", title: "Second paper" });
    vi.mocked(fetchFeed).mockResolvedValue({
      page: 1,
      pageSize: 14,
      total: 2,
      totalPages: 1,
      items: [makePaper(), second],
    });
    renderWithQuery(await ExploreFeed({ tab: "cited" }));
    fireEvent.click(screen.getAllByRole("link", { name: /Second paper/ })[0] as HTMLElement);
    expect(track).toHaveBeenCalledWith(
      "article_clicked",
      expect.objectContaining({ article_id: "22", list_type: "feed_cited", position: 2 }),
    );
  });
});

describe("ExploreFeed 看过（v0.18）", () => {
  it("卡片写看过人数，与收藏、讨论同一批取", async () => {
    vi.mocked(fetchFeed).mockResolvedValue({
      page: 1,
      pageSize: 14,
      total: 1,
      totalPages: 1,
      items: [makePaper()],
    });
    vi.mocked(fetchArticleStats).mockResolvedValue(
      new Map([["352303128713027974", { viewerCount: 37, fulltextReaderCount: 0, hot: false }]]),
    );
    renderWithQuery(await ExploreFeed({ tab: "recent" }));
    expect(screen.getByText("37 人看过")).toBeInTheDocument();
  });
});

describe("ExploreFeed 热读（简报 v0.18 §3.5）", () => {
  const papers = Array.from({ length: 14 }, (_, i) =>
    makePaper({ id: String(i + 1), title: `Hot paper ${i + 1}` }),
  );
  const hot = (rankedTotal: number, count = 14) => ({
    rankedTotal,
    items: papers
      .slice(0, count)
      .map((paper, i) =>
        i < rankedTotal
          ? { articleId: paper.id, source: "RANKED" as const, rank: i + 1 }
          : { articleId: paper.id, source: "LATEST" as const, rank: null },
      ),
  });
  const titles = () => screen.getAllByRole("heading", { level: 3 }).map((h) => h.textContent);

  it("feed.hot.full：序号就是名次，说明句讲算法", async () => {
    vi.mocked(fetchHotReads).mockResolvedValue(hot(14));
    vi.mocked(fetchPublicationCards).mockResolvedValue(papers);
    renderWithQuery(await ExploreFeed({ tab: "hot" }));
    expect(
      screen.getByText(
        "按近 14 天的阅读排：读了全文的分量更重，越近的阅读分量越重；每小时更新一次。",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("01")).toBeInTheDocument();
    expect(screen.getByText("14")).toBeInTheDocument();
    expect(screen.queryByText("最新收录")).toBeNull();
  });

  it("feed.hot.partial：第 5 篇起是「最新收录」，说明句写实际篇数", async () => {
    vi.mocked(fetchHotReads).mockResolvedValue(hot(4));
    vi.mocked(fetchPublicationCards).mockResolvedValue(papers);
    renderWithQuery(await ExploreFeed({ tab: "hot" }));
    expect(
      screen.getByText(
        "近 7 天有 3 位以上读者的文献只有 4 篇；后面 10 篇是最新收录，卡片上标着「最新收录」。",
      ),
    ).toBeInTheDocument();
    expect(screen.getAllByText("最新收录")).toHaveLength(10);
    expect(screen.queryByText("05")).toBeNull();
    expect(titles()[0]).toContain("Hot paper 1");
  });

  it("catalog 没回的补位不计入说明句", async () => {
    vi.mocked(fetchHotReads).mockResolvedValue(hot(4));
    vi.mocked(fetchPublicationCards).mockResolvedValue(papers.filter((p) => p.id !== "14"));
    renderWithQuery(await ExploreFeed({ tab: "hot" }));
    expect(screen.getByText(/后面 9 篇是最新收录/)).toBeInTheDocument();
  });

  it("catalog 去掉一篇上榜的：名次按实际显示的重新编号，不断号", async () => {
    vi.mocked(fetchHotReads).mockResolvedValue(hot(4));
    vi.mocked(fetchPublicationCards).mockResolvedValue(papers.filter((p) => p.id !== "2"));
    renderWithQuery(await ExploreFeed({ tab: "hot" }));
    expect(screen.queryByText("04")).toBeNull();
    expect(screen.getByText(/只有 3 篇/)).toBeInTheDocument();
  });

  it("feed.hot.none：一篇都没上榜", async () => {
    vi.mocked(fetchHotReads).mockResolvedValue(hot(0));
    vi.mocked(fetchPublicationCards).mockResolvedValue(papers);
    renderWithQuery(await ExploreFeed({ tab: "hot" }));
    expect(
      screen.getByText("近 7 天还没有文献的读者达到 3 位，下面先列出最新收录。"),
    ).toBeInTheDocument();
    expect(screen.getAllByText("最新收录")).toHaveLength(14);
  });

  it.each([
    ["行为数据服务取不到", () => vi.mocked(fetchHotReads).mockResolvedValue(null)],
    [
      "catalog 取不到",
      () => {
        vi.mocked(fetchHotReads).mockResolvedValue(hot(4));
        vi.mocked(fetchPublicationCards).mockRejectedValue(new Error("boom"));
      },
    ],
  ])("feed.error：%s → 加载失败", async (_, arrange) => {
    arrange();
    const { container } = renderWithQuery(await ExploreFeed({ tab: "hot" }));
    expect(container.querySelector('[data-feed-state="error"]')).not.toBeNull();
  });

  it("从没算过、也没有可补的：空状态", async () => {
    vi.mocked(fetchHotReads).mockResolvedValue({ rankedTotal: 0, items: [] });
    vi.mocked(fetchPublicationCards).mockResolvedValue([]);
    const { container } = renderWithQuery(await ExploreFeed({ tab: "hot" }));
    expect(container.querySelector('[data-feed-state="empty"]')).not.toBeNull();
  });

  it("其他标签没有热读说明句；区块说明句写三类", async () => {
    vi.mocked(fetchFeed).mockResolvedValue({
      page: 1,
      pageSize: 14,
      total: 1,
      totalPages: 1,
      items: [makePaper()],
    });
    renderWithQuery(await ExploreFeed({ tab: "recent" }));
    expect(screen.queryByText(/按近 14 天的阅读排/)).toBeNull();
    expect(screen.getByText(/最近入库、高被引与近期读的人多的文献/)).toBeInTheDocument();
  });

  it("热读列表的 list_type 是 feed_hot，位置是卡片在列表里的序号", async () => {
    vi.mocked(track).mockReset();
    vi.mocked(fetchHotReads).mockResolvedValue(hot(4));
    vi.mocked(fetchPublicationCards).mockResolvedValue(papers);
    renderWithQuery(await ExploreFeed({ tab: "hot" }));
    fireEvent.click(screen.getAllByRole("link", { name: /Hot paper 6$/ })[0] as HTMLElement);
    expect(track).toHaveBeenCalledWith(
      "article_clicked",
      expect.objectContaining({ article_id: "6", list_type: "feed_hot", position: 6 }),
    );
  });
});
