import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ResetPasswordLanding } from "@/components/landing/ResetPasswordLanding";
import { resetPassword } from "@/lib/identity/token-actions";
import { queryKeys } from "@/lib/query-keys";
import { useAuthHandoffStore } from "@/store/auth-handoff";

const router = { replace: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/identity/token-actions", () => ({ resetPassword: vi.fn() }));

const TOKEN = "t".repeat(43);

afterEach(() => {
  useAuthHandoffStore.getState().take();
  vi.clearAllMocks();
});

function renderPage() {
  window.history.replaceState(null, "", `/reset-password#token=${TOKEN}`);
  const client = new QueryClient();
  client.setQueryData(queryKeys.currentUser, USER);
  const invalidate = vi.spyOn(client, "invalidateQueries");
  render(
    <QueryClientProvider client={client}>
      <ResetPasswordLanding />
    </QueryClientProvider>,
  );
  return { user: userEvent.setup(), invalidate };
}

async function fill(
  user: ReturnType<typeof userEvent.setup>,
  password: string,
  confirm = password,
) {
  await user.type(await screen.findByLabelText("新密码"), password);
  await user.type(screen.getByLabelText("确认新密码"), confirm);
  await user.click(screen.getByRole("button", { name: "设置新密码" }));
}

describe("ResetPasswordLanding", () => {
  it("就绪态：标题、说明、两个密码框，焦点在新密码", async () => {
    renderPage();
    expect(
      await screen.findByRole("heading", { level: 1, name: "设置新密码" }),
    ).toBeInTheDocument();
    expect(screen.getByText("提交后，所有设备都会退出登录。")).toBeInTheDocument();
    expect(screen.getByText("至少 8 位")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText("新密码")).toHaveFocus());
  });

  it("提交中：两个密码框都只读", async () => {
    vi.mocked(resetPassword).mockReturnValue(new Promise(() => {}));
    const { user } = renderPage();
    await fill(user, "new-pass-1");
    expect(await screen.findByRole("button", { name: "提交中…" })).toBeInTheDocument();
    expect(screen.getByLabelText("新密码")).toHaveAttribute("readonly");
    expect(screen.getByLabelText("确认新密码")).toHaveAttribute("readonly");
  });

  it("两次不一致：就地提示，不提交", async () => {
    const { user } = renderPage();
    await fill(user, "new-pass-1", "new-pass-2");
    expect(await screen.findByText("两次输入的密码不一致")).toBeInTheDocument();
    expect(resetPassword).not.toHaveBeenCalled();
  });

  it("成功：带邮箱与提示去登录页，失效当前用户", async () => {
    vi.mocked(resetPassword).mockResolvedValue({ ok: true, email: "a@b.com" });
    const { user, invalidate } = renderPage();
    await fill(user, "new-pass-1");
    expect(resetPassword).toHaveBeenCalledWith({ token: TOKEN, newPassword: "new-pass-1" });
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login"));
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.currentUser });
    expect(useAuthHandoffStore.getState().take()).toEqual({
      email: "a@b.com",
      notice: "password-reset",
    });
  });

  it("密码太常见：新密码下方提示，留在就绪态", async () => {
    vi.mocked(resetPassword).mockResolvedValue({
      ok: false,
      reason: "invalid-password",
      code: "TOO_COMMON",
    });
    const { user } = renderPage();
    await fill(user, "password1");
    expect(await screen.findByText("这个密码太常见，容易被猜到，请换一个")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("429：提示条按分钟说", async () => {
    vi.mocked(resetPassword).mockResolvedValue({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: 125,
    });
    const { user } = renderPage();
    await fill(user, "new-pass-1");
    expect(await screen.findByRole("alert")).toHaveTextContent("提交太频繁，请 3 分钟后再试");
  });

  it("链接无效：无效屏，重新申请", async () => {
    vi.mocked(resetPassword).mockResolvedValue({ ok: false, reason: "invalid" });
    const { user } = renderPage();
    await fill(user, "new-pass-1");
    expect(
      await screen.findByRole("heading", { level: 1, name: "链接无效或已过期" }),
    ).toBeInTheDocument();
    expect(screen.getByText("重设链接 1 小时内有效，而且只能用一次。")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "重新申请" })).toHaveAttribute(
      "href",
      "/forgot-password",
    );
  });
});
