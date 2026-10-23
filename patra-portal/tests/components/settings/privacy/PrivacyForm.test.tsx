import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PUBLIC_PRIVACY } from "@tests/fixtures/profile";
import { USER } from "@tests/fixtures/user";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { PrivacyForm } from "@/components/settings/privacy/PrivacyForm";
import type { CurrentUser } from "@/lib/auth/types";
import { type SavePrivacyResult, savePrivacy } from "@/lib/identity/profile-actions";
import type { PrivacySettings } from "@/lib/identity/profile-schema";

const router = { replace: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/identity/profile-actions", () => ({ savePrivacy: vi.fn() }));

function renderForm(user: CurrentUser = USER, initial: PrivacySettings = PUBLIC_PRIVACY) {
  const client = new QueryClient();
  render(
    <QueryClientProvider client={client}>
      <PrivacyForm initial={initial} user={user} />
    </QueryClientProvider>,
  );
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
}

/// 一个由测试决定何时、以什么结果回来的请求
function deferred() {
  let answer: (value: SavePrivacyResult) => void = () => {};
  const promise = new Promise<SavePrivacyResult>((resolve) => {
    answer = resolve;
  });
  return { promise, answer };
}

const radio = (name: string | RegExp) => screen.getByRole("radio", { name });
const ok = (settings: PrivacySettings): SavePrivacyResult => ({ ok: true, settings });

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.mocked(savePrivacy).mockImplementation(async (settings) => ok(settings));
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

describe("PrivacyForm", () => {
  it("三组设置与说明文案", () => {
    renderForm();
    expect(screen.getByRole("radiogroup", { name: "主页可见性" })).toBeInTheDocument();
    expect(radio(/^公开 任何人都能看到/)).toBeChecked();
    expect(radio(/^仅登录用户/)).toBeInTheDocument();
    expect(radio(/^仅自己 别人打开你的主页会看到「没有找到这个用户」/)).toBeInTheDocument();
    expect(screen.getByRole("radiogroup", { name: "专业背景可见性" })).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /允许搜索引擎收录主页/ })).not.toBeChecked();
    expect(screen.getByRole("link", { name: /查看我的主页/ })).toHaveAttribute(
      "href",
      "/u/chen-yu",
    );
  });

  it("改动立即保存三项，旁边「保存中…」→「已保存」，2 秒后消失", async () => {
    const pending = deferred();
    vi.mocked(savePrivacy).mockReturnValueOnce(pending.promise);
    const user = renderForm();
    await user.click(radio(/^仅登录用户/));
    expect(savePrivacy).toHaveBeenCalledWith({ ...PUBLIC_PRIVACY, profileVisibility: "SIGNED_IN" });
    expect(screen.getByText("保存中…")).toBeInTheDocument();
    await act(async () =>
      pending.answer(ok({ ...PUBLIC_PRIVACY, profileVisibility: "SIGNED_IN" })),
    );
    expect(screen.getByText("已保存")).toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2_100);
    });
    expect(screen.queryByText("已保存")).not.toBeInTheDocument();
  });

  it("保存失败：回到原值，提示重试", async () => {
    vi.mocked(savePrivacy).mockResolvedValueOnce({ ok: false, reason: "unavailable" });
    const user = renderForm();
    await user.click(screen.getByRole("switch", { name: /允许搜索引擎收录主页/ }));
    expect(await screen.findByText("没有保存成功，请重试")).toBeInTheDocument();
    expect(screen.getByRole("switch", { name: /允许搜索引擎收录主页/ })).not.toBeChecked();
  });

  it("rolls_back_to_last_confirmed_value_after_failure_in_a_burst", async () => {
    const first = deferred();
    const second = deferred();
    const third = deferred();
    vi.mocked(savePrivacy)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
      .mockReturnValueOnce(third.promise);
    const user = renderForm();
    await user.click(radio(/^仅登录用户/));
    await user.click(radio(/^仅自己 别人/));
    await user.click(radio(/^公开 任何人/));
    // 串行：第一个回来之前只发了一个请求
    expect(savePrivacy).toHaveBeenCalledTimes(1);
    await act(async () => first.answer(ok({ ...PUBLIC_PRIVACY, profileVisibility: "SIGNED_IN" })));
    await waitFor(() => expect(savePrivacy).toHaveBeenCalledTimes(2));
    await act(async () => second.answer({ ok: false, reason: "unavailable" }));
    await waitFor(() => expect(savePrivacy).toHaveBeenCalledTimes(3));
    // 还有在途的请求：界面先停在最后一次点的值
    expect(radio(/^公开 任何人/)).toBeChecked();
    await act(async () => third.answer({ ok: false, reason: "unavailable" }));
    // 最后一次也失败：回到服务端最后确认的「仅登录用户」
    await waitFor(() => expect(radio(/^仅登录用户/)).toBeChecked());
  });

  it("邮箱未验证：顶部说明不论怎么设都只有自己能看到", () => {
    renderForm({ ...USER, emailVerified: false });
    expect(
      screen.getByText("你的邮箱还没验证：验证之前，不论下面怎么设置，主页都只有你能看到。"),
    ).toBeInTheDocument();
  });

  it("主页不是公开时：开关下提示开启也不会被收录", () => {
    renderForm(USER, { ...PUBLIC_PRIVACY, profileVisibility: "SELF" });
    expect(screen.getByText("当前主页不是公开的，开启也不会被收录")).toBeInTheDocument();
  });

  it.each([
    ["pending-deletion", "/account/deletion-pending?next=%2Fsettings%2Fprivacy"],
    ["signed-out", "/login?next=%2Fsettings%2Fprivacy"],
  ] as const)("%s：跳走", async (reason, href) => {
    vi.mocked(savePrivacy).mockResolvedValueOnce({ ok: false, reason });
    const user = renderForm();
    await user.click(radio(/^仅登录用户/));
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith(href));
  });
});
