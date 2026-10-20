import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { FullTextSkeleton, TocSkeleton } from "@/components/portal/fulltext/FullTextSkeleton";

describe("FullTextSkeleton", () => {
  it("全文区块占位：保留「全文」区块与锚点，标记为加载中", () => {
    render(<FullTextSkeleton />);
    const article = screen.getByRole("article", { name: "全文" });
    expect(article).toHaveAttribute("id", "fulltext");
    expect(article).toHaveAttribute("aria-busy", "true");
  });

  it("占位跟随标签栏版式：区块头「全文 · Full text」，段落条在标签栏的内容格里（PAP-89）", () => {
    const { container } = render(<FullTextSkeleton />);
    expect(screen.getByText(/全文 · Full text/i)).toBeInTheDocument();
    const rows = container.querySelectorAll('[class*="grid-cols-[128px_minmax(0,1fr)]"]');
    expect(rows.length).toBeGreaterThanOrEqual(3);
  });

  it("目录卡占位：5 条骨架条，标记为加载中", () => {
    const { container } = render(<TocSkeleton />);
    expect(container.firstElementChild).toHaveAttribute("aria-busy", "true");
    expect(container.querySelectorAll(".shimmer")).toHaveLength(5);
  });
});
