import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { LoginForm } from "@/components/auth/LoginForm";
import { login } from "@/lib/auth/actions";
import type { LoginResult } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

const router = { replace: vi.fn(), prefetch: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

import { toast } from "sonner";

const NEXT = "/papers?q=GLP-1";
const USER = { userId: "319041872872550658", email: "chen.yu@example.com" };

/// 一个还没回来的请求，`answer` 让它在测试想要的时候回来
function deferred<T>() {
  let answer: (value: T) => void = () => {};
  const promise = new Promise<T>((resolve) => {
    answer = resolve;
  });
  return { promise, answer };
}

function renderLogin() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const user = userEvent.setup();
  const view = render(
    <QueryClientProvider client={client}>
      <LoginForm next={NEXT} />
    </QueryClientProvider>,
  );
  const email = screen.getByLabelText("邮箱") as HTMLInputElement;
  const password = screen.getByLabelText("密码") as HTMLInputElement;
  const submit = screen.getByRole("button", { name: "登录" });
  return { client, user, view, email, password, submit };
}

/// 填好邮箱和密码后提交
async function fillAndSubmit(form: ReturnType<typeof renderLogin>, email = USER.email) {
  await form.user.type(form.email, email);
  await form.user.type(form.password, "correct horse");
  await form.user.click(form.submit);
}

beforeEach(() => {
  vi.mocked(login).mockResolvedValue({ ok: true, user: USER });
});

afterEach(() => {
  vi.clearAllMocks();
  vi.unstubAllGlobals();
});

describe("LoginForm · 输入框", () => {
  it("进入时焦点在邮箱框", () => {
    const { email } = renderLogin();
    expect(email).toHaveFocus();
  });

  it("浏览器和密码管理器能识别：邮箱是用户名，密码是当前密码；不做拼写检查", () => {
    const { email, password } = renderLogin();
    expect(email).toHaveAttribute("type", "email");
    expect(email).toHaveAttribute("autocomplete", "username");
    expect(email).toHaveAttribute("spellcheck", "false");
    expect(email).toHaveAttribute("placeholder", "name@example.com");
    expect(password).toHaveAttribute("type", "password");
    expect(password).toHaveAttribute("autocomplete", "current-password");
    expect(password).toHaveAttribute("spellcheck", "false");
  });

  it("去注册的链接带上 next", () => {
    renderLogin();
    expect(screen.getByRole("link", { name: "注册" })).toHaveAttribute(
      "href",
      "/register?next=%2Fpapers%3Fq%3DGLP-1",
    );
  });
});

describe("LoginForm · 校验时机", () => {
  it("输入时不校验，离开字段时校验它", async () => {
    const { user, email } = renderLogin();
    await user.type(email, "reader");
    expect(screen.queryByText("邮箱格式不正确")).not.toBeInTheDocument();
    await user.tab();
    expect(email).toHaveAccessibleDescription("邮箱格式不正确");
    expect(email).toHaveAttribute("aria-invalid", "true");
  });

  it("显示错误之后随输入重新校验，改对了错误立即消失", async () => {
    const { user, email } = renderLogin();
    await user.type(email, "reader");
    await user.tab();
    await user.type(email, "@example.com");
    expect(screen.queryByText("邮箱格式不正确")).not.toBeInTheDocument();
    expect(email).toHaveAttribute("aria-invalid", "false");
  });

  it("提交时校验全部字段，焦点移到第一个出错的字段，不请求", async () => {
    const { user, email, password, submit } = renderLogin();
    await user.click(submit);
    expect(email).toHaveAccessibleDescription("请输入邮箱");
    expect(password).toHaveAccessibleDescription("请输入密码");
    expect(email).toHaveFocus();
    expect(login).not.toHaveBeenCalled();
  });

  it("提交之后，没报错的字段仍然只在离开时校验", async () => {
    const { user, email, password, submit } = renderLogin();
    await user.type(email, USER.email);
    await user.click(submit);
    expect(password).toHaveAccessibleDescription("请输入密码");
    await user.clear(email);
    await user.type(email, "x");
    expect(screen.queryByText("邮箱格式不正确")).not.toBeInTheDocument();
    await user.tab();
    expect(screen.getByText("邮箱格式不正确")).toBeInTheDocument();
  });

  it("在输入框里回车即提交；邮箱去掉首尾空白，密码原样", async () => {
    const { user, email, password } = renderLogin();
    await user.type(email, `  ${USER.email} `);
    await user.type(password, " pass word {Enter}");
    await waitFor(() => expect(login).toHaveBeenCalledTimes(1));
    expect(login).toHaveBeenCalledWith({ email: USER.email, password: " pass word " });
  });
});

