import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { renderDiscussion } from "@tests/support/discussion";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { HelpfulButton } from "@/components/helpful/HelpfulButton";
import * as helpfulCache from "@/lib/helpful/cache";
import { type HelpfulActionResult, setHelpful } from "@/lib/interaction/helpful-actions";
import { queryKeys } from "@/lib/query-keys";

vi.mock("@/lib/interaction/helpful-actions", () => ({ setHelpful: vi.fn() }));
vi.mock("@/components/portal/ResendVerificationButton", () => ({
  ResendVerificationButton: ({ label }: { label: string }) => (
    <button type="button">{label}</button>
  ),
}));

const action = vi.mocked(setHelpful);
const COMMENT = { type: "comment", id: "5" } as const;

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  window.history.replaceState(null, "", "/papers/1?sort=helpful");
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

function renderButton(props: Partial<Parameters<typeof HelpfulButton>[0]> = {}, user?: null) {
  const element = (next: Partial<Parameters<typeof HelpfulButton>[0]> = {}) => (
    <HelpfulButton
      target={COMMENT}
      helpful={false}
      count={3}
      own={false}
      variant="text"
      {...props}
      {...next}
    />
  );
  const view = renderDiscussion(element(), user === null ? { user: null } : {});
  return {
    ...view,
    update: (next: Partial<Parameters<typeof HelpfulButton>[0]>) => view.rerender(element(next)),
  };
}

