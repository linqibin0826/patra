import { screen } from "@testing-library/react";
import { renderWithQuery } from "@tests/support/render-with-query";
import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("@/lib/interaction/stats", () => ({ fetchPaperStats: vi.fn() }));
vi.mock("@/lib/analytics/insight", () => ({ fetchArticleStats: vi.fn() }));

import {
  AnalyticsReaderCounts,
  InteractionReaderCounts,
  PaperReaders,
} from "@/components/portal/paper-detail/PaperReaders";
import { fetchArticleStats } from "@/lib/analytics/insight";
import { fetchPaperStats } from "@/lib/interaction/stats";

const items = () =>
  [...document.querySelectorAll("[data-reader-count]")].map((item) => item.textContent);

async function renderAnalytics() {
  renderWithQuery(<div>{await AnalyticsReaderCounts({ paperId: "11" })}</div>);
}
async function renderInteraction() {
  renderWithQuery(<div>{await InteractionReaderCounts({ paperId: "11" })}</div>);
}

beforeEach(() => {
  vi.mocked(fetchArticleStats).mockReset();
  vi.mocked(fetchPaperStats).mockReset();
});

describe("文献页读者一行（简报 v0.18 §3.3）", () => {
  it("paper.readers：看过、读过全文在前，带悬停说明", async () => {
    vi.mocked(fetchArticleStats).mockResolvedValue(
      new Map([["11", { viewerCount: 1284, fulltextReaderCount: 312, hot: false }]]),
    );
    await renderAnalytics();
    expect(items()).toEqual(["1,284 人看过", "312 人读过全文"]);
    expect(screen.getByText("1,284").closest("[data-reader-count]")).toHaveAttribute(
      "title",
      "看过的人数：同一个人只算一次（登录用户按账号、访客按设备），不含搜索引擎等爬虫；1 分钟内更新",
    );
    expect(screen.getByText("312").closest("[data-reader-count]")).toHaveAttribute(
      "title",
      "在全文区停留满 10 秒或下载了 PDF 的人数，同一个人只算一次",
    );
  });

  it("paper.readers.fresh / no-fulltext：为 0 的不写", async () => {
    vi.mocked(fetchArticleStats).mockResolvedValue(
      new Map([["11", { viewerCount: 37, fulltextReaderCount: 0, hot: false }]]),
    );
    await renderAnalytics();
    expect(items()).toEqual(["37 人看过"]);
  });

  it("paper.readers.down：行为数据服务取不到时这两项都不出现", async () => {
    vi.mocked(fetchArticleStats).mockResolvedValue(null);
    await renderAnalytics();
    expect(items()).toEqual([]);
  });

  it("收藏与讨论来自互动服务，写法同卡片；取不到时这两项都不出现", async () => {
    vi.mocked(fetchPaperStats).mockResolvedValue(
      new Map([["11", { collectorCount: 23, discussionCount: 7 }]]),
    );
    await renderInteraction();
    expect(items()).toEqual(["23 人收藏", "7 条讨论"]);
    expect(screen.getByRole("link", { name: "7 条讨论" })).toHaveAttribute("href", "#discussion");
  });

  it("互动服务取不到：不出现", async () => {
    vi.mocked(fetchPaperStats).mockResolvedValue(null);
    await renderInteraction();
    expect(items()).toEqual([]);
  });

  it("外壳：两组各自在 Suspense 里到达；项之间的点由样式画，四项都没有时整行收起", () => {
    const html = renderToStaticMarkup(<PaperReaders paperId="11" />);
    expect(html).toContain("empty:hidden");
    // 分隔符只加在真正的项之间：流式时未完成的 boundary 留着 <template> 元素，用 *+* 会在第一项前多画一个点
    expect(html).toContain("[&amp;&gt;[data-reader-count]~[data-reader-count]]:before:content-[");
    expect(html).not.toMatch(/\[&amp;&gt;\*\+\*\]/);
  });
});
