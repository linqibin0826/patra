import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Journals } from "@/components/portal/Journals";

describe("Journals", () => {
  it("渲染 6 张封面卡片", () => {
    render(<Journals />);
    const buttons = screen.getAllByRole("button");
    // 6 张 JournalCoverCard（每张渲染为 button）+ 1 个"浏览全部"button
    expect(buttons.length).toBeGreaterThanOrEqual(6);
  });

  it("包含 NEJM 封面文字", () => {
    render(<Journals />);
    expect(screen.getByText("NEJM")).toBeInTheDocument();
  });

  it("含 '高影响力期刊' 标题", () => {
    render(<Journals />);
    expect(screen.getByRole("heading", { name: /高影响力期刊/ })).toBeInTheDocument();
  });
});
