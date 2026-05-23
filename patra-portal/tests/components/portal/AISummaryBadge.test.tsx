import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { AISummaryBadge } from "@/components/portal/AISummaryBadge";

describe("AISummaryBadge", () => {
  it("渲染 'AI 速读' 标签", () => {
    render(<AISummaryBadge summary="测试摘要" />);
    expect(screen.getByText("AI 速读")).toBeInTheDocument();
  });

  it("渲染传入的 summary 文字", () => {
    render(<AISummaryBadge summary="心血管事件下降 22%" />);
    expect(screen.getByText(/心血管事件下降/)).toBeInTheDocument();
  });

  it("应用 clay border + paper bg utility class", () => {
    const { container } = render(<AISummaryBadge summary="x" />);
    const root = container.firstChild as HTMLElement;
    expect(root.className).toMatch(/border-l/);
    expect(root.className).toMatch(/clay/);
    expect(root.className).toMatch(/(paper|amber|clay)-(50|100)/);
  });
});
