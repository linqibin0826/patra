import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { TopNav } from "@/components/portal/TopNav";

describe("TopNav", () => {
  it("渲染 banner role", () => {
    render(<TopNav />);
    expect(screen.getByRole("banner")).toBeInTheDocument();
  });

  it("首页 link 标记为 aria-current=page", () => {
    render(<TopNav />);
    const homeLink = screen.getByRole("link", { name: /首页/ });
    expect(homeLink).toHaveAttribute("aria-current", "page");
  });

  it("文献 / 期刊 / 主题 link 标记为 aria-disabled", () => {
    render(<TopNav />);
    for (const label of ["文献", "期刊", "主题"]) {
      const link = screen.getByText(label).closest("a");
      expect(link).toHaveAttribute("aria-disabled", "true");
    }
  });

  it("点击汉堡按钮打开 Sheet drawer", async () => {
    const user = userEvent.setup();
    render(<TopNav />);
    const menuBtn = screen.getByRole("button", { name: /打开菜单|menu/i });
    await user.click(menuBtn);
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });
});
