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
    expect(screen.getByText(/Patra/i)).toBeInTheDocument();
  });

  it("含版权年份 2026", () => {
    render(<Footer />);
    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });
});
