import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { register } from "@/lib/auth/actions";
import { VERIFICATION_MESSAGES } from "@/lib/auth/messages";
import type { RegisterResult } from "@/lib/auth/types";

const router = { replace: vi.fn(), prefetch: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/auth/actions", () => ({ login: vi.fn(), logout: vi.fn(), register: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn() } }));
vi.mock(
  "@/components/auth/human-verification/HumanVerificationWidget",
  () => import("@tests/stubs/manual-human-verification-widget"),
);

const PASSWORD = "correct horse battery";

function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <RegisterForm next="/" verification={{ provider: "turnstile", siteKey: "site-1" }} />
    </QueryClientProvider>,
  );
  return { user: userEvent.setup(), submit: screen.getByRole("button", { name: "注册" }) };
}

type Form = ReturnType<typeof setup>;

async function fill(form: Form) {
  await form.user.type(screen.getByLabelText("邮箱"), "reader@example.com");
  await form.user.type(screen.getByLabelText("用户名"), "reader");
  await form.user.type(screen.getByLabelText("密码", { exact: true }), PASSWORD);
  await form.user.type(screen.getByLabelText("确认密码"), PASSWORD);
}

const pass = (form: Form) => form.user.click(screen.getByRole("button", { name: "通过验证" }));

beforeEach(() => {
  vi.mocked(register).mockReset();
});

describe("RegisterForm 的人机验证", () => {
  it("组件一直显示，操作是 register", () => {
    setup();

    expect(screen.getByText("组件 turnstile register")).toBeInTheDocument();
  });

  it("拿到令牌之前不能提交", async () => {
    const form = setup();
    await fill(form);

    expect(form.submit).toHaveAttribute("aria-disabled", "true");
    await form.user.click(form.submit);

    expect(register).not.toHaveBeenCalled();
  });

  it("提交带上令牌", async () => {
    vi.mocked(register).mockResolvedValue({ ok: false, reason: "unavailable" });
    const form = setup();
    await fill(form);
    await pass(form);

    await form.user.click(form.submit);

    await waitFor(() => expect(register).toHaveBeenCalledOnce());
    expect(vi.mocked(register).mock.calls[0]?.[0]).toMatchObject({
      email: "reader@example.com",
      verificationToken: "token-0",
    });
  });

  it.each<[string, RegisterResult]>([
    ["字段错误", { ok: false, reason: "invalid-fields", fieldErrors: [] }],
    ["被占用", { ok: false, reason: "taken", email: true, username: false }],
    ["服务不可用", { ok: false, reason: "unavailable" }],
    ["验证没通过", { ok: false, reason: "verification-failed" }],
  ])("%s：令牌不能再用，要重新验证", async (_name, result) => {
    vi.mocked(register).mockResolvedValue(result);
    const form = setup();
    await fill(form);
    await pass(form);

    await form.user.click(form.submit);

    await waitFor(() => expect(form.submit).toHaveAttribute("aria-disabled", "true"));
    await pass(form);
    await form.user.click(form.submit);
    await waitFor(() => expect(register).toHaveBeenCalledTimes(2));
    expect(vi.mocked(register).mock.calls[1]?.[0]).toMatchObject({
      verificationToken: "token-1",
    });
  });

  it.each([
    ["verification-required", VERIFICATION_MESSAGES.required],
    ["verification-failed", VERIFICATION_MESSAGES.failed],
    ["verification-unavailable", VERIFICATION_MESSAGES.unavailable],
  ] as const)("%s 显示验证的提示", async (reason, message) => {
    vi.mocked(register).mockResolvedValue({ ok: false, reason });
    const form = setup();
    await fill(form);
    await pass(form);

    await form.user.click(form.submit);

    expect(await screen.findByText(message)).toBeInTheDocument();
  });
});
