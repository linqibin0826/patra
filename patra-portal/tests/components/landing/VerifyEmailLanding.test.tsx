import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { VerifyEmailLanding } from "@/components/landing/VerifyEmailLanding";
import type { CurrentUser } from "@/lib/auth/types";
import { verifyEmail } from "@/lib/identity/token-actions";

vi.mock("@/lib/identity/token-actions", () => ({ verifyEmail: vi.fn() }));
vi.mock("@/components/portal/ResendVerificationButton", () => ({
  ResendVerificationButton: ({ label }: { label?: string }) => (
    <button type="button">{label}</button>
  ),
}));

const TOKEN = "t".repeat(43);

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

/// 打开带令牌的验证页；`me` 是 `/api/auth/me` 每次回的用户（数组按次序回）
function renderPage(me: (CurrentUser | null)[]) {
  window.history.replaceState(null, "", `/verify-email#token=${TOKEN}`);
  const answers = [...me];
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => Response.json({ user: answers.length > 1 ? answers.shift() : answers[0] })),
  );
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <VerifyEmailLanding />
    </QueryClientProvider>,
  );
  return userEvent.setup();
}

const UNVERIFIED = { ...USER, emailVerified: false };

describe("VerifyEmailLanding", () => {
  it("就绪态：打开时不提交", async () => {
    renderPage([UNVERIFIED]);
    expect(
      await screen.findByRole("heading", { level: 1, name: "验证你的邮箱" }),
    ).toBeInTheDocument();
    expect(screen.getByText("点下面的按钮完成验证。")).toBeInTheDocument();
    expect(verifyEmail).not.toHaveBeenCalled();
  });

  it("成功且本机会话还在：其他设备已退出，继续浏览", async () => {
    vi.mocked(verifyEmail).mockResolvedValue({ ok: true, email: "chen.yu@example.com" });
    const user = renderPage([UNVERIFIED, USER]);
    await user.click(await screen.findByRole("button", { name: "验证邮箱" }));
    expect(
      await screen.findByRole("heading", { level: 1, name: "邮箱已验证" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("chen.yu@example.com 已验证。其他设备上的登录已退出。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "继续浏览" })).toHaveAttribute("href", "/");
  });

  it.each([
    ["未登录", null],
    ["登录的是别的账号", { ...USER, email: "other@example.com" }],
  ])("成功但%s：所有设备已退出，登录", async (_label, after) => {
    vi.mocked(verifyEmail).mockResolvedValue({ ok: true, email: "chen.yu@example.com" });
    const user = renderPage([null, after]);
    await user.click(await screen.findByRole("button", { name: "验证邮箱" }));
    expect(
      await screen.findByText("chen.yu@example.com 已验证。为了安全，所有设备都已退出登录。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "登录" })).toHaveAttribute("href", "/login");
  });

  it("无效 + 已登录未验证：重新发送验证邮件", async () => {
    vi.mocked(verifyEmail).mockResolvedValue({ ok: false, reason: "invalid" });
    const user = renderPage([UNVERIFIED]);
    await user.click(await screen.findByRole("button", { name: "验证邮箱" }));
    expect(
      await screen.findByRole("heading", { level: 1, name: "链接无效或已过期" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("验证链接 24 小时内有效，重新发送会让旧链接失效。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新发送验证邮件" })).toBeInTheDocument();
  });

  it("无效 + 已登录已验证：显示已经验证过了", async () => {
    vi.mocked(verifyEmail).mockResolvedValue({ ok: false, reason: "invalid" });
    const user = renderPage([USER]);
    await user.click(await screen.findByRole("button", { name: "验证邮箱" }));
    expect(
      await screen.findByRole("heading", { level: 1, name: "邮箱已验证" }),
    ).toBeInTheDocument();
    expect(screen.getByText("你的邮箱 chen.yu@example.com 已经验证过了。")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "继续浏览" })).toHaveAttribute("href", "/");
  });

  it("无效 + 未登录：登录后重新发送", async () => {
    vi.mocked(verifyEmail).mockResolvedValue({ ok: false, reason: "invalid" });
    const user = renderPage([null]);
    await user.click(await screen.findByRole("button", { name: "验证邮箱" }));
    expect(await screen.findByRole("link", { name: "登录后重新发送" })).toHaveAttribute(
      "href",
      "/login?next=%2Fsettings%2Faccount",
    );
  });
});
