import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { TopNav } from "@/components/portal/TopNav";

// 仅覆写 usePathname，保留 next/navigation 其余导出（Next <Link> 渲染所需）
let mockPathname = "/";
vi.mock("next/navigation", async (importOriginal) => {
  const actual = await importOriginal<typeof import("next/navigation")>();
  return { ...actual, usePathname: () => mockPathname };
});

afterEach(() => {
  mockPathname = "/";
});

describe("TopNav", () => {
  it("渲染 banner role", () => {
    render(<TopNav />);
    expect(screen.getByRole("banner")).toBeInTheDocument();
  });

  it("在首页路径下，首页 link 标记为 aria-current=page", () => {
    mockPathname = "/";
    render(<TopNav />);
    const homeLink = screen.getByRole("link", { name: /首页/ });
    expect(homeLink).toHaveAttribute("aria-current", "page");
  });

  it("期刊 link 指向 /journals 且不再 disabled", () => {
    render(<TopNav />);
    const journalLink = screen.getByRole("link", { name: /期刊/ });
    expect(journalLink).toHaveAttribute("href", "/journals");
    expect(journalLink).not.toHaveAttribute("aria-disabled");
  });

  it("在 /journals 路径下，期刊 高亮、首页 不再高亮", () => {
    mockPathname = "/journals";
    render(<TopNav />);
    const journalLink = screen.getByRole("link", { name: /期刊/ });
    expect(journalLink).toHaveAttribute("aria-current", "page");
    const homeLink = screen.getByRole("link", { name: /首页/ });
    expect(homeLink).not.toHaveAttribute("aria-current", "page");
  });

  it("在 /journals 子路由（详情）下，期刊 仍高亮", () => {
    mockPathname = "/journals/123";
    render(<TopNav />);
    const journalLink = screen.getByRole("link", { name: /期刊/ });
    expect(journalLink).toHaveAttribute("aria-current", "page");
  });

  it("主题 tab 已移除（不再渲染）", () => {
    render(<TopNav />);
    expect(screen.queryByText("主题")).not.toBeInTheDocument();
  });

  it("点击汉堡按钮打开 Sheet drawer", async () => {
    const user = userEvent.setup();
    render(<TopNav />);
    const menuBtn = screen.getByRole("button", { name: /打开菜单|menu/i });
    await user.click(menuBtn);
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });

  it("文献 link 指向 /papers，不再 disabled / SOON", () => {
    render(<TopNav />);
    const link = screen.getByRole("link", { name: "文献" });
    expect(link).toHaveAttribute("href", "/papers");
    expect(link).not.toHaveAttribute("aria-disabled");
    expect(screen.queryByText(/soon/i)).not.toBeInTheDocument();
  });

  it("在 /papers 与 /papers/[id] 下，文献 高亮", () => {
    mockPathname = "/papers/123";
    render(<TopNav />);
    expect(screen.getByRole("link", { name: "文献" })).toHaveAttribute("aria-current", "page");
  });

  it("移动菜单中文献同样可点", async () => {
    const user = userEvent.setup();
    render(<TopNav />);
    await user.click(screen.getByRole("button", { name: /打开菜单|menu/i }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("link", { name: "文献" })).toHaveAttribute("href", "/papers");
  });
});
