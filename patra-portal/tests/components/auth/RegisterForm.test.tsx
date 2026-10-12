import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { register } from "@/lib/auth/actions";
import type { RegisterResult } from "@/lib/auth/types";
import { queryKeys } from "@/lib/query-keys";

const router = { replace: vi.fn(), prefetch: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

import { toast } from "sonner";

const NEXT = "/journals/7";
const USER = { userId: "319041872872550658", email: "chen.yu@example.com" };
const PASSWORD = "correct horse";

/// 一个还没回来的请求，`answer` 让它在测试想要的时候回来
function deferred<T>() {
  let answer: (value: T) => void = () => {};
  const promise = new Promise<T>((resolve) => {
    answer = resolve;
  });
  return { promise, answer };
}

function renderRegister() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const user = userEvent.setup();
  render(
    <QueryClientProvider client={client}>
      <RegisterForm next={NEXT} />
    </QueryClientProvider>,
  );
  const email = screen.getByLabelText("邮箱") as HTMLInputElement;
  const password = screen.getByLabelText("密码") as HTMLInputElement;
  const confirm = screen.getByLabelText("确认密码") as HTMLInputElement;
  const submit = screen.getByRole("button", { name: "注册" });
  return { client, user, email, password, confirm, submit };
}

/// 三个字段都填好后点「注册」。邮箱前后带空格：type="email" 的输入框读出的值不带首尾空白
async function fillAndSubmit(form: ReturnType<typeof renderRegister>) {
  await form.user.type(form.email, ` ${USER.email} `);
  await form.user.type(form.password, PASSWORD);
  await form.user.type(form.confirm, PASSWORD);
  await form.user.click(form.submit);
}

beforeEach(() => {
  vi.mocked(register).mockResolvedValue({ ok: true, user: USER });
});

afterEach(() => {
  vi.clearAllMocks();
});

describe("RegisterForm · 输入框", () => {
  it("进入时焦点在邮箱框", () => {
    const { email } = renderRegister();
    expect(email).toHaveFocus();
  });

  it("浏览器和密码管理器能识别：邮箱是 email，两个密码框是新密码；不做拼写检查", () => {
    const { email, password, confirm } = renderRegister();
    expect(email).toHaveAttribute("type", "email");
    expect(email).toHaveAttribute("autocomplete", "email");
    expect(email).toHaveAttribute("placeholder", "name@example.com");
    for (const input of [email, password, confirm]) {
      expect(input).toHaveAttribute("spellcheck", "false");
    }
    for (const input of [password, confirm]) {
      expect(input).toHaveAttribute("type", "password");
      expect(input).toHaveAttribute("autocomplete", "new-password");
    }
  });

  it("密码下方常驻「至少 8 位」，出错时被错误文案替换", async () => {
    const { user, password } = renderRegister();
    expect(password).toHaveAccessibleDescription("至少 8 位");
    await user.type(password, "short");
    await user.tab();
    expect(password).toHaveAccessibleDescription("密码至少 8 位");
    expect(screen.queryByText("至少 8 位")).not.toBeInTheDocument();
  });

  it("提交按钮上方提示不支持找回密码", () => {
    renderRegister();
    expect(screen.getByText("请牢记密码，目前不支持找回密码。")).toBeInTheDocument();
  });

  it("两个显示 / 隐藏切换互不影响", async () => {
    const { user, password, confirm } = renderRegister();
    const [passwordToggle] = screen.getAllByRole("button", { name: "显示密码" });
    await user.click(passwordToggle as HTMLElement);
    expect(password).toHaveAttribute("type", "text");
    expect(confirm).toHaveAttribute("type", "password");
  });

  it("去登录的链接带上 next", () => {
    renderRegister();
    expect(screen.getByRole("link", { name: "登录" })).toHaveAttribute(
      "href",
      "/login?next=%2Fjournals%2F7",
    );
  });
});

describe("RegisterForm · 确认密码", () => {
  it("为空时提示再输入一次，和密码不同时提示不一致", async () => {
    const { user, password, confirm, submit } = renderRegister();
    await user.type(password, PASSWORD);
    await user.click(submit);
    expect(confirm).toHaveAccessibleDescription("请再输入一次密码");
    await user.type(confirm, "correct horsf");
    await user.tab();
    expect(confirm).toHaveAccessibleDescription("两次输入的密码不一致");
  });

  it("确认密码不为空时，改密码会立即重新比对", async () => {
    const { user, password, confirm } = renderRegister();
    await user.type(password, PASSWORD);
    await user.type(confirm, PASSWORD);
    await user.type(password, "!");
    expect(confirm).toHaveAccessibleDescription("两次输入的密码不一致");
    await user.type(password, "{Backspace}");
    expect(confirm).toHaveAttribute("aria-invalid", "false");
  });

  it("确认密码为空时，改密码不去校验确认密码", async () => {
    const { user, password, confirm } = renderRegister();
    await user.type(password, PASSWORD);
    expect(confirm).toHaveAttribute("aria-invalid", "false");
  });
});

