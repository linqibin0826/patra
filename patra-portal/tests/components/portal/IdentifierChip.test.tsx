import "@testing-library/jest-dom/vitest";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { IdentifierChip } from "@/components/portal/IdentifierChip";

describe("IdentifierChip", () => {
  it("value 为空 → 不渲染", () => {
    const { container } = render(<IdentifierChip label="ISSN" value={null} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("渲染 label 与 value", () => {
    render(<IdentifierChip label="ISSN" value="1234-5678" />);
    expect(screen.getByText("ISSN")).toBeInTheDocument();
    expect(screen.getByText("1234-5678")).toBeInTheDocument();
  });

  it("长标识符在胶囊内截断：值可收缩，复制图标与外链格不被挤出", () => {
    const long = "10.1016/j.ijid.2026.108341.supplementary";
    render(<IdentifierChip label="DOI" value={long} href={`https://doi.org/${long}`} />);
    expect(screen.getByText(long)).toHaveClass("truncate");
    expect(screen.getByRole("button", { name: `复制 DOI：${long}` })).toHaveClass("min-w-0");
    expect(screen.getByRole("button", { name: `复制 DOI：${long}` }).parentElement).toHaveClass(
      "max-w-full",
    );
  });

  it("有 href → 渲染外链（target=_blank + rel 含 noopener）", () => {
    render(<IdentifierChip label="DOI" value="10.1/x" href="https://doi.org/10.1/x" />);
    const link = screen.getByRole("link", { name: /打开/ });
    expect(link).toHaveAttribute("href", "https://doi.org/10.1/x");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link.getAttribute("rel")).toContain("noopener");
  });
});
