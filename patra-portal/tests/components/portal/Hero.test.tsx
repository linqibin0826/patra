import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Hero } from "@/components/portal/Hero";

describe("Hero", () => {
  it("渲染含 '可被检索' 的 h1", () => {
    render(<Hero />);
    const h1 = screen.getByRole("heading", { level: 1 });
    expect(h1.textContent).toMatch(/可被检索/);
  });

  it("masthead 显示 records 数（4,238,917）", () => {
    render(<Hero />);
    expect(screen.getByText(/4,238,917/)).toBeInTheDocument();
  });

  it("含 Composer 表单（嵌入的 textbox）", () => {
    render(<Hero />);
    expect(screen.getByRole("textbox")).toBeInTheDocument();
  });

  it("含 ornament 元素（仅桌面端可见）", () => {
    const { container } = render(<Hero />);
    const ornament = container.querySelector("[data-ornament]");
    expect(ornament).toBeTruthy();
  });
});
