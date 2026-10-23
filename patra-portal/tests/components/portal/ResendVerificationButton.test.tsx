import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { USER } from "@tests/fixtures/user";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ResendVerificationButton } from "@/components/portal/ResendVerificationButton";
import { resendVerificationEmail } from "@/lib/identity/verification-actions";
import { queryKeys } from "@/lib/query-keys";
import { useVerificationResendStore } from "@/store/verification-resend";

const router = { replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/papers",
  useSearchParams: () => new URLSearchParams("q=a"),
}));
vi.mock("@/lib/identity/verification-actions", () => ({ resendVerificationEmail: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { toast } from "sonner";

const NOW = new Date("2026-10-23T08:00:00Z").getTime();

function renderButton() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(queryKeys.currentUser, { ...USER, emailVerified: false });
  render(
    <QueryClientProvider client={client}>
      <ResendVerificationButton />
    </QueryClientProvider>,
  );
  return client;
}

/// 点「重新发送」并等 action 回来
async function clickResend() {
  await act(async () => {
    fireEvent.click(screen.getByRole("button", { name: "重新发送" }));
  });
}

beforeEach(() => {
  vi.useFakeTimers({
    toFake: ["setInterval", "clearInterval", "setTimeout", "clearTimeout", "Date"],
  });
  vi.setSystemTime(NOW);
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
  useVerificationResendStore.getState().reset();
});

describe("ResendVerificationButton", () => {
  it("发送中：按钮换成「发送中…」并禁用", async () => {
    vi.mocked(resendVerificationEmail).mockReturnValue(new Promise(() => {}));
    renderButton();
    await clickResend();
    expect(screen.getByRole("button", { name: "发送中…" })).toBeDisabled();
  });

  it("成功：提示已发送，按 nextResendAt 倒计时，到 0 恢复「重新发送」并播报", async () => {
    vi.mocked(resendVerificationEmail).mockResolvedValue({
      ok: true,
      nextResendAt: new Date(NOW + 60_000).toISOString(),
    });
    renderButton();
    await clickResend();
    expect(toast.success).toHaveBeenCalledWith("验证邮件已发送");
    expect(screen.getByText("已发送 · 60 秒后可重发")).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(1_000));
    expect(screen.getByText("已发送 · 59 秒后可重发")).toBeInTheDocument();
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
    act(() => vi.advanceTimersByTime(59_000));
    expect(screen.getByRole("button", { name: "重新发送" })).toBeEnabled();
    expect(screen.getByRole("status")).toHaveTextContent("可以重新发送了");
  });

  it("倒计时结束后不再每秒走表", async () => {
    vi.mocked(resendVerificationEmail).mockResolvedValue({
      ok: true,
      nextResendAt: new Date(NOW + 3_000).toISOString(),
    });
    renderButton();
    // QueryClient 自己有回收缓存的定时器，只看按钮带来的
    const before = vi.getTimerCount();
    await clickResend();
    expect(vi.getTimerCount()).toBe(before + 1);
    act(() => vi.advanceTimersByTime(3_000));
    expect(screen.getByRole("button", { name: "重新发送" })).toBeEnabled();
    expect(vi.getTimerCount()).toBe(before);
  });

  it.each([
    [125, "发送太频繁，请 3 分钟后再试"],
    [45, "发送太频繁，请 45 秒后再试"],
  ])("限流 %i 秒：%s", async (seconds, text) => {
    vi.mocked(resendVerificationEmail).mockResolvedValue({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: seconds,
    });
    renderButton();
    await clickResend();
    expect(screen.getByText(text)).toBeInTheDocument();
  });

  it("已验证：刷新当前用户，提示「邮箱已验证」", async () => {
    vi.mocked(resendVerificationEmail).mockResolvedValue({ ok: false, reason: "already-verified" });
    const client = renderButton();
    const invalidate = vi.spyOn(client, "invalidateQueries");
    await clickResend();
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.currentUser });
    expect(toast.success).toHaveBeenCalledWith("邮箱已验证");
  });

  it("服务出错：错误提示，按钮恢复", async () => {
    vi.mocked(resendVerificationEmail).mockResolvedValue({ ok: false, reason: "unavailable" });
    renderButton();
    await clickResend();
    expect(toast.error).toHaveBeenCalledWith("没有发出去，请稍后再试");
    expect(screen.getByRole("button", { name: "重新发送" })).toBeEnabled();
  });

  it("action 的 promise 被拒绝：同服务出错", async () => {
    vi.mocked(resendVerificationEmail).mockRejectedValue(new Error("network"));
    renderButton();
    await clickResend();
    expect(toast.error).toHaveBeenCalledWith("没有发出去，请稍后再试");
  });

  it("冷静期：去取消注销页，带上当前地址", async () => {
    vi.mocked(resendVerificationEmail).mockResolvedValue({ ok: false, reason: "pending-deletion" });
    renderButton();
    await clickResend();
    expect(router.replace).toHaveBeenCalledWith("/account/deletion-pending?next=%2Fpapers%3Fq%3Da");
  });

  it("会话失效：当前用户置空，去登录页", async () => {
    vi.mocked(resendVerificationEmail).mockResolvedValue({ ok: false, reason: "signed-out" });
    const client = renderButton();
    await clickResend();
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull();
    expect(router.replace).toHaveBeenCalledWith("/login?next=%2Fpapers%3Fq%3Da");
  });

  it("注册后 60 秒内（store 里已有冷却）：一出现就是倒计时", () => {
    useVerificationResendStore.getState().startCooldown(NOW + 42_000);
    renderButton();
    expect(screen.getByText("已发送 · 42 秒后可重发")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "重新发送" })).not.toBeInTheDocument();
  });
});

describe("ResendVerificationButton · 整宽按钮样式", () => {
  it("冷却中仍是按钮（不可点），文字是倒计时", () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(queryKeys.currentUser, { ...USER, emailVerified: false });
    useVerificationResendStore.getState().startCooldown(NOW + 52_000);
    render(
      <QueryClientProvider client={client}>
        <ResendVerificationButton variant="block" label="重新发送验证邮件" />
      </QueryClientProvider>,
    );
    expect(screen.getByRole("button", { name: "已发送 · 52 秒后可重发" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("发送中再点不会重复请求", async () => {
    vi.mocked(resendVerificationEmail).mockReturnValue(new Promise(() => {}));
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    client.setQueryData(queryKeys.currentUser, { ...USER, emailVerified: false });
    render(
      <QueryClientProvider client={client}>
        <ResendVerificationButton variant="block" label="重新发送验证邮件" />
      </QueryClientProvider>,
    );
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "重新发送验证邮件" }));
    });
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "发送中…" }));
    });
    expect(resendVerificationEmail).toHaveBeenCalledTimes(1);
  });
});
