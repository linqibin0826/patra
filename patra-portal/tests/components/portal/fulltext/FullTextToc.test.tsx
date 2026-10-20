import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

const progress = vi.hoisted(() => ({ body: 0, whole: 0, started: false }));
vi.mock("@/lib/use-reading-progress", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/use-reading-progress")>()),
  useReadingProgress: () => progress,
}));

import { FullTextToc } from "@/components/portal/fulltext/FullTextToc";
import type { TocEntry } from "@/lib/portal-api/fulltext-derive";

const ENTRIES: TocEntry[] = [
  { id: "ft-s1", number: "01", label: "Introduction", children: [] },
  {
    id: "ft-s2",
    number: "02",
    label: "Methods",
    children: [{ id: "ft-s2-1", number: "2.1", label: "Study design", children: [] }],
  },
  { id: "ft-s3", number: "03", label: "Results", children: [] },
];

/** 在页面上放出各章节标题，并指定它们此刻距视口顶部的距离。 */
function placeHeadings(tops: Record<string, number>) {
  for (const [id, top] of Object.entries(tops)) {
    let el = document.getElementById(id);
    if (!el) {
      el = document.createElement("h2");
      el.id = id;
      document.body.append(el);
    }
    el.getBoundingClientRect = () => ({ top }) as DOMRect;
  }
}

function renderToc(overrides: Partial<Parameters<typeof FullTextToc>[0]> = {}) {
  return render(
    <FullTextToc
      entries={ENTRIES}
      declarationCount={8}
      referenceCount={56}
      readMin={25}
      paperId="42"
      hasPdf
      pdfSizeBytes="2500000"
      licenseText="CC BY 4.0"
      fetchedDate="2026-10-18"
      {...overrides}
    />,
  );
}

afterEach(() => {
  Object.assign(progress, { body: 0, whole: 0, started: false });
  for (const id of ["ft-s1", "ft-s2", "ft-s2-1", "ft-s3"]) document.getElementById(id)?.remove();
});

describe("FullTextToc", () => {
  it("卡头是「目录」与阅读时长；列出一级章节，末尾固定声明与附注、参考文献、文献档案与许可", () => {
    renderToc();
    const nav = screen.getByRole("navigation", { name: "目录" });
    expect(within(nav).getByText("≈ 25 分钟")).toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: "Introduction" })).toHaveAttribute(
      "href",
      "#ft-s1",
    );
    expect(within(nav).getByRole("link", { name: "声明与附注 8 项" })).toHaveAttribute(
      "href",
      "#declarations",
    );
    expect(within(nav).getByRole("link", { name: "参考文献 56" })).toHaveAttribute(
      "href",
      "#references",
    );
    expect(within(nav).getByRole("link", { name: "文献档案与许可 ↓" })).toHaveAttribute(
      "href",
      "#record",
    );
  });

  it("一级项前有序号（不进链接名称）", () => {
    renderToc();
    const link = screen.getByRole("link", { name: "Introduction" });
    expect(link).toHaveTextContent("01Introduction");
  });

  it("没有声明、没有参考文献时不列这两项", () => {
    renderToc({ declarationCount: 0, referenceCount: 0 });
    expect(screen.queryByRole("link", { name: /声明与附注/ })).toBeNull();
    expect(screen.queryByRole("link", { name: /^参考文献/ })).toBeNull();
  });

  it("读到正文后显示剩余分钟；正文读完显示「正文已读完」", () => {
    Object.assign(progress, { body: 0.62, whole: 0.5, started: true });
    const { unmount } = renderToc({ readMin: 34 });
    expect(screen.getByText("剩 ≈ 13 分钟")).toBeInTheDocument();
    unmount();
    Object.assign(progress, { body: 1, whole: 0.9, started: true });
    renderToc({ readMin: 34 });
    expect(screen.getByText("正文已读完")).toBeInTheDocument();
  });

  it("标题越过吸顶线的最后一节是当前章节：高亮，并展开它的二级标题", () => {
    placeHeadings({ "ft-s1": -800, "ft-s2": 60, "ft-s2-1": 400, "ft-s3": 900 });
    renderToc();
    act(() => {
      fireEvent.scroll(window);
    });

    expect(screen.getByRole("link", { name: "Methods" })).toHaveAttribute(
      "aria-current",
      "location",
    );
    expect(screen.getByRole("link", { name: "Introduction" })).not.toHaveAttribute("aria-current");
    expect(screen.getByRole("link", { name: "Study design" })).toBeInTheDocument();
  });

  it("滚到二级标题时，二级标题高亮，所属一级章节保持展开", () => {
    placeHeadings({ "ft-s1": -800, "ft-s2": -300, "ft-s2-1": 100, "ft-s3": 900 });
    renderToc();
    act(() => {
      fireEvent.scroll(window);
    });
    expect(screen.getByRole("link", { name: "Study design" })).toHaveAttribute(
      "aria-current",
      "location",
    );
  });

  it("还没读到正文时没有当前章节，二级标题收起", () => {
    placeHeadings({ "ft-s1": 600, "ft-s2": 1200, "ft-s2-1": 1500, "ft-s3": 2000 });
    renderToc();
    act(() => {
      fireEvent.scroll(window);
    });
    expect(screen.queryByRole("link", { name: "Study design" })).toBeNull();
    expect(screen.queryByRole("link", { current: "location" })).toBeNull();
  });

  it("卡底：有 PDF 时整宽下载按钮带大小；许可 · 抓取日期", () => {
    renderToc();
    expect(screen.getByRole("link", { name: /下载 PDF.*2\.5 MB/ })).toHaveAttribute(
      "href",
      "/api/papers/42/pdf",
    );
    expect(screen.getByText("CC BY 4.0 · 抓取于 2026-10-18")).toBeInTheDocument();
  });

  it("没有 PDF 时不显示下载按钮", () => {
    renderToc({ pdfSizeBytes: null, paperId: "42", hasPdf: false });
    expect(screen.queryByRole("link", { name: /下载 PDF/ })).toBeNull();
  });
});