describe("LoginForm · 后端返回失败", () => {
  it.each<[string, LoginResult, string]>([
    ["邮箱或密码错误", { ok: false, reason: "bad-credentials" }, "邮箱或密码错误"],
    [
      "限流 61 秒",
      { ok: false, reason: "throttled", retryAfterSeconds: 61 },
      "尝试次数过多，请 2 分钟后再试",
    ],
    ["封禁", { ok: false, reason: "banned" }, "该账号已被封禁，无法登录"],
    ["服务不可用", { ok: false, reason: "unavailable" }, "登录没有成功，请稍后再试"],
    [
      "字段名一条都对不上",
      {
        ok: false,
        reason: "invalid-fields",
        fieldErrors: [{ field: "deviceId", code: "REQUIRED" }],
      },
      "登录没有成功，请稍后再试",
    ],
  ])("%s：提示条，保留邮箱、清空密码，焦点回到密码框", async (_label, result, message) => {
    vi.mocked(login).mockResolvedValue(result);
    const form = renderLogin();
    await fillAndSubmit(form);
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(form.email).toHaveValue(USER.email);
    expect(form.password).toHaveValue("");
    expect(form.password).toHaveFocus();
    expect(form.submit).not.toHaveAttribute("aria-disabled");
  });

  it("action 的 promise 被拒绝：按服务不可用处理", async () => {
    vi.mocked(login).mockRejectedValue(new Error("network"));
    const form = renderLogin();
    await fillAndSubmit(form);
    expect(await screen.findByRole("alert")).toHaveTextContent("登录没有成功，请稍后再试");
  });

  it("字段错误：显示在字段下方，同一字段只显示第一条，焦点移到第一个出错的字段", async () => {
    vi.mocked(login).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [
        { field: "password", code: "REQUIRED" },
        { field: "password", code: "TOO_LONG" },
        { field: "email", code: "INVALID_FORMAT" },
      ],
    });
    const form = renderLogin();
    await fillAndSubmit(form);
    await waitFor(() => expect(form.email).toHaveAccessibleDescription("邮箱格式不正确"));
    expect(form.password).toHaveAccessibleDescription("请输入密码");
    expect(form.email).toHaveFocus();
    expect(form.password).toHaveValue("correct horse");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("后端的字段错误跟着值走：离开字段不会消失，值一改就消失（邮箱按去掉空白后的值比）", async () => {
    vi.mocked(login).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "email", code: "INVALID_FORMAT" }],
    });
    const form = renderLogin();
    await fillAndSubmit(form);
    await waitFor(() => expect(form.email).toHaveAccessibleDescription("邮箱格式不正确"));
    await form.user.tab();
    expect(form.email).toHaveAccessibleDescription("邮箱格式不正确");
    await form.user.type(form.email, " ");
    expect(form.email).toHaveAccessibleDescription("邮箱格式不正确");
    await form.user.type(form.email, "{Backspace}{Backspace}");
    expect(form.email).toHaveValue("chen.yu@example.co");
    expect(form.email).not.toHaveAccessibleDescription("邮箱格式不正确");
    expect(form.email).toHaveAttribute("aria-invalid", "false");
  });

  it("再次提交时，后端的字段错误也先清掉", async () => {
    vi.mocked(login).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "email", code: "INVALID_FORMAT" }],
    });
    const form = renderLogin();
    await fillAndSubmit(form);
    await waitFor(() => expect(form.email).toHaveAccessibleDescription("邮箱格式不正确"));
    vi.mocked(login).mockReturnValue(deferred<LoginResult>().promise);
    await form.user.click(form.submit);
    expect(screen.getByRole("button", { name: "登录中…" })).toBeInTheDocument();
    expect(form.email).toHaveAttribute("aria-invalid", "false");
  });

  it("再次提交时先清掉上一次的提示条；同样的错误再出现，提示条是新插入的", async () => {
    vi.mocked(login).mockResolvedValue({ ok: false, reason: "bad-credentials" });
    const form = renderLogin();
    await fillAndSubmit(form);
    const first = await screen.findByRole("alert");
    const pending = deferred<LoginResult>();
    vi.mocked(login).mockReturnValue(pending.promise);
    await form.user.type(form.password, "another try");
    await form.user.click(form.submit);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    await act(async () => pending.answer({ ok: false, reason: "bad-credentials" }));
    const second = await screen.findByRole("alert");
    expect(second).toHaveTextContent("邮箱或密码错误");
    expect(second).not.toBe(first);
  });
});

