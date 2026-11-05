import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ProviderRegistrationFlow } from "@/components/auth/ProviderRegistrationFlow";
import {
  postLink,
  postLinkCode,
  postRegistrationCode,
  postRegistrationComplete,
} from "@/lib/auth/post-registration";
import type { PendingRegistration, SendCodeResult } from "@/lib/auth/registration-types";
import { queryKeys } from "@/lib/query-keys";
import { useAuthHandoffStore } from "@/store/auth-handoff";

const router = { replace: vi.fn(), prefetch: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/auth/post-registration", () => ({
  postLink: vi.fn(),
  postLinkCode: vi.fn(),
  postRegistrationCode: vi.fn(),
  postRegistrationComplete: vi.fn(),
}));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));

import { toast } from "sonner";

afterEach(() => vi.clearAllMocks());

const GITHUB: PendingRegistration = {
  provider: "github",
  accountLabel: "octocat",
  suggestedUsername: "octocat",
  suggestedEmail: "cat@example.com",
  expiresAt: "2026-11-04T09:30:00Z",
  pendingCode: null,
};
const ORCID: PendingRegistration = {
  ...GITHUB,
  provider: "orcid",
  accountLabel: "0000-0002-1825-0097",
  suggestedUsername: null,
  suggestedEmail: null,
};
const LINK_ENTRY = "验证这个邮箱，并绑定到已有账号";
/// 刷新回来时的绑定验证码：已经过了重发冷却
const LINK_PENDING_READY = {
  purpose: "LINK" as const,
  username: null,
  email: "chen.yu@example.com",
  codeExpiresAt: "2099-01-01T00:10:00Z",
  nextResendAt: "2000-01-01T00:00:00Z",
};
const SENT = {
  ok: true,
  email: "cat@example.com",
  codeExpiresAt: "2099-01-01T00:10:00Z",
  nextResendAt: "2099-01-01T00:01:00Z",
} as const;

function renderFlow(pending: PendingRegistration | "expired", next = "/papers/1") {
  const client = new QueryClient();
  render(
    <QueryClientProvider client={client}>
      <ProviderRegistrationFlow pending={pending} next={next} />
    </QueryClientProvider>,
  );
  return { client, user: userEvent.setup() };
}

/// 从补填步提交，进到验证码步
async function toCodeStep(user: ReturnType<typeof userEvent.setup>) {
  vi.mocked(postRegistrationCode).mockResolvedValueOnce(SENT);
  await user.click(screen.getByRole("button", { name: "发送验证码" }));
  await screen.findByRole("heading", { name: "输入验证码" });
}

