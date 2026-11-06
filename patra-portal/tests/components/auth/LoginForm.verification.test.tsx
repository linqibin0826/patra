import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import type { HumanVerificationState } from "@/components/auth/human-verification/use-human-verification";
import { LoginForm } from "@/components/auth/LoginForm";
import { login } from "@/lib/auth/actions";
import { VERIFICATION_MESSAGES } from "@/lib/auth/messages";
import type { HumanVerificationConfig } from "@/lib/auth/types";
import { useAuthHandoffStore } from "@/store/auth-handoff";

const router = { replace: vi.fn(), prefetch: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));
vi.mock("@/components/auth/human-verification/HumanVerificationWidget", () => ({
  HumanVerificationWidget: ({
    config,
    verification,
  }: {
    config: HumanVerificationConfig | null;
    verification: HumanVerificationState;
  }) =>
    verification.required ? (
      <div>
        <span>组件 {config ? config.provider : "不可用"}</span>
        <span>第 {verification.widgetProps.resetKey} 次</span>
        <button type="button" onClick={() => verification.widgetProps.onToken("token-1")}>
          通过验证
        </button>
      </div>
    ) : null,
}));

const CONFIG: HumanVerificationConfig = { provider: "turnstile", siteKey: "site-1" };
const PASSWORD = "correct horse";

function setup(config: HumanVerificationConfig | null = CONFIG) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const user = userEvent.setup();
  render(
    <QueryClientProvider client={client}>
      <LoginForm next="/" verification={config} />
    </QueryClientProvider>,
  );
  return {
    user,
    password: screen.getByLabelText("密码") as HTMLInputElement,
    email: screen.getByLabelText("邮箱") as HTMLInputElement,
    submit: screen.getByRole("button", { name: "登录" }),
  };
}

type Form = ReturnType<typeof setup>;

async function fillAndSubmit(form: Form) {
  await form.user.type(form.email, "reader@example.com");
  await form.user.type(form.password, PASSWORD);
  await form.user.click(form.submit);
}

/// 第一次提交被要求验证，组件显示出来
async function demanded(form: Form) {
  vi.mocked(login).mockResolvedValueOnce({ ok: false, reason: "verification-required" });
  await fillAndSubmit(form);
  await screen.findByText(VERIFICATION_MESSAGES.required);
}

beforeEach(() => {
  vi.mocked(login).mockReset();
  useAuthHandoffStore.setState({ email: null, notice: null });
});

describe("LoginForm 的人机验证", () => {
  it("默认不显示组件，提交不带令牌", async () => {
    vi.mocked(login).mockResolvedValueOnce({ ok: false, reason: "bad-credentials" });
    const form = setup();

    expect(screen.queryByText(/组件/)).not.toBeInTheDocument();
    await fillAndSubmit(form);

    await waitFor(() => expect(login).toHaveBeenCalledOnce());
    expect(vi.mocked(login).mock.calls[0]?.[0]).not.toHaveProperty("verificationToken");
    expect(screen.queryByText(/组件/)).not.toBeInTheDocument();
  });

  it("被要求验证：显示组件，保留已输入的密码", async () => {
    const form = setup();

    await demanded(form);

    expect(screen.getByText("组件 turnstile")).toBeInTheDocument();
    expect(form.password.value).toBe(PASSWORD);
  });

  it("拿到令牌之前不能提交，按钮和回车都不发请求", async () => {
    const form = setup();
    await demanded(form);

    expect(form.submit).toHaveAttribute("aria-disabled", "true");
    await form.user.click(form.submit);
    await form.user.type(form.password, "{Enter}");

    expect(login).toHaveBeenCalledOnce();
  });

  it("拿到令牌后提交带上它", async () => {
    vi.mocked(login).mockResolvedValue({ ok: false, reason: "bad-credentials" });
    const form = setup();
    vi.mocked(login).mockResolvedValueOnce({ ok: false, reason: "verification-required" });
    await fillAndSubmit(form);
    await screen.findByText(VERIFICATION_MESSAGES.required);

    await form.user.click(screen.getByRole("button", { name: "通过验证" }));
    expect(form.submit).not.toHaveAttribute("aria-disabled");
    await form.user.click(form.submit);

    await waitFor(() => expect(login).toHaveBeenCalledTimes(2));
    expect(vi.mocked(login).mock.calls[1]?.[0]).toMatchObject({ verificationToken: "token-1" });
  });

  /// 通过验证后再次提交，后端回给定的结果
  async function resubmit(form: Form, result: Awaited<ReturnType<typeof login>>) {
    await demanded(form);
    await form.user.click(screen.getByRole("button", { name: "通过验证" }));
    vi.mocked(login).mockResolvedValueOnce(result);
    await form.user.click(form.submit);
  }

  it("密码错：清空密码，重置组件，令牌不能再用", async () => {
    const form = setup();

    await resubmit(form, { ok: false, reason: "bad-credentials" });

    await screen.findByText("邮箱或密码错误");
    expect(form.password.value).toBe("");
    expect(screen.getByText(/第 [1-9]/)).toBeInTheDocument();
    expect(form.submit).toHaveAttribute("aria-disabled", "true");
  });

  it("验证没通过：保留密码，重置组件", async () => {
    const form = setup();

    await resubmit(form, { ok: false, reason: "verification-failed" });

    await screen.findByText(VERIFICATION_MESSAGES.failed);
    expect(form.password.value).toBe(PASSWORD);
    expect(form.submit).toHaveAttribute("aria-disabled", "true");
  });

  it("验证不可用：保留密码，重置组件", async () => {
    const form = setup();

    await resubmit(form, { ok: false, reason: "verification-unavailable" });

    await screen.findByText(VERIFICATION_MESSAGES.unavailable);
    expect(form.password.value).toBe(PASSWORD);
    expect(form.submit).toHaveAttribute("aria-disabled", "true");
  });

  it("账号锁定：提示和验证问题不同，也重置组件", async () => {
    const form = setup();

    await resubmit(form, { ok: false, reason: "throttled", retryAfterSeconds: 900 });

    await screen.findByText("尝试次数过多，请 15 分钟后再试");
    expect(form.submit).toHaveAttribute("aria-disabled", "true");
  });

  it("读不到厂商配置时被要求验证：显示不可用，不能提交", async () => {
    const form = setup(null);

    await demanded(form);

    expect(screen.getByText("组件 不可用")).toBeInTheDocument();
    expect(form.submit).toHaveAttribute("aria-disabled", "true");
  });
});
