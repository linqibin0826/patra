import { render, screen, within } from "@testing-library/react";
import { makePaperDetail } from "@tests/fixtures/paper";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PaperRail } from "@/components/portal/paper-detail/PaperRail";

const actions = () => screen.getByText("操作").parentElement as HTMLElement;

describe("PaperRail", () => {
  it("有全文：操作卡是「阅读全文」「下载 PDF」与收藏，外链降为「在 PMC 查看原文」", () => {
    render(
      <PaperRail
        paper={makePaperDetail({
          hasFullText: true,
          hasPdf: true,
          pdfSizeBytes: "2500000",
          pmcid: "PMC13583816",
          estimatedReadMin: 25,
        })}
      />,
    );
    expect(within(actions()).getByRole("link", { name: /阅读全文/ })).toHaveAttribute(
      "href",
      "#fulltext",
    );
    expect(within(actions()).getByRole("link", { name: /下载 PDF/ })).toBeInTheDocument();
    expect(within(actions()).getByRole("link", { name: /在 PMC 查看原文/ })).toHaveAttribute(
      "href",
      "https://pmc.ncbi.nlm.nih.gov/articles/PMC13583816/",
    );
    expect(screen.getByText("≈ 25 分钟")).toBeInTheDocument();
  });

  it("只有 PDF：主按钮「下载 PDF」，同样给 PMC 链接；原文阅读为 —", () => {
    render(
      <PaperRail
        paper={makePaperDetail({ hasPdf: true, pdfSizeBytes: "334000", pmcid: "PMC7121018" })}
      />,
    );
    expect(within(actions()).getByRole("link", { name: /下载 PDF/ })).toBeInTheDocument();
    expect(within(actions()).getByRole("link", { name: /在 PMC 查看原文/ })).toBeInTheDocument();
    expect(within(actions()).queryByRole("link", { name: /阅读全文/ })).toBeNull();
  });

  it("无全文：与原来一致，主按钮去出版方", () => {
    render(<PaperRail paper={makePaperDetail()} />);
    expect(within(actions()).getByRole("link", { name: /去全文 · DOI/ })).toBeInTheDocument();
    expect(within(actions()).queryByRole("link", { name: /PMC/ })).toBeNull();
  });
});
