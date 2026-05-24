import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Badge } from "@/components/ui/badge";

describe("Badge", () => {
  it("渲染文字内容", () => {
    render(<Badge>NEW</Badge>);
    expect(screen.getByText("NEW")).toBeInTheDocument();
  });

  it("默认 variant 应用 bg-primary class", () => {
    const { container } = render(<Badge>x</Badge>);
    const root = container.firstChild as HTMLElement;
    expect(root.className).toMatch(/bg-primary/);
  });

  it("secondary variant 应用 bg-secondary class", () => {
    const { container } = render(<Badge variant="secondary">x</Badge>);
    const root = container.firstChild as HTMLElement;
    expect(root.className).toMatch(/bg-secondary/);
  });

  it("透传 className 合并", () => {
    const { container } = render(<Badge className="custom-badge">x</Badge>);
    const root = container.firstChild as HTMLElement;
    expect(root.className).toMatch(/custom-badge/);
  });
});
