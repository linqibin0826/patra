import { screen, within } from "@testing-library/react";
import { renderWithQuery } from "@tests/support/render-with-query";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PublicationDetailView } from "@/components/portal/paper-detail/PublicationDetailView";
import type { PaperDetail } from "@/types/portal";

function paper(overrides: Partial<PaperDetail> = {}): PaperDetail {
  return {
    id: "319041872872550658",
    title: "Semaglutide RCT",
    originalTitle: null,
    venueId: "42",
    venueName: "N Engl J Med",
    publicationYear: 2024,
    publishedDate: null,
    evidenceLevel: {
      level: "RANDOMIZED_CONTROLLED_TRIAL",
      rank: 4,
      label: "随机对照试验",
      derived: true,
    },
    abstractType: "STRUCTURED",
    abstractSections: [{ label: "BACKGROUND", text: "bg" }],
    abstractPlainText: null,
    doi: "10.1/x",
    pmid: "9",
    pmcid: null,
    pii: null,
    primaryType: "Journal Article",
    publicationTypes: ["Journal Article"],
    citationCount: 142,
    numberOfReferences: 30,
    conflictOfInterest: null,
    isOa: true,
    oaStatus: null,
    authors: [],
    meshHeadings: [],
    keywords: [],
    funding: [],
    dates: [],
    aiSummary: null,
    source: "PubMed",
    fullTextUrl: "https://full",
    estimatedReadMin: null,
    retracted: false,
    hasFullText: false,
    hasPdf: false,
    pdfSizeBytes: null,
    ...overrides,
  };
}

describe("PublicationDetailView", () => {
  it("AI 速读尚未接入：按未上线功能的统一写法挂「即将上线」，不对访客露出 mock 字样", () => {
    renderWithQuery(<PublicationDetailView shareLink="http://mini/papers/1" paper={paper()} />);
    expect(screen.getByText("即将上线")).toBeInTheDocument();
    expect(screen.queryByText(/mock/i)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /生成速读|重新生成/ })).toHaveAttribute(
      "title",
      "功能即将上线",
    );
  });

  it("渲染面包屑（pmid）+ 标题 + 摘要节；正文前不再有「关键标识」「深度数据」（PAP-89）", () => {
    renderWithQuery(<PublicationDetailView shareLink="http://mini/papers/1" paper={paper()} />);
    const nav = screen.getByRole("navigation", { name: "面包屑" });
    expect(nav).toHaveTextContent("9");
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Semaglutide RCT");
    expect(screen.getByRole("heading", { name: /摘要 · Abstract/i })).toBeInTheDocument();
    expect(screen.getByText("bg")).toBeInTheDocument();
    expect(screen.queryByText("关键标识")).toBeNull();
    expect(screen.queryByText(/深度数据/)).toBeNull();
  });

  it("整页只有一组全文操作；main 是标签栏的容器", () => {
    const { container } = renderWithQuery(
      <PublicationDetailView
        shareLink="http://mini/papers/1"
        paper={paper({ hasFullText: true, pmcid: "PMC1" })}
      />,
    );
    expect(screen.getAllByRole("link", { name: /阅读全文/ })).toHaveLength(1);
    expect(container.querySelector('[class~="@container/main"]')).not.toBeNull();
  });
});

describe("PublicationDetailView · 全文", () => {
  it("事实条插槽在全文区块之前；面包屑右端放目录按钮", () => {
    const { container } = renderWithQuery(
      <PublicationDetailView
        shareLink="http://mini/papers/1"
        paper={paper({ hasFullText: true, pmcid: "PMC1" })}
        fullTextFacts={<div>FACTS-SLOT</div>}
        fullText={<p>FULLTEXT-SLOT</p>}
        tocCard={<p>TOC-CARD</p>}
        tocButton={<button type="button">目录</button>}
      />,
    );
    const html = container.innerHTML;
    expect(html.indexOf("FACTS-SLOT")).toBeLessThan(html.indexOf("FULLTEXT-SLOT"));
    expect(
      within(screen.getByRole("navigation", { name: "面包屑" })).getByRole("button", {
        name: "目录",
      }),
    ).toBeInTheDocument();
  });

  it("有全文：侧栏不整体吸顶，目录卡单独吸顶；标识卡里有 PMCID", () => {
    renderWithQuery(
      <PublicationDetailView
        shareLink="http://mini/papers/1"
        paper={paper({ hasFullText: true, pmcid: "PMC13583816" })}
        fullText={null}
        tocCard={<p>TOC-CARD</p>}
        tocButton={null}
      />,
    );
    const aside = screen.getByRole("complementary");
    expect(aside.className).not.toMatch(/(^|\s)sticky(\s|$)/);
    expect(screen.getByText("TOC-CARD").parentElement?.className).toMatch(/(^|\s)sticky(\s|$)/);
    const ids = screen.getByRole("region", { name: "标识" });
    expect(within(ids).getByText("PMC13583816")).toBeInTheDocument();
  });

  it("没有全文：侧栏照旧整体吸顶，不放目录卡", () => {
    renderWithQuery(
      <PublicationDetailView
        shareLink="http://mini/papers/1"
        paper={paper()}
        fullText={null}
        tocCard={<p>TOC-CARD</p>}
        tocButton={null}
      />,
    );
    expect(screen.getByRole("complementary").className).toMatch(/(^|\s)sticky(\s|$)/);
    expect(screen.queryByText("TOC-CARD")).toBeNull();
  });

  it("已撤稿（即便标着有全文）：侧栏整体吸顶，不放目录卡", () => {
    renderWithQuery(
      <PublicationDetailView
        shareLink="http://mini/papers/1"
        paper={paper({ hasFullText: true, retracted: true, pmcid: "PMC1" })}
        fullText={null}
        tocCard={<p>TOC-CARD</p>}
        tocButton={null}
      />,
    );
    expect(screen.getByRole("complementary").className).toMatch(/(^|\s)sticky(\s|$)/);
    expect(screen.queryByText("TOC-CARD")).toBeNull();
  });
});

describe("PublicationDetailView · 事实条的互动两格", () => {
  it("should_pass_interaction_facts_into_the_facts_row", () => {
    renderWithQuery(
      <PublicationDetailView
        paper={paper()}
        shareLink="http://patra.test/papers/1"
        interactionFacts={
          <div>
            <dt>收藏人数探针</dt>
            <dd>2 人</dd>
          </div>
        }
      />,
    );
    expect(screen.getByText("收藏人数探针").closest("dl")).toContainElement(
      screen.getByText("被引"),
    );
  });
});
