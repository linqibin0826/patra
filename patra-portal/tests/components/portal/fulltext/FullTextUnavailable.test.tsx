import { render, screen, within } from "@testing-library/react";
import { makeFullText } from "@tests/fixtures/fulltext";
import { makePaperDetail } from "@tests/fixtures/paper";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn() }) }));

import { FullTextUnavailable } from "@/components/portal/fulltext/FullTextUnavailable";

/** 定义表里某一行（dt 文本 → 对应的 dd）。 */
function row(name: string): HTMLElement {
  return screen.getByText(name, { selector: "dt" }).nextElementSibling as HTMLElement;
}

describe("FullTextUnavailable", () => {
  it("不在开放获取子集：说明原因，主按钮去出版方（DOI）", () => {
    render(<FullTextUnavailable kind="not-oa" paper={makePaperDetail()} fullText={null} />);
    expect(screen.getByRole("heading", { name: "暂无开放全文" })).toBeInTheDocument();
    expect(screen.getByText(/只转载 PubMed Central 开放获取子集里的全文/)).toBeInTheDocument();
    const go = screen.getByRole("link", { name: /去全文 · DOI/ });
    expect(go).toHaveAttribute("href", "https://doi.org/10.1136/bmjopen-2026-122934");
    expect(go).toHaveAttribute("target", "_blank");
  });

  it("没有 DOI 时去 PubMed", () => {
    render(
      <FullTextUnavailable kind="not-oa" paper={makePaperDetail({ doi: null })} fullText={null} />,
    );
    expect(screen.getByRole("link", { name: /去全文 · PubMed/ })).toHaveAttribute(
      "href",
      "https://pubmed.ncbi.nlm.nih.gov/42776074",
    );
  });

  it("PMC 收录但不能转载：主按钮去 PMC，次按钮去 DOI", () => {
    render(
      <FullTextUnavailable
        kind="pmc-only"
        paper={makePaperDetail({ pmcid: "PMC12879293" })}
        fullText={null}
      />,
    );
    expect(screen.getByRole("heading", { name: "PMC 收录了这篇文献" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /去 PMC 查看/ })).toHaveAttribute(
      "href",
      "https://pmc.ncbi.nlm.nih.gov/articles/PMC12879293/",
    );
    expect(screen.getByRole("link", { name: /去全文 · DOI/ })).toBeInTheDocument();
  });

  it("已撤稿且有 PMCID：说明撤稿，去 PMC 与去 DOI 都是次按钮", () => {
    render(
      <FullTextUnavailable
        kind="retracted"
        paper={makePaperDetail({ pmcid: "PMC12829825", retracted: true })}
        fullText={null}
      />,
    );
    expect(screen.getByRole("heading", { name: "已撤稿" })).toBeInTheDocument();
    expect(
      screen.getByText("这篇文献已撤稿，Patra 不展示正文——撤稿说明可以去 PMC 或出版方页面查看。"),
    ).toBeInTheDocument();
    const pmc = screen.getByRole("link", { name: /去 PMC 查看/ });
    expect(pmc).toHaveAttribute("href", "https://pmc.ncbi.nlm.nih.gov/articles/PMC12829825/");
    const doi = screen.getByRole("link", { name: /去全文 · DOI/ });
    expect(pmc.className).toBe(doi.className);
  });

  it("已撤稿但没有 PMCID：撤稿说明指向出版方，只留去 DOI", () => {
    render(
      <FullTextUnavailable
        kind="retracted"
        paper={makePaperDetail({ retracted: true })}
        fullText={null}
      />,
    );
    expect(
      screen.getByText("这篇文献已撤稿，Patra 不展示正文——撤稿说明见出版方页面。"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /去 PMC 查看/ })).toBeNull();
    expect(screen.getByRole("link", { name: /去全文 · DOI/ })).toBeInTheDocument();
  });

  it("只有 PDF：下载按钮带大小；PDF 同样署名（许可、数据来源、PMC、抓取日期）", () => {
    render(
      <FullTextUnavailable
        kind="pdf-only"
        paper={makePaperDetail({ hasPdf: true, pdfSizeBytes: "334000", pmcid: "PMC7121018" })}
        fullText={makeFullText({ body: null, license: null, pmcid: "PMC7121018" })}
      />,
    );
    expect(screen.getByRole("heading", { name: "只有 PDF 版本" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /下载 PDF.*334 KB/ })).toHaveAttribute(
      "href",
      "/api/papers/352303012816019646/pdf",
    );
    expect(row("许可")).toHaveTextContent("许可见原文");
    expect(row("许可")).toHaveTextContent("PDF 按原文许可转载，未作改动。");
    expect(row("数据来源")).toHaveTextContent("美国国家医学图书馆（NLM）PubMed Central");
    expect(within(row("PMC")).getByRole("link")).toHaveAttribute(
      "href",
      "https://pmc.ncbi.nlm.nih.gov/articles/PMC13583816/",
    );
    expect(row("抓取日期")).toHaveTextContent("PDF 抓取于 2026-10-18");
  });

  it("加载失败：重新加载 + 有 PDF 时下载 PDF", () => {
    render(
      <FullTextUnavailable
        kind="error"
        paper={makePaperDetail({ hasFullText: true, hasPdf: true, pdfSizeBytes: "2500000" })}
        fullText={null}
      />,
    );
    expect(screen.getByRole("heading", { name: "全文没能加载" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新加载" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /下载 PDF/ })).toBeInTheDocument();
  });
});
