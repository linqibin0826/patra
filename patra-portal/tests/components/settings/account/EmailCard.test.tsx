import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { renderToString } from "react-dom/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { EmailCard } from "@/components/settings/account/EmailCard";
import type { CurrentUser } from "@/lib/auth/types";
import { cancelEmailChange, requestEmailChange } from "@/lib/identity/account-actions";
import type { PendingEmailChange } from "@/lib/identity/account-schema";

const router = { replace: vi.fn(), refresh: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/identity/account-actions", () => ({
  requestEmailChange: vi.fn(),
  cancelEmailChange: vi.fn(),
}));
const withReauth = vi.fn(async (call: () => Promise<unknown>) => call());
vi.mock("@/lib/reauth/use-reauth", () => ({ useReauth: () => ({ withReauth }) }));
vi.mock("@/components/portal/ResendVerificationButton", () => ({
  ResendVerificationButton: () => <button type="button">重新发送</button>,
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { toast } from "sonner";

const NOW = Date.parse("2026-10-23T08:00:00Z");
const PENDING: PendingEmailChange = {
  newEmail: "new@example.com",
  expiresAt: "2026-10-23T08:42:00Z",
};

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout", "Date"], shouldAdvanceTime: true });
  vi.setSystemTime(NOW);
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

function renderCard(user: Partial<CurrentUser> = {}, pending: PendingEmailChange | null = null) {
  const client = new QueryClient();
  render(
    <QueryClientProvider client={client}>
      <EmailCard user={{ ...USER, ...user }} pending={pending} />
    </QueryClientProvider>,
  );
}

/// 展开改邮箱表单，填入新邮箱并提交
async function submitNewEmail(value: string) {
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  await user.click(screen.getByRole("button", { name: "更改邮箱" }));
  const input = screen.getByLabelText("新邮箱");
  if (value) await user.type(input, value);
  await user.click(screen.getByRole("button", { name: "发送确认邮件" }));
  return user;
}

describe("EmailCard", () => {
  it("锚点 #email；已验证显示已验证胶囊，没有重发", () => {
    renderCard();
    expect(screen.getByRole("region", { name: "邮箱" })).toHaveAttribute("id", "email");
    expect(screen.getByText("chen.yu@example.com")).toBeInTheDocument();
    expect(screen.getByText("已验证")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "重新发送" })).not.toBeInTheDocument();
  });

  it("未验证：未验证胶囊、有效期说明与重新发送", () => {
    renderCard({ emailVerified: false });
    expect(screen.getByText("未验证")).toBeInTheDocument();
    expect(screen.getByText(/验证邮件已发到这个地址，24 小时内有效。/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新发送" })).toBeInTheDocument();
  });

  it("空邮箱：请输入邮箱，不请求", async () => {
    renderCard();
    await submitNewEmail("");
    expect(screen.getByText("请输入邮箱")).toBeInTheDocument();
    expect(requestEmailChange).not.toHaveBeenCalled();
  });

  it("与当前相同（大小写不同）：新邮箱和现在的一样", async () => {
    renderCard();
    await submitNewEmail("CHEN.YU@example.com");
    expect(screen.getByText("新邮箱和现在的一样")).toBeInTheDocument();
    expect(requestEmailChange).not.toHaveBeenCalled();
  });

  it("成功：经再认证包装提交，显示待确认块，更改邮箱禁用", async () => {
    vi.mocked(requestEmailChange).mockResolvedValue({ ok: true, pending: PENDING });
    renderCard();
    await submitNewEmail("new@example.com");
    expect(withReauth).toHaveBeenCalledTimes(1);
    expect(requestEmailChange).toHaveBeenCalledWith("new@example.com");
    expect(await screen.findByText(/正在等待/)).toHaveTextContent(
      "正在等待 new@example.com 确认 · 16:42 前有效",
    );
    expect(screen.getByRole("button", { name: "更改邮箱" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.queryByLabelText("新邮箱")).not.toBeInTheDocument();
  });

  it("在再认证里取消：不显示任何错误，表单还在", async () => {
    vi.mocked(requestEmailChange).mockResolvedValue({ ok: false, reason: "reauth-required" });
    withReauth.mockResolvedValueOnce({ ok: false, reason: "cancelled" });
    renderCard();
    await submitNewEmail("new@example.com");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByLabelText("新邮箱")).toBeInTheDocument();
  });

  it.each([
    [{ ok: false, reason: "taken" } as const, "这个邮箱已被其他账号使用"],
    [{ ok: false, reason: "invalid", code: "INVALID_FORMAT" } as const, "邮箱格式不正确"],
  ])("%j：字段下方提示", async (result, message) => {
    vi.mocked(requestEmailChange).mockResolvedValue(result);
    renderCard();
    await submitNewEmail("used@example.com");
    expect(await screen.findByText(message)).toBeInTheDocument();
  });

  it("已有待确认：提示条，并刷新页面拉回待确认块", async () => {
    vi.mocked(requestEmailChange).mockResolvedValue({ ok: false, reason: "pending-exists" });
    renderCard();
    await submitNewEmail("new@example.com");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "已有一个待确认的更改，请先取消或等它过期",
    );
    expect(router.refresh).toHaveBeenCalled();
  });

  it("429：今天更改邮箱的次数已用完", async () => {
    vi.mocked(requestEmailChange).mockResolvedValue({ ok: false, reason: "throttled" });
    renderCard();
    await submitNewEmail("new@example.com");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "今天更改邮箱的次数已用完，请明天再试",
    );
  });

  it("页面带着待确认：显示块；取消更改成功后块消失并提示", async () => {
    vi.mocked(cancelEmailChange).mockResolvedValue({ ok: true });
    renderCard({}, PENDING);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.click(screen.getByRole("button", { name: "取消更改" }));
    expect(screen.queryByText(/正在等待/)).not.toBeInTheDocument();
    expect(toast.success).toHaveBeenCalledWith("已取消更改邮箱");
    expect(screen.getByRole("button", { name: "更改邮箱" })).not.toHaveAttribute("aria-disabled");
  });

  it("取消更改失败：卡片提示条", async () => {
    vi.mocked(cancelEmailChange).mockResolvedValue({ ok: false, reason: "unavailable" });
    renderCard({}, PENDING);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.click(screen.getByRole("button", { name: "取消更改" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("没有保存成功，请稍后再试");
  });

  it("已过期的待确认不显示", () => {
    renderCard({}, { newEmail: "new@example.com", expiresAt: "2026-10-23T07:00:00Z" });
    expect(screen.queryByText(/正在等待/)).not.toBeInTheDocument();
  });

  it("首帧只看服务端给的数据，不看当前时间（服务端渲染与水合一致），挂载后再按时间收起", () => {
    const expired = { newEmail: "new@example.com", expiresAt: "2026-10-23T07:00:00Z" };
    const html = renderToString(
      <QueryClientProvider client={new QueryClient()}>
        <EmailCard user={USER} pending={expired} />
      </QueryClientProvider>,
    );
    expect(html).toContain("正在等待");
    renderCard({}, expired);
    expect(screen.queryByText(/正在等待/)).not.toBeInTheDocument();
  });

  it("hides_pending_block_when_it_expires_while_open", () => {
    renderCard({}, PENDING);
    expect(screen.getByText(/正在等待/)).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(42 * 60_000 + 1_000);
    });
    expect(screen.queryByText(/正在等待/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "更改邮箱" })).not.toHaveAttribute("aria-disabled");
  });

  it("冷静期：去取消注销页", async () => {
    vi.mocked(requestEmailChange).mockResolvedValue({ ok: false, reason: "pending-deletion" });
    renderCard();
    await submitNewEmail("new@example.com");
    expect(router.replace).toHaveBeenCalledWith(
      "/account/deletion-pending?next=%2Fsettings%2Faccount",
    );
  });
});
