import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import HomePage from "@/app/page";

describe("HomePage smoke", () => {
  it("渲染 6 个核心区块的 data-section anchor", () => {
    const { container } = render(<HomePage />);
    for (const section of ["topnav", "hero", "topic-cloud", "journals", "explore-feed", "footer"]) {
      expect(container.querySelector(`[data-section='${section}']`)).toBeTruthy();
    }
  });

  it("含 h1 标题 '可被检索'", () => {
    render(<HomePage />);
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(/可被检索/);
  });
});