describe("补填步", () => {
  it("预填用户名和邮箱，说明来源；显示已确认的第三方账号", () => {
    renderFlow(GITHUB);
    expect(screen.getByRole("heading", { level: 1, name: "还差一步" })).toBeInTheDocument();
    expect(
      screen.getByText("第一次用 GitHub 账号登录 Patra，填好用户名和邮箱就能建好账号。"),
    ).toBeInTheDocument();
    expect(screen.getByText("已确认你的 GitHub 账号")).toBeInTheDocument();
    expect(screen.getByText("octocat")).toBeInTheDocument();
    expect(screen.getByLabelText("用户名")).toHaveValue("octocat");
    expect(screen.getByLabelText("邮箱")).toHaveValue("cat@example.com");
    expect(screen.getByText("已按你的 GitHub 用户名预填，可以改。")).toBeInTheDocument();
    expect(
      screen.getByText("我们会发验证码到这个邮箱。已按你的 GitHub 公开邮箱预填，可以改。"),
    ).toBeInTheDocument();
  });

  it.each([
    ["ORCID", ORCID, "ORCID 不提供邮箱，请填一个常用的。我们会发验证码到这个邮箱。"],
    [
      "GitHub 没有公开邮箱",
      { ...GITHUB, suggestedEmail: null },
      "你的 GitHub 没有公开邮箱，请填一个常用的。我们会发验证码到这个邮箱。",
    ],
    [
      "Google",
      { ...GITHUB, provider: "google", suggestedUsername: null },
      "我们会发验证码到这个邮箱。已按你的 Google 账号预填，可以改。",
    ],
  ] as const)("%s 的邮箱提示", (_name, pending, hint) => {
    renderFlow(pending);
    expect(screen.getByText(hint)).toBeInTheDocument();
  });

  it("前端规则挡住时不请求", async () => {
    const { user } = renderFlow(ORCID);
    await user.click(screen.getByRole("button", { name: "发送验证码" }));
    expect(await screen.findByText("请输入用户名")).toBeInTheDocument();
    expect(screen.getByText("请输入邮箱")).toBeInTheDocument();
    expect(postRegistrationCode).not.toHaveBeenCalled();
  });

  it("提交规范化后的用户名和邮箱", async () => {
    const { user } = renderFlow(GITHUB);
    const username = screen.getByLabelText("用户名");
    await user.clear(username);
    await user.type(username, "Chen-Yu");
    await toCodeStep(user);
    expect(postRegistrationCode).toHaveBeenCalledWith({
      username: "chen-yu",
      email: "cat@example.com",
    });
  });

  it("邮箱撞车：显示引导，不发字段错误；换一个邮箱清掉", async () => {
    const { user } = renderFlow(GITHUB);
    vi.mocked(postRegistrationCode).mockResolvedValue({
      ok: false,
      reason: "taken",
      email: true,
      username: false,
    });
    await user.click(screen.getByRole("button", { name: "发送验证码" }));
    const clash = await screen.findByRole("alert");
    expect(clash).toHaveTextContent("这个邮箱已经注册过 Patra。");
    expect(clash).toHaveTextContent(
      "如果是你的账号：可以验证这个邮箱直接绑定，也可以先用原来的方式登录，再到「设置 › 安全」绑定 GitHub。",
    );
    const toLogin = screen.getByRole("link", { name: "去登录" });
    expect(toLogin).toHaveAttribute("href", "/login?next=%2Fsettings%2Fsecurity");
    expect(toLogin).toHaveFocus();
    expect(screen.getByLabelText("邮箱")).toHaveAttribute("aria-invalid", "true");
    await user.click(screen.getByRole("button", { name: "换一个邮箱" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByLabelText("邮箱")).toHaveValue("");
  });

  it.each([
    [
      "用户名被占用",
      { ok: false, reason: "taken", email: false, username: true },
      "这个用户名已被使用",
    ],
    [
      "用户名是保留字",
      {
        ok: false,
        reason: "invalid-fields",
        fieldErrors: [{ field: "username", code: "RESERVED" }],
      },
      "这个用户名不能使用，请换一个",
    ],
    [
      "太频繁",
      { ok: false, reason: "throttled", retryAfterSeconds: 900 },
      "发送太频繁，请 15 分钟后再试",
    ],
    ["服务错误", { ok: false, reason: "unavailable" }, "验证码没有发出去，请稍后再试"],
  ] satisfies [string, SendCodeResult, string][])("%s", async (_name, result, message) => {
    const { user } = renderFlow(GITHUB);
    vi.mocked(postRegistrationCode).mockResolvedValue(result);
    await user.click(screen.getByRole("button", { name: "发送验证码" }));
    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "还差一步" })).toBeInTheDocument();
  });

  it("记录过期：过期态，带平台名，回登录页带 next", async () => {
    const { user } = renderFlow(GITHUB);
    vi.mocked(postRegistrationCode).mockResolvedValue({ ok: false, reason: "expired" });
    await user.click(screen.getByRole("button", { name: "发送验证码" }));
    expect(await screen.findByRole("heading", { name: "这次注册已过期" })).toBeInTheDocument();
    expect(
      screen.getByText("等得太久了，账号没有建。请重新用 GitHub 账号登录，再填一次。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "回到登录页" })).toHaveAttribute(
      "href",
      "/login?next=%2Fpapers%2F1",
    );
  });
});

