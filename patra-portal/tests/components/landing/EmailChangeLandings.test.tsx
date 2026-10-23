import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ConfirmEmailChangeLanding } from "@/components/landing/ConfirmEmailChangeLanding";
import { RevertEmailChangeLanding } from "@/components/landing/RevertEmailChangeLanding";
import type { CurrentUser } from "@/lib/auth/types";
import { confirmEmailChange, revertEmailChange } from "@/lib/identity/token-actions";
import { queryKeys } from "@/lib/query-keys";

vi.mock("@/lib/identity/token-actions", () => ({
  confirmEmailChange: vi.fn(),
  revertEmailChange: vi.fn(),
}));

const TOKEN = "t".repeat(43);

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

/// 打开带令牌的落地页；`me` 是当前用户（null 未登录）
function renderPage(path: string, page: ReactNode, me: CurrentUser | null) {
  window.history.replaceState(null, "", `${path}#token=${TOKEN}`);
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => Response.json({ user: me })),
  );
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(queryKeys.currentUser, me);
  const invalidate = vi.spyOn(client, "invalidateQueries");
  render(<QueryClientProvider client={client}>{page}</QueryClientProvider>);
  return { user: userEvent.setup(), invalidate };
}

describe("ConfirmEmailChangeLanding", () => {
  const open = (me: CurrentUser | null) =>
    renderPage("/confirm-email-change", <ConfirmEmailChangeLanding />, me);

  it("就绪态", async () => {
    open(USER);
    expect(
      await screen.findByRole("heading", { level: 1, name: "确认新邮箱" }),
    ).toBeInTheDocument();
    expect(screen.getByText("确认后，这个邮箱会成为你登录 Patra 的邮箱。")).toBeInTheDocument();
  });

  it("成功：以后用新邮箱登录，继续浏览，刷新当前用户", async () => {
    vi.mocked(confirmEmailChange).mockResolvedValue({ ok: true, email: "new@example.com" });
    const { user, invalidate } = open(USER);
    await user.click(await screen.findByRole("button", { name: "确认更改" }));
    expect(
      await screen.findByRole("heading", { level: 1, name: "邮箱已更改" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("以后请用 new@example.com 登录。原来的邮箱会收到一封通知。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "继续浏览" })).toHaveAttribute("href", "/");
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.currentUser });
  });

  it("地址已被注册：冲突屏，去账号设置", async () => {
    vi.mocked(confirmEmailChange).mockResolvedValue({ ok: false, reason: "taken" });
    const { user } = open(USER);
    await user.click(await screen.findByRole("button", { name: "确认更改" }));
    expect(
      await screen.findByRole("heading", { level: 1, name: "这个邮箱已被注册" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("在你确认之前，这个地址被另一个账号注册了，无法使用。你的登录邮箱没有变。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "去账号设置" })).toHaveAttribute(
      "href",
      "/settings/account",
    );
  });

  it.each([
    ["已登录", USER, "去账号设置", "/settings/account"],
    ["未登录", null, "登录", "/login"],
  ])("链接无效 + %s：出口是%s", async (_label, me, name, href) => {
    vi.mocked(confirmEmailChange).mockResolvedValue({ ok: false, reason: "invalid" });
    const { user } = open(me);
    await user.click(await screen.findByRole("button", { name: "确认更改" }));
    expect(
      await screen.findByText("确认链接 1 小时内有效。可以回到设置重新发起更改。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name })).toHaveAttribute("href", href);
  });
});

describe("RevertEmailChangeLanding", () => {
  const open = () => renderPage("/revert-email-change", <RevertEmailChangeLanding />, null);

  it("就绪态：三条后果、危险主按钮", async () => {
    open();
    expect(
      await screen.findByRole("heading", { level: 1, name: "撤销邮箱更改" }),
    ).toBeInTheDocument();
    expect(screen.getByText("所有设备退出登录，原密码作废")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "撤销更改并锁定账号" })).toBeInTheDocument();
  });

  it("成功：写出改回的邮箱，没有按钮，刷新当前用户", async () => {
    vi.mocked(revertEmailChange).mockResolvedValue({ ok: true, email: "old@example.com" });
    const { user, invalidate } = open();
    await user.click(await screen.findByRole("button", { name: "撤销更改并锁定账号" }));
    expect(
      await screen.findByRole("heading", { level: 1, name: "邮箱已改回" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/登录邮箱已改回 old@example.com，所有设备已退出登录/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.currentUser });
  });

  it("旧地址已被注册：无法改回原邮箱", async () => {
    vi.mocked(revertEmailChange).mockResolvedValue({ ok: false, reason: "taken" });
    const { user } = open();
    await user.click(await screen.findByRole("button", { name: "撤销更改并锁定账号" }));
    expect(
      await screen.findByRole("heading", { level: 1, name: "无法改回原邮箱" }),
    ).toBeInTheDocument();
    expect(screen.getByText("原邮箱已被另一个账号注册。")).toBeInTheDocument();
  });

  it("链接无效：撤销链接 7 天内有效", async () => {
    vi.mocked(revertEmailChange).mockResolvedValue({ ok: false, reason: "invalid" });
    const { user } = open();
    await user.click(await screen.findByRole("button", { name: "撤销更改并锁定账号" }));
    expect(await screen.findByText("撤销链接 7 天内有效。")).toBeInTheDocument();
  });
});
