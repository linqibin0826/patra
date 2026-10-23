import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { SettingsCard } from "@/components/settings/SettingsCard";
import { SettingsShell } from "@/components/settings/SettingsShell";

vi.mock("next/navigation", () => ({ usePathname: () => "/settings/profile" }));

describe("SettingsShell", () => {
  it("标题「设置」、@用户名、查看我的主页", () => {
    render(
      <SettingsShell username="chen-yu">
        <p>内容</p>
      </SettingsShell>,
    );
    expect(screen.getByRole("heading", { level: 1, name: "设置" })).toBeInTheDocument();
    expect(screen.getByText("@chen-yu")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /查看我的主页/ })).toHaveAttribute(
      "href",
      "/u/chen-yu",
    );
    expect(screen.getByText("内容")).toBeInTheDocument();
  });

  it("还不知道用户名（未登录、页面会跳走）：不渲染主页链接", () => {
    render(
      <SettingsShell username={null}>
        <p>内容</p>
      </SettingsShell>,
    );
    expect(screen.queryByRole("link", { name: /查看我的主页/ })).not.toBeInTheDocument();
  });
});

describe("SettingsCard", () => {
  it("标题、说明、右侧附加内容", () => {
    render(
      <SettingsCard title="关注领域" description="最多 10 个" aside="2 / 10">
        <p>正文</p>
      </SettingsCard>,
    );
    expect(screen.getByRole("heading", { level: 2, name: "关注领域" })).toBeInTheDocument();
    expect(screen.getByText("最多 10 个")).toBeInTheDocument();
    expect(screen.getByText("2 / 10")).toBeInTheDocument();
  });

  it("危险区：标题用危险色", () => {
    render(
      <SettingsCard title="注销账号" variant="danger">
        <p>正文</p>
      </SettingsCard>,
    );
    expect(screen.getByRole("heading", { name: "注销账号" })).toHaveClass("text-status-danger");
  });
});
