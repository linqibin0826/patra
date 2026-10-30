import { screen } from "@testing-library/react";
import { renderWithQuery } from "@tests/support/render-with-query";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { useSaveStatus } from "@/components/library/LibraryStatusProvider";
import { PaperHeader } from "@/components/portal/paper-detail/PaperHeader";
import type { PaperDetail } from "@/types/portal";

const LINK = "http://mini/papers/1";

function paper(overrides: Partial<PaperDetail> = {}): PaperDetail {
  return {
    id: "1",
    title: "Semaglutide RCT",
    originalTitle: null,
    venueId: "42",
    venueName: "N Engl J Med",
    publicationYear: 2024,
    publishedDate: "2024",
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
    estimatedReadMin: null,
    retracted: false,
    hasFullText: false,
    hasPdf: false,
    pdfSizeBytes: null,
    ...overrides,
  };
}

describe("PaperHeader", () => {
  it("作者行的日期按精度写：到月写「2019 年 3 月」，不补出 1 月 1 日", () => {
    renderWithQuery(
      <PaperHeader
        shareLink={LINK}
        paper={paper({ publicationYear: 2019, publishedDate: "2019-03" })}
      />,
    );
    expect(screen.getByText(/2019 年 3 月/)).toBeInTheDocument();
    expect(screen.queryByText(/1 月 1 日/)).not.toBeInTheDocument();
  });

  it("到日写「2019 年 3 月 15 日」", () => {
    renderWithQuery(
      <PaperHeader
        shareLink={LINK}
        paper={paper({ publicationYear: 2019, publishedDate: "2019-03-15" })}
      />,
    );
    expect(screen.getByText(/2019 年 3 月 15 日/)).toBeInTheDocument();
  });

  it("渲染标题 + 期刊链接（指向 /journals/[venueId]）+ 去全文链接", () => {
    renderWithQuery(<PaperHeader shareLink={LINK} paper={paper()} />);
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Semaglutide RCT");
    expect(screen.getByRole("link", { name: "N Engl J Med" })).toHaveAttribute(
      "href",
      "/journals/42",
    );
    expect(screen.getByRole("link", { name: /去全文/ })).toHaveAttribute("href", "https://full");
  });
  it("无全文链接时按钮禁用", () => {
    renderWithQuery(
      <PaperHeader shareLink={LINK} paper={paper({ fullTextUrl: null, doi: null, pmid: null })} />,
    );
    expect(screen.getByRole("button", { name: /全文链接不可用/ })).toBeDisabled();
  });
  it("原题按白名单渲染内联标签（回归：防止改回纯文本插值）", () => {
    const { container } = renderWithQuery(
      <PaperHeader
        shareLink={LINK}
        paper={paper({ originalTitle: "Studie zu <i>E. coli</i> und CO<sub>2</sub>" })}
      />,
    );
    expect(container.querySelector("i")?.textContent).toBe("E. coli");
    expect(container.querySelector("sub")).not.toBeNull();
  });
});

describe("PaperHeader · 全文按钮", () => {
  it("有全文、有 PDF：「阅读全文」跳到页内全文区块，「下载 PDF」带文件大小，不再给外链主按钮", () => {
    renderWithQuery(
      <PaperHeader
        shareLink={LINK}
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
    renderWithQuery(
      <PaperHeader shareLink={LINK} paper={paper({ hasFullText: true, pmcid: "PMC1" })} />,
    );
    expect(screen.getByRole("link", { name: /阅读全文/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /下载 PDF/ })).toBeNull();
  });

  it("只有 PDF：主按钮是「下载 PDF」", () => {
    renderWithQuery(
      <PaperHeader
        shareLink={LINK}
        paper={paper({ hasPdf: true, pdfSizeBytes: "334000", pmcid: "PMC1" })}
      />,
    );
    expect(screen.getByRole("link", { name: /下载 PDF.*334 KB/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /阅读全文|去全文/ })).toBeNull();
  });

  it("已撤稿：刊头行加「已撤稿」状态胶囊，按钮同无全文", () => {
    renderWithQuery(<PaperHeader shareLink={LINK} paper={paper({ retracted: true })} />);
    expect(screen.getByText("已撤稿")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /去全文/ })).toBeInTheDocument();
  });
});

const PMID_41575322 =
  "Explainable machine learning for early diagnosis of esophageal cancer: A feature-enriched Light Gradient Boosting Machine framework with Shapley Additive Explanations and Local Interpretable Model-Agnostic Explanations interpretations.";

