import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { NotificationSettingsForm } from "@/components/settings/notifications/NotificationSettingsForm";
import { type SaveModeResult, saveNotificationMode } from "@/lib/notification/preference-actions";
import type { NotificationPreferences } from "@/lib/notification/schema";

const router = { replace: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/notification/preference-actions", () => ({ saveNotificationMode: vi.fn() }));

const DEFAULTS: NotificationPreferences = { reply: "INSTANT", mention: "INSTANT" };

function renderForm(initial: NotificationPreferences = DEFAULTS) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <NotificationSettingsForm initial={initial} email="chen.yu@example.com" />
    </QueryClientProvider>,
  );
  return userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
}

/// 一个由测试决定何时、以什么结果回来的请求
function deferred() {
  let answer: (value: SaveModeResult) => void = () => {};
  const promise = new Promise<SaveModeResult>((resolve) => {
    answer = resolve;
  });
  return { promise, answer };
}

const group = (name: "「回复我的」提醒邮件" | "「@ 我的」提醒邮件") =>
  within(screen.getByRole("radiogroup", { name }));
const reply = () => group("「回复我的」提醒邮件");
const mention = () => group("「@ 我的」提醒邮件");
/// 单选的可及名以档位名开头
const option = (scope: ReturnType<typeof group>, title: "关" | "离站时逐条" | "每日汇总") =>
  scope.getByRole("radio", { name: new RegExp(`^${title}`) });
/// 卡片：标题所在的 section
const card = (title: string) =>
  within(screen.getByRole("heading", { level: 2, name: title }).closest("section") as HTMLElement);
const ok = (preferences: NotificationPreferences): SaveModeResult => ({ ok: true, preferences });

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.mocked(saveNotificationMode).mockImplementation(async (category, mode) =>
    ok({ ...DEFAULTS, [category === "REPLY" ? "reply" : "mention"]: mode }),
  );
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

