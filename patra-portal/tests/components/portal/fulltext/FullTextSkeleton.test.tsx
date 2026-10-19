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

  it("目录卡占位：5 条骨架条，标记为加载中", () => {
    const { container } = render(<TocSkeleton />);
    expect(container.firstElementChild).toHaveAttribute("aria-busy", "true");
    expect(container.querySelectorAll(".shimmer")).toHaveLength(5);
  });
});
