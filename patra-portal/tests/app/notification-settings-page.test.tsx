import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { USER } from "@tests/fixtures/user";
import type { ReactElement } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";

vi.mock("next/navigation", () => ({
  redirect: vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT:${url}`);
  }),
  useRouter: () => ({ replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/settings/notifications",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/auth/require-user", () => ({ requireUser: vi.fn() }));
vi.mock("@/lib/notification/preferences", () => ({ fetchMyNotificationPreferences: vi.fn() }));
vi.mock("@/lib/notification/preference-actions", () => ({ saveNotificationMode: vi.fn() }));

import NotificationSettingsPage, { metadata } from "@/app/settings/notifications/page";
import { requireUser } from "@/lib/auth/require-user";
import { fetchMyNotificationPreferences } from "@/lib/notification/preferences";

afterEach(() => vi.clearAllMocks());

async function renderPage() {
  const page = (await NotificationSettingsPage()) as ReactElement;
  render(<QueryClientProvider client={new QueryClient()}>{page}</QueryClientProvider>);
}

describe("/settings/notifications", () => {
  it("守卫用这一页的路径；标题是通知 · 设置 · Patra；取到偏好时出表单", async () => {
    vi.mocked(requireUser).mockResolvedValue(USER);
    vi.mocked(fetchMyNotificationPreferences).mockResolvedValue({
      reply: "DIGEST",
      mention: "INSTANT",
    });
    await renderPage();
    expect(requireUser).toHaveBeenCalledWith("/settings/notifications");
    expect(metadata).toMatchObject({ title: "通知 · 设置 · Patra" });
    expect(screen.getByRole("radiogroup", { name: "「回复我的」提醒邮件" })).toBeInTheDocument();
    expect(screen.getByText("chen.yu@example.com")).toBeInTheDocument();
    expect(screen.queryByText(/你的邮箱还没验证/)).not.toBeInTheDocument();
  });

  it("邮箱未验证：顶部提示与重新发送验证邮件", async () => {
    vi.mocked(requireUser).mockResolvedValue({ ...USER, emailVerified: false });
    vi.mocked(fetchMyNotificationPreferences).mockResolvedValue({
      reply: "INSTANT",
      mention: "INSTANT",
    });
    await renderPage();
    expect(
      screen.getByText("你的邮箱还没验证：验证之前，不论下面怎么设置，都不会给你发提醒邮件。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新发送验证邮件" })).toBeInTheDocument();
  });

  it("通知服务不可用：只有「暂时打不开」卡片，没有表单与安全说明", async () => {
    vi.mocked(requireUser).mockResolvedValue({ ...USER, emailVerified: false });
    vi.mocked(fetchMyNotificationPreferences).mockResolvedValue("unavailable");
    await renderPage();
    expect(
      screen.getByRole("heading", { level: 2, name: "通知设置暂时打不开" }),
    ).toBeInTheDocument();
    expect(screen.getByText("请稍后再试。账号安全邮件不受影响。")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重试" })).toBeInTheDocument();
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
    expect(screen.queryByText("账号安全邮件总会发送")).not.toBeInTheDocument();
    // 未验证提示照常
    expect(screen.getByText(/你的邮箱还没验证/)).toBeInTheDocument();
  });

  it("读偏好时会话已失效：去登录页", async () => {
    vi.mocked(requireUser).mockResolvedValue(USER);
    vi.mocked(fetchMyNotificationPreferences).mockResolvedValue("signed-out");
    await expect(NotificationSettingsPage()).rejects.toThrow(
      "NEXT_REDIRECT:/login?next=%2Fsettings%2Fnotifications",
    );
  });
});
