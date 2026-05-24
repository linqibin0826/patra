import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PaperCard } from "@/components/portal/PaperCard";
import { FEED } from "@/data/feed";

describe("PaperCard", () => {
  const p = FEED.trending[0];
  if (!p) throw new Error("unreachable");

  it("渲染 title", () => {
    render(<PaperCard paper={p} />);
    expect(screen.getByText(p.title)).toBeInTheDocument();
  });

  it("渲染 journal + year", () => {
    render(<PaperCard paper={p} />);
    expect(screen.getByText(/N Engl J Med/)).toBeInTheDocument();
    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });

  it("DOI 应用 font-mono", () => {
    const { container } = render(<PaperCard paper={p} />);
    const doi = container.querySelector("[data-doi]") as HTMLElement | null;
    if (!doi) throw new Error("data-doi element not found");
    expect(doi.className).toMatch(/font-mono/);
  });

  it("渲染 source 标签", () => {
    render(<PaperCard paper={p} />);
    expect(screen.getByText(p.source)).toBeInTheDocument();
  });

  it("作者文本展示 visible 数 + remaining 数", () => {
    render(<PaperCard paper={p} />);
    const remaining = p.authorCount - 2;
    expect(screen.getByText(new RegExp(`等\\s*${remaining}\\s*位作者`))).toBeInTheDocument();
  });

  it("含 AI 速读区域 + summary 文字片段", () => {
    render(<PaperCard paper={p} />);
    expect(screen.getByText("AI 速读")).toBeInTheDocument();
    expect(screen.getByText("自动生成")).toBeInTheDocument();
    expect(screen.getByText(/心血管事件下降/)).toBeInTheDocument();
  });

  it("不包含 emoji 字符 (📖 / ⭐ / 💬)", () => {
    const { container } = render(<PaperCard paper={p} />);
    expect(container.textContent).not.toMatch(/[📖⭐💬]/u);
  });
});
