import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Input } from "@/components/ui/input";

describe("Input", () => {
  it("渲染为 textbox role", () => {
    render(<Input placeholder="email" />);
    expect(screen.getByRole("textbox")).toBeInTheDocument();
  });

  it("透传 className 合并", () => {
    render(<Input className="custom-class" />);
    const input = screen.getByRole("textbox");
    expect(input.className).toMatch(/custom-class/);
    expect(input.className).toMatch(/border-/);
  });

  it("disabled 状态可识别", () => {
    render(<Input disabled placeholder="email" />);
    expect(screen.getByRole("textbox")).toBeDisabled();
  });
});
