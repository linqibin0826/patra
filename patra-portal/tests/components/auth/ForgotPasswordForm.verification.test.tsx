import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ForgotPasswordForm } from "@/components/auth/ForgotPasswordForm";
import { VERIFICATION_MESSAGES } from "@/lib/auth/messages";
import { requestPasswordReset } from "@/lib/identity/token-actions";
import { useAuthHandoffStore } from "@/store/auth-handoff";

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn(), prefetch: vi.fn() }) }));
vi.mock("@/lib/identity/token-actions", () => ({ requestPasswordReset: vi.fn() }));
vi.mock(
  "@/components/auth/human-verification/HumanVerificationWidget",
  () => import("@tests/stubs/manual-human-verification-widget"),
);

const EMAIL = "reader@example.com";
const OK = { ok: true, email: EMAIL, nextRequestAt: "2020-01-01T00:00:00Z" } as const;

function setup() {
  render(
    <ForgotPasswordForm next="/" verification={{ provider: "turnstile", siteKey: "site-1" }} />,
  );
  return {
    user: userEvent.setup(),
    submit: screen.getByRole("button", { name: "发送重设邮件" }),
  };
}

type Form = ReturnType<typeof setup>;

const pass = (form: Form) => form.user.click(screen.getByRole("button", { name: "通过验证" }));

beforeEach(() => {
  vi.mocked(requestPasswordReset).mockReset();
  act(() => useAuthHandoffStore.setState({ email: null, notice: null }));
});

describe("ForgotPasswordForm 的人机验证", () => {
  it("组件一直显示，操作是 password_reset", () => {
    setup();

    expect(screen.getByText("组件 turnstile password_reset")).toBeInTheDocument();
  });

  it("拿到令牌之前不能提交", async () => {
    const form = setup();
    await form.user.type(screen.getByLabelText("邮箱"), EMAIL);

    expect(form.submit).toHaveAttribute("aria-disabled", "true");
    await form.user.click(form.submit);

    expect(requestPasswordReset).not.toHaveBeenCalled();
  });

  it("提交带上令牌", async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue(OK);
    const form = setup();
    await form.user.type(screen.getByLabelText("邮箱"), EMAIL);
    await pass(form);

    await form.user.click(form.submit);

    await waitFor(() => expect(requestPasswordReset).toHaveBeenCalledWith(EMAIL, "token-0"));
  });

  it("失败后令牌不能再用", async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue({ ok: false, reason: "unavailable" });
    const form = setup();
    await form.user.type(screen.getByLabelText("邮箱"), EMAIL);
    await pass(form);

    await form.user.click(form.submit);

    await waitFor(() => expect(form.submit).toHaveAttribute("aria-disabled", "true"));
  });

  it.each([
    ["verification-failed", VERIFICATION_MESSAGES.failed],
    ["verification-unavailable", VERIFICATION_MESSAGES.unavailable],
  ] as const)("%s 显示验证的提示", async (reason, message) => {
    vi.mocked(requestPasswordReset).mockResolvedValue({ ok: false, reason });
    const form = setup();
    await form.user.type(screen.getByLabelText("邮箱"), EMAIL);
    await pass(form);

    await form.user.click(form.submit);

    expect(await screen.findByText(message)).toBeInTheDocument();
  });

  it("重新发送要新令牌：倒计时结束后先验证，才有重新发送按钮", async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue(OK);
    const form = setup();
    await form.user.type(screen.getByLabelText("邮箱"), EMAIL);
    await pass(form);
    await form.user.click(form.submit);
    await screen.findByText("查看你的邮箱");

    expect(screen.queryByRole("button", { name: "重新发送" })).not.toBeInTheDocument();
    await pass(form);

    await form.user.click(await screen.findByRole("button", { name: "重新发送" }));
    await waitFor(() => expect(requestPasswordReset).toHaveBeenCalledTimes(2));
    expect(vi.mocked(requestPasswordReset).mock.calls[1]).toEqual([EMAIL, "token-1"]);
  });
});
