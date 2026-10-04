import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { Footer } from "@/components/portal/Footer";

describe("Footer", () => {
  it("渲染 contentinfo role", () => {
    render(<Footer />);
    expect(screen.getByRole("contentinfo")).toBeInTheDocument();
  });

  it("品牌链接回到页首", () => {
    render(<Footer />);
    expect(screen.getByRole("link", { name: "Patra" })).toHaveAttribute("href", "#top");
  });

  it("「关于」锚点落在页脚的关于段", () => {
    render(<Footer />);
    expect(screen.getByRole("link", { name: "关于" })).toHaveAttribute("href", "#about");
    expect(screen.getByRole("region", { name: "关于 Patra" })).toHaveAttribute("id", "about");
  });

  it("「数据源」锚点落在来源列表，列出主要来源", () => {
    render(<Footer />);
    expect(screen.getByRole("link", { name: "数据源" })).toHaveAttribute("href", "#sources");
    const list = screen.getByRole("list", { name: "数据来源" });
    expect(list).toHaveAttribute("id", "sources");
    for (const source of ["PubMed", "Europe PMC", "Crossref"]) {
      expect(within(list).getByText(source)).toBeInTheDocument();
    }
  });

  it("GitHub 与更新日志指向真实仓库", () => {
    render(<Footer />);
    expect(screen.getByRole("link", { name: /GitHub/ })).toHaveAttribute(
      "href",
      "https://github.com/linqibin0826/patra",
    );
    expect(screen.getByRole("link", { name: /更新日志/ })).toHaveAttribute(
      "href",
      "https://github.com/linqibin0826/patra/commits/main",
    );
  });

  it("底栏不写过期的版本号与写死的快照时间", () => {
    render(<Footer />);
    expect(screen.queryByText(/V0\.4/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/索引快照/)).not.toBeInTheDocument();
  });

  it("关于段全角句号后不多出半角空格", () => {
    render(<Footer />);
    expect(screen.getByText(/装订成册。Patra 把散落在/)).toBeInTheDocument();
  });
});