describe("HelpfulButton 渲染", () => {
  it("0 不写数字", () => {
    renderButton({ count: 0 });
    const button = screen.getByRole("button", { name: "有帮助" });
    expect(button).toHaveAttribute("aria-pressed", "false");
  });

  it("有数时读屏名带人数", () => {
    renderButton({ count: 3 });
    expect(screen.getByRole("button", { name: "有帮助，3 人觉得有帮助" })).toBeVisible();
  });

  it("已点时 aria-pressed", () => {
    renderButton({ helpful: true });
    expect(screen.getByRole("button", { name: /^有帮助/ })).toHaveAttribute("aria-pressed", "true");
  });

  it("千分位", () => {
    renderButton({ count: 1204 });
    expect(screen.getByRole("button", { name: "有帮助，1,204 人觉得有帮助" })).toHaveTextContent(
      "1,204",
    );
  });

  it("自己的内容只有文字", () => {
    renderButton({ own: true, count: 3 });
    expect(screen.queryByRole("button", { name: /有帮助/ })).toBeNull();
    expect(screen.getByText("3 人觉得有帮助")).toBeVisible();
  });

  it("自己的内容为 0 时什么都不显示", () => {
    const { container } = renderButton({ own: true, count: 0 });
    expect(container).toBeEmptyDOMElement();
  });

  it("胶囊形状态相同", () => {
    renderButton({ variant: "pill", helpful: true, count: 5 });
    expect(screen.getByRole("button", { name: "有帮助，5 人觉得有帮助" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });
});

const clicker = () => userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
/// 点一下，再冲掉被假定时器推迟的 effect（React 的调度器也跑在假定时器上）。`shouldAdvanceTime` 让真实时间
/// 也推着假时钟走，计时边界断言留几百毫秒余量
const press = async (name: RegExp | string = /^有帮助/) => {
  await clicker().click(screen.getByRole("button", { name }));
  await act(() => vi.advanceTimersByTimeAsync(0));
};

describe("HelpfulButton 身份", () => {
  it("这次请求没有会话：去登录，不发请求", async () => {
    const { router } = renderButton({ helpful: null });
    await press();
    expect(router.push).toHaveBeenCalledWith(
      `/login?${new URLSearchParams({ next: "/papers/1?sort=helpful" })}`,
    );
    expect(action).not.toHaveBeenCalled();
  });

  it("当前用户已退出：去登录", async () => {
    const { router } = renderButton({ helpful: false }, null);
    await press();
    expect(router.push).toHaveBeenCalledTimes(1);
    expect(action).not.toHaveBeenCalled();
  });

  it("邮箱未验证：前端拦下，数字不变，琥珀提示 4 秒", async () => {
    const view = renderButton({ count: 3 });
    act(() => view.client.setQueryData(["current-user"], { ...USER, emailVerified: false }));
    await press();
    expect(action).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "有帮助，3 人觉得有帮助" })).toBeVisible();
    expect(screen.getByRole("status")).toHaveTextContent("验证邮箱后才能点有帮助");
    expect(screen.getByRole("button", { name: "重新发送验证邮件" })).toBeVisible();
    act(() => vi.advanceTimersByTime(4000));
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("焦点在重新发送上时提示不消失，离开后再计 4 秒", async () => {
    const view = renderButton();
    act(() => view.client.setQueryData(["current-user"], { ...USER, emailVerified: false }));
    await press();
    act(() => screen.getByRole("button", { name: "重新发送验证邮件" }).focus());
    act(() => vi.advanceTimersByTime(4000));
    expect(screen.getByRole("status")).toHaveTextContent("验证邮箱后才能点有帮助");
    act(() => screen.getByRole("button", { name: /^有帮助/ }).focus());
    act(() => vi.advanceTimersByTime(3500));
    expect(screen.getByRole("status")).toHaveTextContent("验证邮箱后才能点有帮助");
    act(() => vi.advanceTimersByTime(600));
    expect(screen.queryByRole("status")).toBeNull();
  });
});

function deferred() {
  let resolve: (value: HelpfulActionResult) => void = () => {};
  const promise = new Promise<HelpfulActionResult>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

const button = () => screen.getByRole("button", { name: /^有帮助/ });

describe("HelpfulButton 乐观更新", () => {
  it("点下立刻 +1 并标已点，请求期间 aria-busy", async () => {
    const pending = deferred();
    action.mockReturnValue(pending.promise);
    renderButton({ count: 3 });
    await press();
    expect(action).toHaveBeenCalledWith(COMMENT, true);
    expect(button()).toHaveAttribute("aria-pressed", "true");
    expect(button()).toHaveAttribute("aria-busy", "true");
    expect(button()).toHaveAccessibleName("有帮助，4 人觉得有帮助");
    await act(async () =>
      pending.resolve({ ok: true, result: { helpful: true, helpfulCount: 5 } }),
    );
    expect(button()).toHaveAccessibleName("有帮助，5 人觉得有帮助");
    expect(button()).not.toHaveAttribute("aria-busy");
  });

  it("请求期间再点不发第二次", async () => {
    action.mockReturnValue(deferred().promise);
    renderButton();
    await press();
    await press();
    expect(action).toHaveBeenCalledTimes(1);
  });

  it("取消：立刻 -1", async () => {
    action.mockReturnValue(deferred().promise);
    renderButton({ helpful: true, count: 4 });
    await press();
    expect(action).toHaveBeenCalledWith(COMMENT, false);
    expect(button()).toHaveAccessibleName("有帮助，3 人觉得有帮助");
    expect(button()).toHaveAttribute("aria-pressed", "false");
  });

  it("成功后写回缓存并标过期", async () => {
    const patch = vi.spyOn(helpfulCache, "patchHelpful");
    const stale = vi.spyOn(helpfulCache, "markHelpfulStale");
    action.mockResolvedValue({ ok: true, result: { helpful: true, helpfulCount: 4 } });
    renderButton();
    await press();
    expect(patch).toHaveBeenCalledWith(expect.anything(), COMMENT, {
      helpful: true,
      helpfulCount: 4,
    });
    expect(stale).toHaveBeenCalledWith(expect.anything(), COMMENT);
  });
});

describe("HelpfulButton 失败", () => {
  it("网络失败：退回点击前，提示 4 秒", async () => {
    action.mockResolvedValue({ ok: false, reason: "unavailable" });
    renderButton({ count: 3 });
    await press();
    expect(button()).toHaveAttribute("aria-pressed", "false");
    expect(button()).toHaveAccessibleName("有帮助，3 人觉得有帮助");
    expect(screen.getByRole("status")).toHaveTextContent("没有标记成功，请稍后再试");
    act(() => vi.advanceTimersByTime(4000));
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("取消失败的文案", async () => {
    action.mockResolvedValue({ ok: false, reason: "unavailable" });
    renderButton({ helpful: true, count: 4 });
    await press();
    expect(button()).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("status")).toHaveTextContent("没有取消成功，请稍后再试");
  });

  it.each([
    ["daily-limit", "今天已经点了 300 次有帮助，明天再来吧"],
    ["account-restricted", "账号状态异常，暂时不能点有帮助"],
    ["self", "没有标记成功，请稍后再试"],
    ["email-unverified", "验证邮箱后才能点有帮助"],
  ] as const)("%s 的提示", async (reason, text) => {
    action.mockResolvedValue({ ok: false, reason });
    renderButton();
    await press();
    expect(screen.getByRole("status")).toHaveTextContent(text);
  });

  it("会话失效：退回并去登录", async () => {
    action.mockResolvedValue({ ok: false, reason: "signed-out" });
    const { router } = renderButton();
    await press();
    expect(button()).toHaveAttribute("aria-pressed", "false");
    expect(router.push).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("目标已删除：提示结束后才对账", async () => {
    action.mockResolvedValue({ ok: false, reason: "gone" });
    const onGone = vi.fn();
    renderButton({ onGone });
    await press();
    expect(screen.getByRole("status")).toHaveTextContent("这条评论已被删除");
    act(() => vi.advanceTimersByTime(3500));
    expect(onGone).not.toHaveBeenCalled();
    act(() => vi.advanceTimersByTime(600));
    expect(onGone).toHaveBeenCalledTimes(1);
  });

  it("新的一次点击替换提示并重新计时", async () => {
    action.mockResolvedValue({ ok: false, reason: "unavailable" });
    renderButton();
    await press();
    act(() => vi.advanceTimersByTime(3000));
    action.mockResolvedValue({ ok: false, reason: "daily-limit" });
    await press();
    act(() => vi.advanceTimersByTime(3000));
    expect(screen.getByRole("status")).toHaveTextContent("今天已经点了 300 次有帮助");
  });

  it("卸载后不再对账", async () => {
    action.mockResolvedValue({ ok: false, reason: "gone" });
    const onGone = vi.fn();
    const view = renderButton({ onGone });
    await press();
    view.unmount();
    act(() => vi.advanceTimersByTime(4000));
    expect(onGone).not.toHaveBeenCalled();
  });
});

describe("HelpfulButton 以点击时的服务端值为基准", () => {
  it("请求期间 props 变了仍显示乐观值，结束后以较新的 props 为准", async () => {
    const pending = deferred();
    action.mockReturnValue(pending.promise);
    const view = renderButton({ count: 3 });
    await press();
    view.update({ helpful: false, count: 9 });
    expect(button()).toHaveAccessibleName("有帮助，4 人觉得有帮助");
    await act(async () =>
      pending.resolve({ ok: true, result: { helpful: true, helpfulCount: 4 } }),
    );
    expect(button()).toHaveAccessibleName("有帮助，9 人觉得有帮助");
  });

  it("props 一直不变（经验页）时显示返回值；之后 props 变了以 props 为准", async () => {
    action.mockResolvedValue({ ok: true, result: { helpful: true, helpfulCount: 4 } });
    const view = renderButton({ count: 3 });
    await press();
    expect(button()).toHaveAccessibleName("有帮助，4 人觉得有帮助");
    expect(button()).toHaveAttribute("aria-pressed", "true");
    view.update({ helpful: false, count: 7 });
    expect(button()).toHaveAccessibleName("有帮助，7 人觉得有帮助");
    expect(button()).toHaveAttribute("aria-pressed", "false");
  });

  it("父组件重新渲染换了 onGone 也不重新计时", async () => {
    action.mockResolvedValue({ ok: false, reason: "gone" });
    const first = vi.fn();
    const view = renderButton({ onGone: first });
    await press();
    act(() => vi.advanceTimersByTime(3000));
    const second = vi.fn();
    view.update({ onGone: second });
    act(() => vi.advanceTimersByTime(1500));
    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledTimes(1);
  });
});

describe("HelpfulButton 评审补充", () => {
  it("取消后 props 变过又碰巧回到点击时的值：以 props 为准", async () => {
    action.mockResolvedValue({ ok: true, result: { helpful: false, helpfulCount: 3 } });
    const view = renderButton({ helpful: true, count: 4 });
    await press();
    view.update({ helpful: false, count: 3 });
    view.update({ helpful: true, count: 4 });
    expect(button()).toHaveAttribute("aria-pressed", "true");
    expect(button()).toHaveAccessibleName("有帮助，4 人觉得有帮助");
  });

  it("请求在途时换了访客：回来的结果不写缓存、不显示", async () => {
    const pending = deferred();
    action.mockReturnValue(pending.promise);
    const patch = vi.spyOn(helpfulCache, "patchHelpful");
    const view = renderButton({ count: 3 });
    await press();
    act(() => view.client.setQueryData(["current-user"], { ...USER, userId: "other" }));
    await act(async () =>
      pending.resolve({ ok: true, result: { helpful: true, helpfulCount: 4 } }),
    );
    expect(patch).not.toHaveBeenCalled();
    expect(button()).toHaveAccessibleName("有帮助，3 人觉得有帮助");
    expect(button()).toHaveAttribute("aria-pressed", "false");
  });

  it("成功后换了访客、props 没变（经验页）：不再显示上一位的返回值", async () => {
    action.mockResolvedValue({ ok: true, result: { helpful: true, helpfulCount: 4 } });
    const view = renderButton({ count: 3 });
    await press();
    expect(button()).toHaveAttribute("aria-pressed", "true");
    act(() => view.client.setQueryData(["current-user"], { ...USER, userId: "other" }));
    // TanStack 的通知按 setTimeout(0) 合批，假定时器下要推一下
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(button()).toHaveAttribute("aria-pressed", "false");
  });

  it("播报区在提示出现前就在、没有被隐藏", () => {
    const { container } = renderButton();
    const live = container.querySelector('[aria-live="polite"]');
    expect(live).not.toBeNull();
    expect(live?.className ?? "").not.toMatch(/hidden/);
  });
});

describe("HelpfulButton 读屏名（e2e 发现）", () => {
  it("读屏名由 aria-label 给出，不靠拼接文字（Chromium 会在隐藏文字前多算一个空格）", () => {
    renderButton({ count: 3 });
    expect(button()).toHaveAttribute("aria-label", "有帮助，3 人觉得有帮助");
  });
});

describe("HelpfulButton 登录状态还没回来（生产构建 e2e 发现）", () => {
  /// 当前用户还在取：`/api/auth/me` 挂起，返回一个放行函数
  function renderWhileUserLoading() {
    let release: () => void = () => {};
    vi.stubGlobal(
      "fetch",
      vi.fn(
        () =>
          new Promise<Response>((resolve) => {
            release = () => resolve(Response.json({ user: { ...USER, emailVerified: true } }));
          }),
      ),
    );
    const view = renderDiscussion(
      <HelpfulButton target={COMMENT} helpful={false} count={3} own={false} variant="text" />,
      { seed: (client) => client.removeQueries({ queryKey: queryKeys.currentUser }) },
    );
    return { ...view, release: () => release() };
  }

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("should_mark_button_disabled_until_current_user_arrives", async () => {
    const { release } = renderWhileUserLoading();
    expect(button()).toHaveAttribute("aria-disabled", "true");
    await act(async () => release());
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(button()).not.toHaveAttribute("aria-disabled");
  });

  it("should_ignore_click_while_current_user_is_loading", async () => {
    renderWhileUserLoading();
    await press();
    expect(action).not.toHaveBeenCalled();
    expect(button()).toHaveAttribute("aria-pressed", "false");
  });

  it("should_keep_mark_after_current_user_arrives", async () => {
    action.mockResolvedValue({ ok: true, result: { helpful: true, helpfulCount: 4 } });
    const { release } = renderWhileUserLoading();
    await press();
    await act(async () => release());
    await act(() => vi.advanceTimersByTimeAsync(0));
    await press();
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(action).toHaveBeenCalledTimes(1);
    expect(button()).toHaveAttribute("aria-pressed", "true");
    expect(button()).toHaveAccessibleName("有帮助，4 人觉得有帮助");
  });
});