describe("撞车去登录", () => {
  it("邮箱经内存带过去，不进地址栏，登录页焦点在密码框", async () => {
    const { user } = renderFlow(GITHUB);
    vi.mocked(postRegistrationCode).mockResolvedValue({
      ok: false,
      reason: "taken",
      email: true,
      username: false,
    });
    await user.click(screen.getByRole("button", { name: "发送验证码" }));
    const toLogin = await screen.findByRole("link", { name: "去登录" });
    toLogin.addEventListener("click", (event) => event.preventDefault());
    await user.click(toLogin);
    expect(useAuthHandoffStore.getState().take()).toEqual({
      email: "cat@example.com",
      notice: "email-clash",
    });
  });
});

describe("验证码步", () => {
  it("说明发到哪个邮箱；重发按 nextResendAt 倒计时", async () => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    expect(screen.getByText("cat@example.com")).toBeInTheDocument();
    expect(screen.getByText(/10 分钟内有效。验证通过后账号才会建好。/)).toBeInTheDocument();
    expect(screen.getByText(/^已发送 · \d+ 秒后可重发$/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "重新发送" })).not.toBeInTheDocument();
  });

  it("只接受数字，最多 6 位", async () => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    const input = screen.getByLabelText("验证码");
    await user.type(input, "48a39b20123");
    expect(input).toHaveValue("483920");
    expect(input).toHaveAttribute("autocomplete", "one-time-code");
    expect(input).toHaveAttribute("inputmode", "numeric");
  });

  it.each([
    ["", "请输入验证码"],
    ["123", "验证码是 6 位数字"],
  ])("%j：就地提示，不请求", async (value, message) => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    if (value) await user.type(screen.getByLabelText("验证码"), value);
    await user.click(screen.getByRole("button", { name: "完成注册" }));
    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(postRegistrationComplete).not.toHaveBeenCalled();
  });

  /// 输入完整的验证码并提交
  async function submitCode(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText("验证码"), "483920");
    await user.click(screen.getByRole("button", { name: "完成注册" }));
  }

  it("成功：写入当前用户，提示，跳 next", async () => {
    const { user, client } = renderFlow(GITHUB);
    await toCodeStep(user);
    vi.mocked(postRegistrationComplete).mockResolvedValue({ ok: true, user: USER });
    await submitCode(user);
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/papers/1"));
    expect(postRegistrationComplete).toHaveBeenCalledWith({ code: "483920" });
    expect(client.getQueryData(queryKeys.currentUser)).toEqual(USER);
    expect(toast.success).toHaveBeenCalledWith("注册成功，已登录");
  });

  it("验证码不对：字段错误，可以改了再交", async () => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    vi.mocked(postRegistrationComplete).mockResolvedValue({ ok: false, reason: "code-invalid" });
    await submitCode(user);
    expect(await screen.findByText("验证码不正确")).toBeInTheDocument();
    expect(screen.getByLabelText("验证码")).toHaveAttribute("aria-invalid", "true");
    await user.type(screen.getByLabelText("验证码"), "{Backspace}");
    expect(screen.queryByText("验证码不正确")).not.toBeInTheDocument();
  });

  it("验证码失效：提示重发，输入锁住，再按完成不请求", async () => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    vi.mocked(postRegistrationComplete).mockResolvedValue({ ok: false, reason: "code-expired" });
    await submitCode(user);
    expect(await screen.findByRole("alert")).toHaveTextContent("这个验证码已失效，请重新发送一个");
    expect(screen.getByLabelText("验证码")).toHaveAttribute("readonly");
    expect(screen.getByLabelText("验证码")).toHaveValue("");
    await user.click(screen.getByRole("button", { name: "完成注册" }));
    expect(postRegistrationComplete).toHaveBeenCalledTimes(1);
  });

  it("用户名被抢：回补填步，字段错误", async () => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    vi.mocked(postRegistrationComplete).mockResolvedValue({
      ok: false,
      reason: "taken",
      email: false,
      username: true,
    });
    await submitCode(user);
    expect(await screen.findByText("这个用户名刚被别人用了，请换一个")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "还差一步" })).toBeInTheDocument();
    expect(screen.getByLabelText("用户名")).toHaveValue("octocat");
  });

  it("邮箱被抢：回补填步，撞车引导", async () => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    vi.mocked(postRegistrationComplete).mockResolvedValue({
      ok: false,
      reason: "taken",
      email: true,
      username: false,
    });
    await submitCode(user);
    expect(await screen.findByRole("alert")).toHaveTextContent("这个邮箱已经注册过 Patra。");
  });

  it("这个第三方账号已注册（两个标签页并发）：结果页，回登录页", async () => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    vi.mocked(postRegistrationComplete).mockResolvedValue({ ok: false, reason: "provider-taken" });
    await submitCode(user);
    expect(await screen.findByRole("heading", { name: "这个账号已经建好了" })).toBeInTheDocument();
    expect(
      screen.getByText("你的 GitHub 账号刚刚完成了注册，可能是在另一个标签页。重新用它登录就行。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "回到登录页" })).toHaveAttribute(
      "href",
      "/login?next=%2Fpapers%2F1",
    );
  });

  it("验证码错：焦点回输入框并全选，方便重填", async () => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    vi.mocked(postRegistrationComplete).mockResolvedValue({ ok: false, reason: "code-invalid" });
    await submitCode(user);
    await screen.findByText("验证码不正确");
    const input = screen.getByLabelText("验证码") as HTMLInputElement;
    expect(input).toHaveFocus();
    expect([input.selectionStart, input.selectionEnd]).toEqual([0, 6]);
  });

  it("粘贴带空格的验证码也能用", async () => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    await user.click(screen.getByLabelText("验证码"));
    await user.paste("483 920");
    expect(screen.getByLabelText("验证码")).toHaveValue("483920");
  });

  it("倒计时不每秒朗读", async () => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    const countdown = screen.getByText(/^已发送 · \d+ 秒后可重发$/);
    expect(countdown.closest("[aria-live]")).toBeNull();
    expect(countdown.closest("[role=status]")).toBeNull();
  });

  it("记录过期：过期态", async () => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    vi.mocked(postRegistrationComplete).mockResolvedValue({ ok: false, reason: "expired" });
    await submitCode(user);
    expect(await screen.findByRole("heading", { name: "这次注册已过期" })).toBeInTheDocument();
  });

  it("服务错误：提示条，留在这一步，可以再交", async () => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    vi.mocked(postRegistrationComplete).mockResolvedValue({ ok: false, reason: "unavailable" });
    await submitCode(user);
    expect(await screen.findByRole("alert")).toHaveTextContent("没有验证成功，请稍后再试");
    expect(screen.getByLabelText("验证码")).toHaveValue("483920");
  });

  it("改用户名或邮箱：回补填步，值还在", async () => {
    const { user } = renderFlow(GITHUB);
    await toCodeStep(user);
    await user.click(screen.getByRole("button", { name: "改用户名或邮箱（要重新发验证码）" }));
    expect(screen.getByLabelText("邮箱")).toHaveValue("cat@example.com");
  });
});