describe("LoginForm · 提交中", () => {
  it("字段只读，按钮 aria-disabled 并换文案，焦点留在按钮上", async () => {
    const pending = deferred<LoginResult>();
    vi.mocked(login).mockReturnValue(pending.promise);
    const form = renderLogin();
    await fillAndSubmit(form);
    const busy = screen.getByRole("button", { name: "登录中…" });
    expect(busy).toHaveAttribute("aria-disabled", "true");
    expect(busy).not.toBeDisabled();
    expect(busy).toHaveAttribute("title", "正在登录");
    expect(busy).toHaveFocus();
    expect(form.email).toHaveAttribute("readonly");
    expect(form.password).toHaveAttribute("readonly");
  });

  it("连点、在输入框里回车都只发一次请求", async () => {
    const pending = deferred<LoginResult>();
    vi.mocked(login).mockReturnValue(pending.promise);
    const form = renderLogin();
    await fillAndSubmit(form);
    await form.user.click(screen.getByRole("button", { name: "登录中…" }));
    await form.user.type(form.password, "{Enter}");
    expect(login).toHaveBeenCalledTimes(1);
  });

  it("还没来得及显示提交中就再次提交，也只发一次请求", async () => {
    vi.mocked(login).mockReturnValue(deferred<LoginResult>().promise);
    const form = renderLogin();
    await form.user.type(form.email, USER.email);
    await form.user.type(form.password, "correct horse");
    const element = form.submit.closest("form") as HTMLFormElement;
    fireEvent.submit(element);
    fireEvent.submit(element);
    await waitFor(() => expect(login).toHaveBeenCalled());
    await act(async () => {});
    expect(login).toHaveBeenCalledTimes(1);
  });
});

describe("LoginForm · 成功", () => {
  it("先预取目标页再请求；成功后写入当前用户、提示「已登录」、替换到 next", async () => {
    const form = renderLogin();
    await fillAndSubmit(form);
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith(NEXT));
    expect(router.prefetch).toHaveBeenCalledWith(NEXT);
    expect(vi.mocked(router.prefetch).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(login).mock.invocationCallOrder[0] ?? 0,
    );
    expect(form.client.getQueryData(queryKeys.currentUser)).toEqual(USER);
    expect(toast.success).toHaveBeenCalledWith("已登录");
  });

  it("跳转完成前一直显示提交中，不能再提交", async () => {
    const form = renderLogin();
    await fillAndSubmit(form);
    await waitFor(() => expect(router.replace).toHaveBeenCalled());
    const busy = screen.getByRole("button", { name: "登录中…" });
    expect(busy).toHaveAttribute("aria-disabled", "true");
    await form.user.click(busy);
    expect(login).toHaveBeenCalledTimes(1);
  });

  it("成功前发出、成功后才回来的 /api/auth/me 不会把用户覆盖成 null", async () => {
    const late = { ok: true, json: vi.fn(async () => ({ user: null })) };
    const me = deferred<typeof late>();
    vi.stubGlobal(
      "fetch",
      vi.fn(() => me.promise),
    );
    const form = renderLogin();
    act(() => {
      void form.client.prefetchQuery({
        queryKey: queryKeys.currentUser,
        queryFn: async () => ((await (await fetch("/api/auth/me")).json()) as { user: null }).user,
      });
    });
    await fillAndSubmit(form);
    await waitFor(() => expect(router.replace).toHaveBeenCalled());
    me.answer(late);
    await waitFor(() => expect(late.json).toHaveBeenCalled());
    await act(async () => {});
    expect(form.client.getQueryData(queryKeys.currentUser)).toEqual(USER);
  });

  it("提交中离开了页面（表单被卸载），请求成功后仍然写入、提示、跳转", async () => {
    const pending = deferred<LoginResult>();
    vi.mocked(login).mockReturnValue(pending.promise);
    const form = renderLogin();
    await fillAndSubmit(form);
    form.view.unmount();
    await act(async () => pending.answer({ ok: true, user: USER }));
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith(NEXT));
    expect(form.client.getQueryData(queryKeys.currentUser)).toEqual(USER);
    expect(toast.success).toHaveBeenCalledWith("已登录");
  });

  it("提交中离开了页面，之后请求失败：什么也不做，也不报错", async () => {
    const pending = deferred<LoginResult>();
    vi.mocked(login).mockReturnValue(pending.promise);
    const form = renderLogin();
    await fillAndSubmit(form);
    form.view.unmount();
    await act(async () => pending.answer({ ok: false, reason: "bad-credentials" }));
    expect(router.replace).not.toHaveBeenCalled();
    expect(toast.success).not.toHaveBeenCalled();
  });
});
