import { screen } from "@testing-library/react";
import { renderWithQuery } from "@tests/support/render-with-query";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PaperCard } from "@/components/portal/PaperCard";
import type { Paper } from "@/types/portal";

function makePaper(overrides: Partial<Paper> = {}): Paper {
  return {
    id: "p1",
    title: "Semaglutide 长期心血管转归",
    journal: "N Engl J Med",
    year: 2026,
    publishedDate: "2026",
    authors: ["Perkovic V.", "Tuttle K. R.", "Mann J. F. E."],
    cites: 142,
    doi: "10.1056/NEJMoa2603120",
    pmid: "39812044",
    source: "PubMed",
    aiSummary: "心血管事件下降 22%",
    estimatedReadMin: 12,
    kind: "Journal Article",
    minutesAgo: 120,
    venueId: null,
    evidenceLevel: { level: "UNKNOWN", rank: 0, label: "未分级", derived: false },
    abstractSnippet: null,
    ...overrides,
  };
}

describe("PaperCard", () => {
  it("卡片只显示出版年份，不写月日", () => {
    renderWithQuery(<PaperCard paper={makePaper({ year: 2019, publishedDate: "2019-03-15" })} />);
    expect(screen.getByText("2019")).toBeInTheDocument();
    expect(screen.queryByText(/3 月/)).not.toBeInTheDocument();
  });

  it("渲染 title / journal / source", () => {
    renderWithQuery(<PaperCard paper={makePaper()} />);
    expect(screen.getByText("Semaglutide 长期心血管转归")).toBeInTheDocument();
    expect(screen.getByText(/N Engl J Med/)).toBeInTheDocument();
    expect(screen.getByText("PubMed")).toBeInTheDocument();
  });

  it("作者展示 visible + remaining 数（基于 authors.length）", () => {
    renderWithQuery(<PaperCard paper={makePaper()} />);
    expect(screen.getByText(/等\s*1\s*位作者/)).toBeInTheDocument();
  });

  it("aiSummary 为 null 时不渲染 AI 速读", () => {
    renderWithQuery(<PaperCard paper={makePaper({ aiSummary: null })} />);
    expect(screen.queryByText("AI 速读")).not.toBeInTheDocument();
  });

  it("kind 为 null 时不渲染类型标签", () => {
    renderWithQuery(<PaperCard paper={makePaper({ kind: null })} />);
    expect(screen.queryByText("Journal Article")).not.toBeInTheDocument();
  });

  it("未知 source 仍渲染 source 文本（兜底色）", () => {
    renderWithQuery(<PaperCard paper={makePaper({ source: "OADOI" })} />);
    expect(screen.getByText("OADOI")).toBeInTheDocument();
  });

  it("journal 与 year 均缺失但有作者时，不渲染悬空的 · 分隔符", () => {
    renderWithQuery(
      <PaperCard paper={makePaper({ journal: null, year: null, publishedDate: null })} />,
    );
    expect(screen.getByText(/Perkovic V\./)).toBeInTheDocument();
    expect(screen.queryByText("·")).not.toBeInTheDocument();
  });

  it("不再渲染禁用的「评论」「分享」占位按钮", () => {
    renderWithQuery(<PaperCard paper={makePaper()} />);
    expect(screen.queryByRole("button", { name: /评论/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /分享/ })).not.toBeInTheDocument();
  });

  it("底栏有收藏按钮，读屏名带标题", () => {
    renderWithQuery(<PaperCard paper={makePaper({ id: "card-bookmark" })} />);
    expect(
      screen.getByRole("button", { name: "收藏「Semaglutide 长期心血管转归」" }),
    ).toHaveAttribute("aria-pressed", "false");
  });

  it("lead 变体展示摘要片段", () => {
    renderWithQuery(
      <PaperCard paper={makePaper({ abstractSnippet: "BACKGROUND: lead text" })} variant="lead" />,
    );
    expect(screen.getByText(/BACKGROUND: lead text/)).toBeInTheDocument();
  });

  it("lead 摘要片段停在半句时补省略号（后端按码点截断、不带省略号）", () => {
    renderWithQuery(
      <PaperCard paper={makePaper({ abstractSnippet: "Here, we demonst" })} variant="lead" />,
    );
    expect(screen.getByText("Here, we demonst…")).toBeInTheDocument();
  });

  it("index 以两位序号显示", () => {
    renderWithQuery(<PaperCard paper={makePaper()} index={3} />);
    expect(screen.getByText("03")).toBeInTheDocument();
  });

  it("详情链接指向 /papers/[id]", () => {
    renderWithQuery(<PaperCard paper={makePaper({ id: "319041872872550658" })} />);
    expect(screen.getByRole("link", { name: /详情/ })).toHaveAttribute(
      "href",
      "/papers/319041872872550658",
    );
  });
});

describe("PaperCard · 公开计数", () => {
  const follows = (a: Node, b: Node) =>
    Boolean(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING);

  it("should_show_stats_between_read_more_and_citations", () => {
    renderWithQuery(
      <PaperCard paper={makePaper()} stats={{ collectorCount: 2, discussionCount: 5 }} />,
    );
    const collectors = screen.getByText("2 人收藏");
    expect(follows(screen.getByRole("link", { name: /阅读详情/ }), collectors)).toBe(true);
    expect(follows(collectors, screen.getByText(/142 引用/))).toBe(true);
    expect(screen.getByRole("link", { name: "5 条讨论" })).toHaveAttribute(
      "href",
      "/papers/p1#discussion",
    );
  });

  it("should_not_show_stats_when_unavailable", () => {
    renderWithQuery(<PaperCard paper={makePaper()} stats={null} />);
    expect(screen.queryByText(/人收藏|条讨论/)).not.toBeInTheDocument();
    expect(screen.getByText(/142 引用/)).toBeInTheDocument();
  });
});