describe("RegisterForm · 后端返回失败", () => {
  it("邮箱已注册：邮箱下方提示并给出去登录的链接，保留全部内容，焦点移到邮箱", async () => {
    vi.mocked(register).mockResolvedValue({ ok: false, reason: "email-taken" });
    const form = renderRegister();
    await fillAndSubmit(form);
    await waitFor(() => expect(form.email).toHaveAccessibleDescription("该邮箱已注册，去登录"));
    expect(screen.getByRole("link", { name: "去登录" })).toHaveAttribute(
      "href",
      "/login?next=%2Fjournals%2F7",
    );
    expect(form.email).toHaveFocus();
    expect(form.password).toHaveValue(PASSWORD);
    expect(form.confirm).toHaveValue(PASSWORD);
  });

  it("密码太常见：密码下方提示，保留全部内容，焦点移到密码框；改了密码提示就消失", async () => {
    vi.mocked(register).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "password", code: "TOO_COMMON" }],
    });
    const form = renderRegister();
    await fillAndSubmit(form);
    await waitFor(() =>
      expect(form.password).toHaveAccessibleDescription("这个密码太常见，容易被猜到，请换一个"),
    );
    expect(form.password).toHaveFocus();
    expect(form.password).toHaveValue(PASSWORD);
    expect(form.confirm).toHaveValue(PASSWORD);
    await form.user.type(form.password, "!");
    expect(form.password).toHaveAccessibleDescription("至少 8 位");
  });

  it.each<[string, () => void]>([
    [
      "服务不可用",
      () => vi.mocked(register).mockResolvedValue({ ok: false, reason: "unavailable" }),
    ],
    ["action 的 promise 被拒绝", () => vi.mocked(register).mockRejectedValue(new Error("network"))],
    [
      "字段名一条都对不上",
      () =>
        vi.mocked(register).mockResolvedValue({
          ok: false,
          reason: "invalid-fields",
          fieldErrors: [{ field: "nickname", code: "REQUIRED" }],
        }),
    ],
  ])("%s：提示条，保留全部内容，焦点留在提交按钮上", async (_label, arrange) => {
    arrange();
    const form = renderRegister();
    await fillAndSubmit(form);
    expect(await screen.findByRole("alert")).toHaveTextContent("注册没有成功，请稍后再试");
    expect(form.email).toHaveValue(USER.email);
    expect(form.password).toHaveValue(PASSWORD);
    expect(form.confirm).toHaveValue(PASSWORD);
    expect(form.submit).toHaveFocus();
    expect(form.submit).not.toHaveAttribute("aria-disabled");
  });
});

describe("RegisterForm · 提交", () => {
  it("提交中：三个输入框只读，按钮 aria-disabled 并换文案", async () => {
    const pending = deferred<RegisterResult>();
    vi.mocked(register).mockReturnValue(pending.promise);
    const form = renderRegister();
    await fillAndSubmit(form);
    const busy = screen.getByRole("button", { name: "注册中…" });
    expect(busy).toHaveAttribute("aria-disabled", "true");
    expect(busy).toHaveAttribute("title", "正在注册");
    for (const input of [form.email, form.password, form.confirm]) {
      expect(input).toHaveAttribute("readonly");
    }
    await form.user.click(busy);
    expect(register).toHaveBeenCalledTimes(1);
    await act(async () => pending.answer({ ok: false, reason: "unavailable" }));
  });

  it("只把邮箱（去掉首尾空白）和密码交给后端，确认密码不传", async () => {
    const form = renderRegister();
    await fillAndSubmit(form);
    await waitFor(() => expect(register).toHaveBeenCalledTimes(1));
    expect(register).toHaveBeenCalledWith({ email: USER.email, password: PASSWORD });
  });

  it("成功：先预取目标页，写入当前用户，提示「注册成功，已登录」，替换到 next", async () => {
    const form = renderRegister();
    await fillAndSubmit(form);
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith(NEXT));
    expect(vi.mocked(router.prefetch).mock.invocationCallOrder[0]).toBeLessThan(
      vi.mocked(register).mock.invocationCallOrder[0] ?? 0,
    );
    expect(form.client.getQueryData(queryKeys.currentUser)).toEqual(USER);
    expect(toast.success).toHaveBeenCalledWith("注册成功，已登录");
  });
});
