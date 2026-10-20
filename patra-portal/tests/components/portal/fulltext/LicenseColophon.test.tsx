import { render, screen, within } from "@testing-library/react";
import { makeFullText } from "@tests/fixtures/fulltext";
import { makePaperDetail } from "@tests/fixtures/paper";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { LicenseColophon } from "@/components/portal/fulltext/LicenseColophon";
import type { Author } from "@/types/portal";

const authors = (n: number): Author[] =>
  Array.from({ length: n }, (_, i) => ({
    order: i + 1,
    first: i === 0,
    corresponding: false,
    name: `Author ${i + 1}`,
    affiliation: null,
  }));

/** 定义表里某一行（dt 文本 → 对应的 dd）。 */
function row(name: string): HTMLElement {
  const dt = screen.getByText(name, { selector: "dt" });
  return dt.nextElementSibling as HTMLElement;
}

describe("LicenseColophon", () => {
  it("许可链到许可证地址，并声明只做格式转换", () => {
    render(<LicenseColophon fullText={makeFullText()} paper={makePaperDetail()} />);
    expect(screen.getByRole("region", { name: "许可与出处" })).toHaveAttribute("id", "license");
    expect(within(row("许可")).getByRole("link", { name: /CC BY 4\.0/ })).toHaveAttribute(
      "href",
      "https://creativecommons.org/licenses/by/4.0/",
    );
    expect(row("许可")).toHaveTextContent("Patra 按原文许可转载，只做格式转换，未改动内容。");
  });

  it("许可为空时写「许可见原文」", () => {
    render(
      <LicenseColophon fullText={makeFullText({ license: null })} paper={makePaperDetail()} />,
    );
    expect(row("许可")).toHaveTextContent("许可见原文");
    expect(within(row("许可")).queryByRole("link")).toBeNull();
  });

  it("作者超过 10 位写前 10 位 + 等 N 位", () => {
    render(
      <LicenseColophon
        fullText={makeFullText()}
        paper={makePaperDetail({ authors: authors(13) })}
      />,
    );
    expect(row("作者")).toHaveTextContent("Author 10");
    expect(row("作者")).not.toHaveTextContent("Author 11");
    expect(row("作者")).toHaveTextContent("等 13 位");
  });

  it("出处、DOI、PMC、NLM 数据来源齐全", () => {
    render(<LicenseColophon fullText={makeFullText()} paper={makePaperDetail()} />);
    expect(row("出处")).toHaveTextContent("BMJ Open. 2026");
    expect(within(row("DOI")).getByRole("link")).toHaveAttribute(
      "href",
      "https://doi.org/10.1136/bmjopen-2026-122934",
    );
    expect(within(row("PMC")).getByRole("link", { name: /PMC13583816/ })).toHaveAttribute(
      "href",
      "https://pmc.ncbi.nlm.nih.gov/articles/PMC13583816/",
    );
    expect(row("数据来源")).toHaveTextContent(/^美国国家医学图书馆（NLM）PubMed Central$/);
  });

  it("抓取日期附「可能不是最新版本」说明与 PMC 最新版链接", () => {
    render(<LicenseColophon fullText={makeFullText()} paper={makePaperDetail()} />);
    expect(row("抓取日期")).toHaveTextContent("2026-10-18");
    expect(row("抓取日期")).toHaveTextContent("可能不是最新版本，以 PMC 原文为准");
    expect(within(row("抓取日期")).getByRole("link", { name: /查看 PMC 最新版/ })).toHaveAttribute(
      "href",
      "https://pmc.ncbi.nlm.nih.gov/articles/PMC13583816/",
    );
  });

  it("只有作者手稿显示「版本」一行", () => {
    const { rerender } = render(
      <LicenseColophon fullText={makeFullText()} paper={makePaperDetail()} />,
    );
    expect(screen.queryByText("版本", { selector: "dt" })).toBeNull();

    rerender(
      <LicenseColophon fullText={makeFullText({ isManuscript: true })} paper={makePaperDetail()} />,
    );
    expect(row("版本")).toHaveTextContent("作者手稿");
  });
});

describe("LicenseColophon · 标签栏排版（PAP-89）", () => {
  it("不再是卡片盒；每行 dt 在标签格、dd 在内容格", () => {
    render(<LicenseColophon fullText={makeFullText()} paper={makePaperDetail()} />);
    const region = screen.getByRole("region", { name: "许可与出处" });
    expect(region.className).not.toContain("rounded-xl");
    const dt = screen.getByText("许可", { selector: "dt" });
    expect(dt.parentElement?.className).toContain("grid-cols-[128px_minmax(0,1fr)]");
  });
});
