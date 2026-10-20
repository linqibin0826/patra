import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PaperHeader } from "@/components/portal/paper-detail/PaperHeader";
import type { PaperDetail } from "@/types/portal";

function paper(overrides: Partial<PaperDetail> = {}): PaperDetail {
  return {
    id: "1",
    title: "Semaglutide RCT",
    originalTitle: null,
    venueId: "42",
    venueName: "N Engl J Med",
    publicationYear: 2024,
    evidenceLevel: {
      level: "RANDOMIZED_CONTROLLED_TRIAL",
      rank: 4,
      label: "随机对照试验",
      derived: true,
    },
    abstractType: null,
    abstractSections: [],
    abstractPlainText: null,
    doi: "10.1/x",
    pmid: "9",
    pmcid: null,
    pii: null,
    primaryType: null,
    publicationTypes: ["Journal Article"],
    citationCount: null,
    numberOfReferences: null,
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
    bookmarks: 0,
    estimatedReadMin: null,
    hasFullText: false,
    hasPdf: false,
    pdfSizeBytes: null,
    ...overrides,
  };
}

describe("PaperHeader", () => {
  it("渲染标题 + 期刊链接（指向 /journals/[venueId]）+ 去全文链接", () => {
    render(<PaperHeader paper={paper()} />);
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Semaglutide RCT");
    expect(screen.getByRole("link", { name: "N Engl J Med" })).toHaveAttribute(
      "href",
      "/journals/42",
    );
    expect(screen.getByRole("link", { name: /去全文/ })).toHaveAttribute("href", "https://full");
  });
  it("无全文链接时按钮禁用", () => {
    render(<PaperHeader paper={paper({ fullTextUrl: null, doi: null, pmid: null })} />);
    expect(screen.getByRole("button", { name: /全文链接不可用/ })).toBeDisabled();
  });
  it("原题按白名单渲染内联标签（回归：防止改回纯文本插值）", () => {
    const { container } = render(
      <PaperHeader
        paper={paper({ originalTitle: "Studie zu <i>E. coli</i> und CO<sub>2</sub>" })}
      />,
    );
    expect(container.querySelector("i")?.textContent).toBe("E. coli");
    expect(container.querySelector("sub")).not.toBeNull();
  });
});

describe("PaperHeader · 全文按钮", () => {
  it("有全文、有 PDF：「阅读全文」跳到页内全文区块，「下载 PDF」带文件大小，不再给外链主按钮", () => {
    render(
      <PaperHeader
        paper={paper({ hasFullText: true, hasPdf: true, pdfSizeBytes: "2500000", pmcid: "PMC1" })}
      />,
    );
    const read = screen.getByRole("link", { name: /阅读全文/ });
    expect(read).toHaveAttribute("href", "#fulltext");
    expect(read).not.toHaveAttribute("target");
    expect(screen.getByRole("link", { name: /下载 PDF.*2\.5 MB/ })).toHaveAttribute(
      "href",
      "/api/papers/1/pdf",
    );
    expect(screen.queryByRole("link", { name: /去全文/ })).toBeNull();
  });

  it("有全文、没有 PDF：只有「阅读全文」，不显示下载按钮", () => {
    render(<PaperHeader paper={paper({ hasFullText: true, pmcid: "PMC1" })} />);
    expect(screen.getByRole("link", { name: /阅读全文/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /下载 PDF/ })).toBeNull();
  });

  it("只有 PDF：主按钮是「下载 PDF」", () => {
    render(<PaperHeader paper={paper({ hasPdf: true, pdfSizeBytes: "334000", pmcid: "PMC1" })} />);
    expect(screen.getByRole("link", { name: /下载 PDF.*334 KB/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /阅读全文|去全文/ })).toBeNull();
  });

  it("已撤稿：刊头行加「已撤稿」状态胶囊，按钮同无全文", () => {
    render(
      <PaperHeader
        paper={paper({ publicationTypes: ["Journal Article", "Retracted Publication"] })}
      />,
    );
    expect(screen.getByText("已撤稿")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /去全文/ })).toBeInTheDocument();
  });
});