describe("NotificationSettingsForm", () => {
  it("两类三档、默认标记、收件地址、有帮助与安全邮件说明", () => {
    renderForm({ reply: "INSTANT", mention: "OFF" });
    expect(option(reply(), "离站时逐条")).toBeChecked();
    expect(option(mention(), "关")).toBeChecked();
    expect(option(reply(), "离站时逐条")).toHaveAccessibleName(
      /^离站时逐条\s*默认 你不在 Patra 上时，提醒 10 分钟后仍未读就发一封/,
    );
    expect(option(reply(), "关")).toHaveAccessibleName("关 只在站内提醒，不发邮件。");
    expect(option(reply(), "每日汇总")).toHaveAccessibleName(
      "每日汇总 每天早上 8:00（北京时间）发一封，列出过去 24 小时里还没读的；都读过了就不发。",
    );
    expect(screen.getByText("有人回复你的评论、评论你的投稿经验时")).toBeInTheDocument();
    expect(screen.getByText("有人在评论里 @ 你时")).toBeInTheDocument();
    expect(screen.getByText("提醒邮件发到")).toBeInTheDocument();
    expect(screen.getByText("chen.yu@example.com")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "更改邮箱" })).toHaveAttribute(
      "href",
      "/settings/account#email",
    );
    expect(card("有帮助").getByText("只在站内")).toBeInTheDocument();
    expect(card("有帮助").queryByRole("radio")).not.toBeInTheDocument();
    expect(screen.getByText("账号安全邮件总会发送")).toBeInTheDocument();
  });

  it("改一档立即保存这一类：这张卡「保存中…」→「已保存」，2 秒后消失；另一张卡无状态", async () => {
    const pending = deferred();
    vi.mocked(saveNotificationMode).mockReturnValueOnce(pending.promise);
    const user = renderForm();
    await user.click(option(mention(), "每日汇总"));
    expect(saveNotificationMode).toHaveBeenCalledWith("MENTION", "DIGEST");
    expect(option(mention(), "每日汇总")).toBeChecked();
    expect(card("@ 我的").getByText("保存中…")).toBeInTheDocument();
    expect(card("回复我的").queryByText("保存中…")).not.toBeInTheDocument();
    await act(async () => pending.answer(ok({ reply: "INSTANT", mention: "DIGEST" })));
    expect(card("@ 我的").getByText("已保存")).toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2_100);
    });
    expect(screen.queryByText("已保存")).not.toBeInTheDocument();
  });

  it("保存失败：这张卡回到原值，提示重试", async () => {
    vi.mocked(saveNotificationMode).mockResolvedValueOnce({ ok: false, reason: "unavailable" });
    const user = renderForm();
    await user.click(option(reply(), "关"));
    expect(await card("回复我的").findByText("没有保存成功，请重试")).toBeInTheDocument();
    expect(option(reply(), "离站时逐条")).toBeChecked();
  });

  it("only_the_latest_change_of_a_category_settles_it", async () => {
    const first = deferred();
    const second = deferred();
    const third = deferred();
    vi.mocked(saveNotificationMode)
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
      .mockReturnValueOnce(third.promise);
    const user = renderForm();
    await user.click(option(reply(), "每日汇总"));
    await user.click(option(reply(), "关"));
    await user.click(option(reply(), "离站时逐条"));
    // 排队：第一个回来之前只发了一个请求
    expect(saveNotificationMode).toHaveBeenCalledTimes(1);
    await act(async () => first.answer(ok({ reply: "DIGEST", mention: "INSTANT" })));
    await waitFor(() => expect(saveNotificationMode).toHaveBeenCalledTimes(2));
    await act(async () => second.answer({ ok: false, reason: "unavailable" }));
    await waitFor(() => expect(saveNotificationMode).toHaveBeenCalledTimes(3));
    // 还有在途的请求：界面停在最后一次点的值，仍是保存中
    expect(option(reply(), "离站时逐条")).toBeChecked();
    expect(card("回复我的").getByText("保存中…")).toBeInTheDocument();
    await act(async () => third.answer({ ok: false, reason: "unavailable" }));
    // 最后一次也失败：回到服务端最后确认的「每日汇总」
    await waitFor(() => expect(option(reply(), "每日汇总")).toBeChecked());
    expect(card("回复我的").getByText("没有保存成功，请重试")).toBeInTheDocument();
  });

  it("两类排一条队：第二个请求在第一个回来之后才发，两张卡各自显示已保存", async () => {
    const first = deferred();
    vi.mocked(saveNotificationMode).mockReturnValueOnce(first.promise);
    const user = renderForm();
    await user.click(option(reply(), "关"));
    await user.click(option(mention(), "每日汇总"));
    expect(saveNotificationMode).toHaveBeenCalledTimes(1);
    await act(async () => first.answer(ok({ reply: "OFF", mention: "INSTANT" })));
    await waitFor(() => expect(saveNotificationMode).toHaveBeenLastCalledWith("MENTION", "DIGEST"));
    expect(await card("回复我的").findByText("已保存")).toBeInTheDocument();
    expect(await card("@ 我的").findByText("已保存")).toBeInTheDocument();
    // 第一个响应里 mention 还是 INSTANT，但「@ 我的」排着队，不被覆盖
    expect(option(mention(), "每日汇总")).toBeChecked();
  });

  it("响应里的另一类：没有排队的改动时一并采用服务端的值", async () => {
    vi.mocked(saveNotificationMode).mockResolvedValueOnce(ok({ reply: "OFF", mention: "OFF" }));
    const user = renderForm();
    await user.click(option(reply(), "关"));
    // 「@ 我的」刚在邮件里被退订：保存「回复我的」的响应带回了它的新档位
    await waitFor(() => expect(option(mention(), "关")).toBeChecked());
    expect(card("@ 我的").queryByText("已保存")).not.toBeInTheDocument();
  });

  it("会话失效：去登录页，回来还是这一页", async () => {
    vi.mocked(saveNotificationMode).mockResolvedValueOnce({ ok: false, reason: "signed-out" });
    const user = renderForm();
    await user.click(option(reply(), "关"));
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith("/login?next=%2Fsettings%2Fnotifications"),
    );
  });
});
