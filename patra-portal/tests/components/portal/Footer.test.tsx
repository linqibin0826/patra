import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Footer } from "@/components/portal/Footer";

describe("Footer", () => {
  it("渲染 contentinfo role", () => {
    render(<Footer />);
    expect(screen.getByRole("contentinfo")).toBeInTheDocument();
  });

  it("含 Patra brand 文字", () => {
    render(<Footer />);
    expect(screen.getByText("Patra")).toBeInTheDocument();
  });

  it("含数据源 / 关于 / 更新日志 / GitHub 导航链接", () => {
    render(<Footer />);
    expect(screen.getByRole("link", { name: "数据源" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "关于" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "更新日志" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /GitHub/ })).toBeInTheDocument();
  });

  it("含版本与索引快照标签", () => {
    render(<Footer />);
    expect(screen.getByText(/索引快照/)).toBeInTheDocument();
  });
});
