import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ForgotPasswordForm } from "@/components/auth/ForgotPasswordForm";
import { requestPasswordReset } from "@/lib/identity/token-actions";
import { useAuthHandoffStore } from "@/store/auth-handoff";

vi.mock("@/lib/identity/token-actions", () => ({ requestPasswordReset: vi.fn() }));

const NOW = Date.parse("2026-10-23T08:00:00Z");

beforeEach(() => {
  vi.useFakeTimers({
    toFake: ["setInterval", "clearInterval", "setTimeout", "clearTimeout", "Date"],
    shouldAdvanceTime: true,
  });
  vi.setSystemTime(NOW);
});

afterEach(() => {
  useAuthHandoffStore.getState().take();
  vi.useRealTimers();
  // 重置而不只是清空：一条用例中途失败时，没用掉的 mockResolvedValueOnce 不能漏到下一条
  vi.resetAllMocks();
});

function renderForm() {
  render(<ForgotPasswordForm next="/papers" />);
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
}

async function send(user: ReturnType<typeof userEvent.setup>, email: string) {
  const input = screen.getByLabelText("邮箱");
  await user.clear(input);
  if (email) await user.type(input, email);
  await user.click(screen.getByRole("button", { name: "发送重设邮件" }));
}

describe("ForgotPasswordForm", () => {
  it("输入态：标题、说明、预填登录页带来的邮箱并聚焦；返回与登录都带 next", () => {
    useAuthHandoffStore.getState().put({ email: "reader@example.com", notice: null });
    renderForm();
    expect(screen.getByRole("heading", { level: 1, name: "找回密码" })).toBeInTheDocument();
    expect(
      screen.getByText("输入注册时用的邮箱，我们会发一封重设密码的邮件。"),
    ).toBeInTheDocument();
    const input = screen.getByLabelText("邮箱");
    expect(input).toHaveValue("reader@example.com");
    // 读一次就清空
    expect(useAuthHandoffStore.getState().take()).toEqual({ email: null, notice: null });
    expect(input).toHaveFocus();
    expect(screen.getByRole("link", { name: "返回" })).toHaveAttribute(
      "href",
      "/login?next=%2Fpapers",
    );
    expect(screen.getByRole("link", { name: "登录" })).toHaveAttribute(
      "href",
      "/login?next=%2Fpapers",
    );
  });

  it("空邮箱：请输入邮箱，不请求", async () => {
    const user = renderForm();
    await send(user, "");
    expect(screen.getByText("请输入邮箱")).toBeInTheDocument();
    expect(requestPasswordReset).not.toHaveBeenCalled();
  });

  it("发送中：按钮显示发送中…", async () => {
    vi.mocked(requestPasswordReset).mockReturnValue(new Promise(() => {}));
    const user = renderForm();
    await send(user, "a@b.com");
    expect(screen.getByRole("button", { name: "发送中…" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("已发送：换态、写出邮箱、倒计时，到点可以重新发送", async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue({
      ok: true,
      email: "a@b.com",
      nextRequestAt: "2026-10-23T08:00:52Z",
    });
    const user = renderForm();
    await send(user, "a@b.com");
    expect(screen.getByRole("heading", { level: 1, name: "查看你的邮箱" })).toBeInTheDocument();
    expect(
      screen.getByText(
        (_, element) =>
          element?.tagName === "P" &&
          element.textContent ===
            "如果 a@b.com 注册过 Patra，几分钟内会收到一封邮件。链接 1 小时内有效。",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("没收到？先看看垃圾邮件。")).toBeInTheDocument();
    // 假时钟随真实时间推进（shouldAdvanceTime），机器忙时可能已过去一两秒
    expect(screen.getByText(/^(5[0-2]) 秒后可重新发送$/)).toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(53_000);
    });
    expect(await screen.findByRole("button", { name: "重新发送" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "返回登录" })).toHaveAttribute(
      "href",
      "/login?next=%2Fpapers",
    );
  });

  it("换一个邮箱：回到输入态，保留输入", async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue({
      ok: true,
      email: "a@b.com",
      nextRequestAt: "2026-10-23T08:01:00Z",
    });
    const user = renderForm();
    await send(user, "a@b.com");
    await user.click(screen.getByRole("button", { name: "换一个邮箱" }));
    expect(screen.getByLabelText("邮箱")).toHaveValue("a@b.com");
  });

  it("429（输入态）：提示条按分钟说", async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: 125,
    });
    const user = renderForm();
    await send(user, "a@b.com");
    expect(screen.getByRole("alert")).toHaveTextContent("发送太频繁，请 3 分钟后再试");
  });

  it("429（已发送态重新发送）：替换倒计时那一行", async () => {
    vi.mocked(requestPasswordReset)
      .mockResolvedValueOnce({ ok: true, email: "a@b.com", nextRequestAt: "2026-10-23T08:00:01Z" })
      .mockResolvedValueOnce({ ok: false, reason: "throttled", retryAfterSeconds: 30 });
    const user = renderForm();
    await send(user, "a@b.com");
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2_000);
    });
    await user.click(await screen.findByRole("button", { name: "重新发送" }));
    expect(screen.getByText(/^发送太频繁，请 (2[89]|30) 秒后再试$/)).toBeInTheDocument();
  });

  it("服务错误：没有发出去，请稍后再试", async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue({ ok: false, reason: "unavailable" });
    const user = renderForm();
    await send(user, "a@b.com");
    expect(screen.getByRole("alert")).toHaveTextContent("没有发出去，请稍后再试");
  });
});
