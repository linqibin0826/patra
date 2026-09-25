import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { BrowseHead } from "@/components/portal/browse/BrowseHead";
import { EmptyState } from "@/components/portal/browse/EmptyState";
import { FacetSkeleton } from "@/components/portal/browse/FacetSkeleton";

describe("BrowseHead", () => {
  it("渲染面包屑、eyebrow、h1 与副文案", () => {
    render(
      <BrowseHead
        crumb="文献浏览"
        eyebrow="按文献浏览"
        title="浏览全部文献"
        description="说明文字"
      />,
    );
    const crumbs = screen.getByRole("navigation", { name: "面包屑" });
    expect(crumbs).toHaveTextContent("Patra/文献浏览");
    expect(screen.getByRole("link", { name: "Patra" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("heading", { level: 1, name: "浏览全部文献" })).toBeInTheDocument();
    expect(screen.getByText("按文献浏览")).toBeInTheDocument();
    expect(screen.getByText("说明文字")).toBeInTheDocument();
  });
});

describe("EmptyState", () => {
  it("渲染标题、说明与按钮链接", () => {
    render(
      <EmptyState
        title="未找到匹配的文献"
        description="换个关键词试试"
        actions={[
          { href: "/papers", label: "清除全部筛选" },
          { href: "/", label: "返回首页" },
        ]}
      />,
    );
    expect(screen.getByRole("heading", { name: "未找到匹配的文献" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "清除全部筛选" })).toHaveAttribute("href", "/papers");
    expect(screen.getByRole("link", { name: "返回首页" })).toHaveAttribute("href", "/");
  });
});

describe("FacetSkeleton", () => {
  it("对辅助技术隐藏，可追加 class", () => {
    const { container } = render(<FacetSkeleton className="w-60" />);
    const aside = container.querySelector("aside");
    expect(aside).toHaveAttribute("aria-hidden", "true");
    expect(aside?.className).toMatch(/w-60/);
  });
});