describe("重发", () => {
  const PENDING_CODE = {
    purpose: "REGISTER" as const,
    email: "chen@example.com",
    username: "chen-yu",
    codeExpiresAt: "2026-11-04T09:20:00Z",
    nextResendAt: "2000-01-01T00:00:00Z",
  };

  it("刷新回来：直接进验证码步；用提交时的用户名和邮箱重发，解锁输入", async () => {
    const { user } = renderFlow({ ...GITHUB, pendingCode: PENDING_CODE });
    expect(screen.getByRole("heading", { name: "输入验证码" })).toBeInTheDocument();
    expect(screen.getByText("chen@example.com")).toBeInTheDocument();
    vi.mocked(postRegistrationCode).mockResolvedValue({ ...SENT, email: "chen@example.com" });
    await user.click(screen.getByRole("button", { name: "重新发送" }));
    expect(postRegistrationCode).toHaveBeenCalledWith({
      username: "chen-yu",
      email: "chen@example.com",
    });
    expect(await screen.findByText(/^已发送 · \d+ 秒后可重发$/)).toBeInTheDocument();
  });

  it("太频繁：按限流说；服务错误：提示条", async () => {
    const { user } = renderFlow({ ...GITHUB, pendingCode: PENDING_CODE });
    vi.mocked(postRegistrationCode).mockResolvedValueOnce({
      ok: false,
      reason: "unavailable",
    });
    await user.click(screen.getByRole("button", { name: "重新发送" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("没有发出去，请稍后再试");
    vi.mocked(postRegistrationCode).mockResolvedValueOnce({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: 900,
    });
    await user.click(screen.getByRole("button", { name: "重新发送" }));
    expect(await screen.findByText("发送太频繁，请 15 分钟后再试")).toBeInTheDocument();
  });

  it("验证码失效后重发成功：解除锁定", async () => {
    const { user } = renderFlow({ ...GITHUB, pendingCode: PENDING_CODE });
    vi.mocked(postRegistrationComplete).mockResolvedValue({ ok: false, reason: "code-expired" });
    await user.type(screen.getByLabelText("验证码"), "483920");
    await user.click(screen.getByRole("button", { name: "完成注册" }));
    await screen.findByRole("alert");
    vi.mocked(postRegistrationCode).mockResolvedValue({ ...SENT, email: "chen@example.com" });
    await user.click(screen.getByRole("button", { name: "重新发送" }));
    await waitFor(() => expect(screen.getByLabelText("验证码")).not.toHaveAttribute("readonly"));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("过期态", () => {
  it("进页面就过期：不知道平台，文案不提平台名", () => {
    renderFlow("expired");
    expect(screen.getByRole("heading", { name: "这次注册已过期" })).toBeInTheDocument();
    expect(
      screen.getByText("等得太久了，账号没有建。请回到登录页，重新用第三方账号登录，再填一次。"),
    ).toBeInTheDocument();
  });
});

describe("就地绑定", () => {
  const LINK_SENT = { ...SENT, email: "cat@example.com" };

  /// 补填步提交后撞车
  async function toClash() {
    const view = renderFlow(GITHUB);
    vi.mocked(postRegistrationCode).mockResolvedValue({
      ok: false,
      reason: "taken",
      email: true,
      username: false,
    });
    await view.user.click(screen.getByRole("button", { name: "发送验证码" }));
    await screen.findByRole("alert");
    return view;
  }

  /// 撞车后选择就地绑定，进到验证码步
  async function toLinkStep() {
    const view = await toClash();
    vi.mocked(postLinkCode).mockResolvedValueOnce(LINK_SENT);
    await view.user.click(screen.getByRole("button", { name: LINK_ENTRY }));
    await screen.findByRole("heading", { name: "验证这个邮箱" });
    return view;
  }

  /// 输入完整的验证码并提交
  async function submitLinkCode(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText("验证码"), "483920");
    await user.click(screen.getByRole("button", { name: "完成绑定" }));
  }

  it("只有撞车才有这个选项", async () => {
    renderFlow(GITHUB);
    expect(screen.queryByRole("button", { name: LINK_ENTRY })).not.toBeInTheDocument();
    cleanup();
    await toClash();
    expect(screen.getByRole("button", { name: LINK_ENTRY })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "去登录" })).toHaveFocus();
  });

  it("用户名被占用但邮箱没撞车：没有这个选项", async () => {
    const { user } = renderFlow(GITHUB);
    vi.mocked(postRegistrationCode).mockResolvedValue({
      ok: false,
      reason: "taken",
      email: false,
      username: true,
    });
    await user.click(screen.getByRole("button", { name: "发送验证码" }));
    await screen.findByText("这个用户名已被使用");
    expect(screen.queryByRole("button", { name: LINK_ENTRY })).not.toBeInTheDocument();
  });

  it("选择后：用当前邮箱发码，文案不说已发送", async () => {
    await toLinkStep();
    expect(postLinkCode).toHaveBeenCalledWith({ email: "cat@example.com" });
    expect(screen.getByText("cat@example.com")).toBeInTheDocument();
    expect(
      screen.getByText(
        /如果这个邮箱可以绑定，你会收到验证码；收不到就先用原方式登录，到设置页绑定/,
      ),
    ).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("已发");
    expect(document.body.textContent).not.toContain("垃圾邮件");
    expect(screen.getByText(/^\d+ 秒后可重发$/)).toBeInTheDocument();
  });

  it("发码：进行中不能重复点", async () => {
    const { user } = await toClash();
    let answer: (value: typeof LINK_SENT) => void = () => {};
    vi.mocked(postLinkCode).mockReturnValue(
      new Promise((resolve) => {
        answer = resolve;
      }),
    );
    await user.click(screen.getByRole("button", { name: LINK_ENTRY }));
    const busy = screen.getByRole("button", { name: "处理中…" });
    expect(busy).toHaveAttribute("aria-disabled", "true");
    await user.click(busy);
    expect(postLinkCode).toHaveBeenCalledTimes(1);
    answer(LINK_SENT);
    await screen.findByRole("heading", { name: "验证这个邮箱" });
  });

  it.each([
    [{ ok: false, reason: "throttled", retryAfterSeconds: 900 }, "发送太频繁，请 15 分钟后再试"],
    [{ ok: false, reason: "unavailable" }, "验证码没有发出去，请稍后再试"],
  ] as const)("发码失败 %j：留在撞车块，显示原因", async (result, message) => {
    const { user } = await toClash();
    vi.mocked(postLinkCode).mockResolvedValue(result);
    await user.click(screen.getByRole("button", { name: LINK_ENTRY }));
    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: LINK_ENTRY })).toBeInTheDocument();
  });

  it("发码时记录过期：绑定的过期态", async () => {
    const { user } = await toClash();
    vi.mocked(postLinkCode).mockResolvedValue({ ok: false, reason: "expired" });
    await user.click(screen.getByRole("button", { name: LINK_ENTRY }));
    expect(await screen.findByRole("heading", { name: "这次绑定已失效" })).toBeInTheDocument();
  });

  it("成功：写入当前用户，提示已绑定，跳 next", async () => {
    const { user, client } = await toLinkStep();
    vi.mocked(postLink).mockResolvedValue({ ok: true, user: USER });
    await submitLinkCode(user);
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/papers/1"));
    expect(postLink).toHaveBeenCalledWith({ code: "483920" });
    expect(postRegistrationComplete).not.toHaveBeenCalled();
    expect(client.getQueryData(queryKeys.currentUser)).toEqual(USER);
    expect(toast.success).toHaveBeenCalledWith("已绑定 GitHub，已登录");
  });

  it("冷静期账号：去取消注销页，不弹已绑定", async () => {
    const { user } = await toLinkStep();
    vi.mocked(postLink).mockResolvedValue({
      ok: true,
      user: { ...USER, status: "PENDING_DELETION" },
    });
    await submitLinkCode(user);
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith("/account/deletion-pending?next=%2Fpapers%2F1"),
    );
    expect(toast.success).not.toHaveBeenCalled();
  });

  it("错码：字段错误", async () => {
    const { user } = await toLinkStep();
    vi.mocked(postLink).mockResolvedValue({ ok: false, reason: "code-invalid" });
    await submitLinkCode(user);
    expect(await screen.findByText("验证码不正确")).toBeInTheDocument();
    expect(screen.getByLabelText("验证码")).toHaveFocus();
  });

  it("验证码失效：提示重发，输入锁住；重发解锁", async () => {
    const { user } = renderFlow({ ...GITHUB, pendingCode: LINK_PENDING_READY });
    vi.mocked(postLink).mockResolvedValue({ ok: false, reason: "code-expired" });
    await submitLinkCode(user);
    expect(await screen.findByRole("alert")).toHaveTextContent("这个验证码已失效，请重新发送一个");
    expect(screen.getByLabelText("验证码")).toHaveAttribute("readonly");
    vi.mocked(postLinkCode).mockResolvedValue(LINK_SENT);
    await user.click(screen.getByRole("button", { name: "重新发送" }));
    await waitFor(() => expect(screen.getByLabelText("验证码")).not.toHaveAttribute("readonly"));
    expect(postLinkCode).toHaveBeenCalledWith({ email: LINK_PENDING_READY.email });
    expect(postRegistrationCode).not.toHaveBeenCalled();
    expect(screen.getByText(/^\d+ 秒后可重发$/)).toBeInTheDocument();
  });

  it("重放（凭证已用过）：绑定的过期态，回登录页", async () => {
    const { user } = await toLinkStep();
    vi.mocked(postLink).mockResolvedValue({ ok: false, reason: "expired" });
    await submitLinkCode(user);
    expect(await screen.findByRole("heading", { name: "这次绑定已失效" })).toBeInTheDocument();
    expect(
      screen.getByText("可能是等得太久，或已经在另一个标签页完成。请重新用 GitHub 账号登录。"),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "回到登录页" })).toHaveAttribute(
      "href",
      "/login?next=%2Fpapers%2F1",
    );
  });

  it("第三方账号已绑给别的账号：结果页", async () => {
    const { user } = await toLinkStep();
    vi.mocked(postLink).mockResolvedValue({ ok: false, reason: "provider-taken" });
    await submitLinkCode(user);
    expect(await screen.findByRole("heading", { name: "这个账号已经绑定了" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "你的 GitHub 账号已经绑定了 Patra 账号，可能是在另一个标签页。重新用它登录就行。",
      ),
    ).toBeInTheDocument();
  });

  it("目标账号已绑这一家：结果页，说明怎么换", async () => {
    const { user } = await toLinkStep();
    vi.mocked(postLink).mockResolvedValue({ ok: false, reason: "already-linked" });
    await submitLinkCode(user);
    expect(await screen.findByRole("heading", { name: "没有绑定" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "这个 Patra 账号已经绑定了另一个 GitHub 账号，每家只能绑一个。要换账号，先用原方式登录，到「设置 › 安全」解绑现在这个。",
      ),
    ).toBeInTheDocument();
  });

  it("服务错误：提示条，留在这一步", async () => {
    const { user } = await toLinkStep();
    vi.mocked(postLink).mockResolvedValue({ ok: false, reason: "unavailable" });
    await submitLinkCode(user);
    expect(await screen.findByRole("alert")).toHaveTextContent("没有验证成功，请稍后再试");
    expect(screen.getByLabelText("验证码")).toHaveValue("483920");
  });

  it("重发限流：按限流说", async () => {
    const { user } = renderFlow({ ...GITHUB, pendingCode: LINK_PENDING_READY });
    vi.mocked(postLinkCode).mockResolvedValue({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: 900,
    });
    await user.click(screen.getByRole("button", { name: "重新发送" }));
    expect(await screen.findByText("发送太频繁，请 15 分钟后再试")).toBeInTheDocument();
  });

  it("刷新回来：purpose 是 LINK 时回到绑定的输码步", () => {
    renderFlow({ ...GITHUB, pendingCode: LINK_PENDING_READY });
    expect(screen.getByRole("heading", { name: "验证这个邮箱" })).toBeInTheDocument();
    expect(screen.getByText(LINK_PENDING_READY.email)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "完成绑定" })).toBeInTheDocument();
  });

  it("返回修改邮箱：回补填步，邮箱还在，用户名回到预填值", async () => {
    const { user } = renderFlow({ ...GITHUB, pendingCode: LINK_PENDING_READY });
    await user.click(screen.getByRole("button", { name: "返回修改邮箱" }));
    expect(screen.getByRole("heading", { name: "还差一步" })).toBeInTheDocument();
    expect(screen.getByLabelText("邮箱")).toHaveValue(LINK_PENDING_READY.email);
    expect(screen.getByLabelText("用户名")).toHaveValue("octocat");
  });
});
