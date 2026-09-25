import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PapersEmptyResult, papersEmptyKind } from "@/components/portal/papers/PapersEmptyResult";
import { EMPTY_PAPER_QUERY } from "@/lib/portal-api/paper-search";
import { makePaper } from "../../../fixtures/paper";

const none = { total: 0, items: [] };

describe("papersEmptyKind", () => {
  it("有条目 → null", () => {
    expect(papersEmptyKind(EMPTY_PAPER_QUERY, { total: 1, items: [makePaper()] })).toBeNull();
  });

  it("total>0 但当前页无条目 → 页码越界", () => {
    expect(papersEmptyKind(EMPTY_PAPER_QUERY, { total: 5, items: [] })).toBe("page-out-of-range");
  });

  it("精确定位 0 条 → exact-miss；关键词 / 作者 0 条 → no-match", () => {
    expect(papersEmptyKind({ ...EMPTY_PAPER_QUERY, pmid: "1" }, none)).toBe("exact-miss");
    expect(papersEmptyKind({ ...EMPTY_PAPER_QUERY, author: "x" }, none)).toBe("no-match");
  });

  it("只有筛选 0 条 → filter-only；浏览态 0 条 → empty-library", () => {
    expect(papersEmptyKind({ ...EMPTY_PAPER_QUERY, type: ["Review"] }, none)).toBe("filter-only");
    expect(papersEmptyKind({ ...EMPTY_PAPER_QUERY, sort: "year" }, none)).toBe("empty-library");
  });
});

describe("PapersEmptyResult", () => {
  it("no-match：标题带检索词，清除全部筛选保留排序", () => {
    render(
      <PapersEmptyResult
        kind="no-match"
        query={{ ...EMPTY_PAPER_QUERY, q: "GLP-9", type: ["Review"], sort: "year" }}
      />,
    );
    expect(screen.getByRole("heading")).toHaveTextContent('未找到匹配 "GLP-9" 的文献');
    expect(screen.getByRole("link", { name: "清除全部筛选" })).toHaveAttribute(
      "href",
      "/papers?sort=year",
    );
    expect(screen.getByRole("link", { name: "返回首页" })).toHaveAttribute("href", "/");
  });

  it("filter-only", () => {
    render(<PapersEmptyResult kind="filter-only" query={{ ...EMPTY_PAPER_QUERY, oa: true }} />);
    expect(screen.getByRole("heading")).toHaveTextContent("当前筛选条件下没有文献");
    expect(screen.getByRole("link", { name: "清除全部筛选" })).toHaveAttribute("href", "/papers");
  });

  it("exact-miss：PMID / DOI 两种文案", () => {
    const { unmount } = render(
      <PapersEmptyResult kind="exact-miss" query={{ ...EMPTY_PAPER_QUERY, pmid: "38491203" }} />,
    );
    expect(screen.getByRole("heading")).toHaveTextContent("未找到 PMID 38491203 对应的文献");
    expect(screen.getByRole("link", { name: "浏览全部文献" })).toHaveAttribute("href", "/papers");
    unmount();
    render(<PapersEmptyResult kind="exact-miss" query={{ ...EMPTY_PAPER_QUERY, doi: "10.1/x" }} />);
    expect(screen.getByRole("heading")).toHaveTextContent("未找到 DOI 10.1/x 对应的文献");
  });

  it("empty-library", () => {
    render(<PapersEmptyResult kind="empty-library" query={EMPTY_PAPER_QUERY} />);
    expect(screen.getByRole("heading")).toHaveTextContent("库中暂无文献");
  });

  it("page-out-of-range：回到第 1 页保留条件", () => {
    render(
      <PapersEmptyResult
        kind="page-out-of-range"
        query={{ ...EMPTY_PAPER_QUERY, q: "x", page: 99 }}
      />,
    );
    expect(screen.getByRole("heading")).toHaveTextContent("该页没有内容");
    expect(screen.getByRole("link", { name: "回到第 1 页" })).toHaveAttribute(
      "href",
      "/papers?q=x",
    );
  });
});
