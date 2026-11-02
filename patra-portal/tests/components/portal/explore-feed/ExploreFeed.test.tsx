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
}));

vi.mock("@/lib/interaction/stats", () => ({ fetchPaperStats: vi.fn(async () => new Map()) }));
vi.mock("@/lib/analytics/client/queue", () => ({ track: vi.fn() }));

import { track } from "@/lib/analytics/client/queue";
import { fetchPaperStats } from "@/lib/interaction/stats";
import { fetchFeed } from "@/lib/portal-api/publications";

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
    expect(screen.getByText(/最近入库与高被引的文献/)).not.toHaveTextContent(/采集时生成/);
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
