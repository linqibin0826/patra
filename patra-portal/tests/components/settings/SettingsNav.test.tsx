import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { SettingsNav } from "@/components/settings/SettingsNav";

let mockPathname = "/settings/profile";
vi.mock("next/navigation", () => ({ usePathname: () => mockPathname }));

afterEach(() => {
  mockPathname = "/settings/profile";
});

describe("SettingsNav", () => {
  it("桌面侧栏四项：个人资料、账号、安全、隐私，不渲染通知与已连接账号", () => {
    render(<SettingsNav />);
    const desktop = screen.getByRole("navigation", { name: "设置分区" });
    const links = Array.from(desktop.querySelectorAll("a"));
    expect(links.map((link) => [link.textContent, link.getAttribute("href")])).toEqual([
      ["个人资料", "/settings/profile"],
      ["账号", "/settings/account"],
      ["安全", "/settings/security"],
      ["隐私", "/settings/privacy"],
    ]);
    expect(screen.queryByText("通知")).not.toBeInTheDocument();
    expect(screen.queryByText("已连接账号")).not.toBeInTheDocument();
  });

  it("移动分段标签用短名：资料、账号、安全、隐私", () => {
    render(<SettingsNav />);
    const mobile = screen.getByRole("navigation", { name: "设置分区（窄屏）" });
    expect(Array.from(mobile.querySelectorAll("a")).map((link) => link.textContent)).toEqual([
      "资料",
      "账号",
      "安全",
      "隐私",
    ]);
  });

  it("当前分区标 aria-current", () => {
    mockPathname = "/settings/privacy";
    render(<SettingsNav />);
    const current = screen.getAllByRole("link", { current: "page" });
    expect(current.map((link) => link.getAttribute("href"))).toEqual([
      "/settings/privacy",
      "/settings/privacy",
    ]);
  });
});