describe("PaperHeader · 长标题与页头结构（PAP-89）", () => {
  it("冒号处拆成主标题 h1 与副标题，都没有末尾句点", () => {
    renderWithQuery(<PaperHeader shareLink={LINK} paper={paper({ title: PMID_41575322 })} />);
    const h1 = screen.getByRole("heading", { level: 1 });
    expect(h1).toHaveTextContent(
      /^Explainable machine learning for early diagnosis of esophageal cancer$/,
    );
    const sub = h1.nextElementSibling;
    expect(sub?.tagName).toBe("P");
    expect(sub).toHaveTextContent(/^A feature-enriched.*interpretations$/);
  });

  it("超过 120 字符、没有冒号的标题降到 md 档", () => {
    renderWithQuery(
      <PaperHeader shareLink={LINK} paper={paper({ title: `${"word ".repeat(25)}end` })} />,
    );
    expect(screen.getByRole("heading", { level: 1 })).toHaveAttribute("data-size", "md");
  });

  it("根元素是 #masthead，不再渲染证据徽章（等级进了事实条）", () => {
    const { container } = renderWithQuery(<PaperHeader shareLink={LINK} paper={paper()} />);
    expect(container.querySelector("section#masthead")).not.toBeNull();
    expect(screen.queryByTitle(/证据等级/)).toBeNull();
  });

  it("操作行有阅读全文、下载 PDF、收藏，有 PMCID 时加「在 PMC 查看原文 ↗」", () => {
    renderWithQuery(
      <PaperHeader
        shareLink={LINK}
        paper={paper({ hasFullText: true, hasPdf: true, pdfSizeBytes: "1751924", pmcid: "PMC1" })}
      />,
    );
    expect(screen.getByRole("link", { name: /阅读全文/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /下载 PDF/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /收藏/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "在 PMC 查看原文 ↗" })).toHaveAttribute(
      "href",
      "https://pmc.ncbi.nlm.nih.gov/articles/PMC1/",
    );
  });

  it("540px 以下操作按钮各占一行、撑满宽度（spec §6）", () => {
    renderWithQuery(
      <PaperHeader
        shareLink={LINK}
        paper={paper({ hasFullText: true, hasPdf: true, pmcid: "PMC1" })}
      />,
    );
    const row = screen.getByRole("link", { name: /阅读全文/ }).parentElement as HTMLElement;
    expect(row.className).toContain("max-[540px]:flex-col");
    expect(row.className).toContain("max-[540px]:items-stretch");
  });

  it("事实条插槽渲染在作者行之后", () => {
    renderWithQuery(
      <PaperHeader shareLink={LINK} paper={paper()} fullTextFacts={<div>FACTS-SLOT</div>} />,
    );
    expect(screen.getByText("FACTS-SLOT")).toBeInTheDocument();
  });
});

describe("PaperHeader · 分享与窄屏操作行（PAP-115，简报 §3.3）", () => {
  /// 次按钮组里各按钮的读屏名（按 DOM 顺序）
  function secondaryNames() {
    const group = screen.getByRole("group", { name: "次要操作" });
    return Array.from(group.querySelectorAll("a, button")).map(
      (el) => el.getAttribute("aria-label") ?? el.textContent ?? "",
    );
  }

  it("has share button", () => {
    renderWithQuery(<PaperHeader shareLink={LINK} paper={paper()} />);
    expect(screen.getByRole("button", { name: "分享" })).toBeInTheDocument();
  });

  it("secondary group with pdf：下载 PDF · 收藏 · 分享，主按钮不在组里", () => {
    renderWithQuery(
      <PaperHeader
        shareLink={LINK}
        paper={paper({ hasFullText: true, hasPdf: true, pdfSizeBytes: "2500000", pmcid: "PMC1" })}
      />,
    );
    const names = secondaryNames();
    expect(names).toHaveLength(3);
    expect(names[0]).toMatch(/^下载 PDF/);
    expect(names[1]).toMatch(/收藏/);
    expect(names[2]).toBe("分享");
    const group = screen.getByRole("group", { name: "次要操作" });
    expect(group).not.toContainElement(screen.getByRole("link", { name: /阅读全文/ }));
  });

  it.each([
    ["全文无 PDF", { hasFullText: true, pmcid: "PMC1" }],
    ["只有 PDF", { hasPdf: true, pmcid: "PMC1" }],
    ["无全文", {}],
  ] as const)("secondary group without pdf（%s）：收藏 · 分享", (_, overrides) => {
    renderWithQuery(<PaperHeader shareLink={LINK} paper={paper(overrides)} />);
    const names = secondaryNames();
    expect(names).toHaveLength(2);
    expect(names[1]).toBe("分享");
  });

  it("只有 PDF 时「下载 PDF」是组外的主按钮", () => {
    renderWithQuery(
      <PaperHeader shareLink={LINK} paper={paper({ hasPdf: true, pmcid: "PMC1" })} />,
    );
    const group = screen.getByRole("group", { name: "次要操作" });
    expect(group).not.toContainElement(screen.getByRole("link", { name: /下载 PDF/ }));
  });

  it("窄屏次按钮等分（grid 自动列）", () => {
    renderWithQuery(<PaperHeader shareLink={LINK} paper={paper()} />);
    const group = screen.getByRole("group", { name: "次要操作" });
    expect(group.className).toContain("max-[540px]:grid");
    expect(group.className).toContain("max-[540px]:auto-cols-fr");
  });

  it("pmc link after group", () => {
    renderWithQuery(
      <PaperHeader shareLink={LINK} paper={paper({ hasFullText: true, pmcid: "PMC1" })} />,
    );
    const group = screen.getByRole("group", { name: "次要操作" });
    const pmc = screen.getByRole("link", { name: "在 PMC 查看原文 ↗" });
    expect(group.compareDocumentPosition(pmc) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });
});

describe("PaperHeader · 事实条的互动两格", () => {
  /// 读这篇的收藏状态：在 `LibraryStatusProvider` 里未登录是 anon，在外面是 pending
  function StatusProbe({ id }: { id: string }) {
    return (
      <div>
        <dt>探针</dt>
        <dd>{useSaveStatus(id).kind}</dd>
      </div>
    );
  }

  it("should_render_interaction_facts_inside_library_status_scope", () => {
    renderWithQuery(
      <PaperHeader paper={paper()} shareLink={LINK} interactionFacts={<StatusProbe id="1" />} />,
    );
    expect(screen.getByText("探针").nextElementSibling).toHaveTextContent("anon");
  });
});
