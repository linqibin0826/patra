import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { SetPasswordDialog } from "@/components/reauth/SetPasswordDialog";
import { requestPasswordReset } from "@/lib/identity/token-actions";

vi.mock(
  "@/components/auth/human-verification/HumanVerificationWidget",
  () => import("@tests/stubs/human-verification-widget"),
);
vi.mock("@/lib/auth/human-verification-actions", () => ({
  fetchHumanVerification: vi.fn(async () => ({ provider: "turnstile", siteKey: "site-1" })),
}));
vi.mock("@/lib/identity/token-actions", () => ({ requestPasswordReset: vi.fn() }));

afterEach(() => vi.clearAllMocks());

const EMAIL = "chen@example.com";
const SENT = { ok: true, email: EMAIL, nextRequestAt: "2099-01-01T00:00:00Z" } as const;

function renderDialog(reason = true) {
  const onClose = vi.fn();
  render(<SetPasswordDialog open onClose={onClose} email={EMAIL} reason={reason} />);
  return { onClose, user: userEvent.setup() };
}

describe("SetPasswordDialog", () => {
  it("从敏感操作进来：说明为什么要设置，和所有设备会下线，没有密码框", () => {
    renderDialog(true);
    const dialog = screen.getByRole("dialog", { name: "先设置一个密码" });
    expect(dialog).toHaveTextContent("这项操作要先验证密码，但你的账号还没有密码。");
    expect(dialog).toHaveTextContent(`我们发一封邮件到 ${EMAIL}，点邮件里的链接设置密码。`);
    expect(dialog).toHaveTextContent("设置后，所有设备都会退出登录，包括这台。");
    expect(dialog).toHaveTextContent("用新密码重新登录，再回来完成刚才的操作。");
    expect(within(dialog).queryByLabelText("密码")).not.toBeInTheDocument();
  });

  it("从设置密码按钮进来：普通说明", () => {
    renderDialog(false);
    const dialog = screen.getByRole("dialog", { name: "设置密码" });
    expect(dialog).toHaveTextContent("有了密码，就能用邮箱和密码登录");
    expect(dialog).toHaveTextContent("用新密码或第三方账号重新登录。");
  });

  it("发送：调找回密码接口，用当前账号邮箱，进入已发送态", async () => {
    const { user } = renderDialog();
    vi.mocked(requestPasswordReset).mockResolvedValue(SENT);
    await user.click(screen.getByRole("button", { name: "发送设置邮件" }));
    expect(requestPasswordReset).toHaveBeenCalledWith(EMAIL, "token-0");
    const dialog = await screen.findByRole("dialog", { name: "查看你的邮箱" });
    expect(dialog).toHaveTextContent(`设置密码的邮件已发到 ${EMAIL}，链接 1 小时内有效。`);
    expect(dialog).toHaveTextContent(/已发送 · \d+ 秒后可重发/);
  });

  it("已发送态：好的关闭；冷却结束可以重发", async () => {
    const { user, onClose } = renderDialog();
    vi.mocked(requestPasswordReset).mockResolvedValue({
      ...SENT,
      nextRequestAt: "2000-01-01T00:00:00Z",
    });
    await user.click(screen.getByRole("button", { name: "发送设置邮件" }));
    await user.click(await screen.findByRole("button", { name: "重新发送" }));
    expect(requestPasswordReset).toHaveBeenCalledTimes(2);
    await user.click(await screen.findByRole("button", { name: "好的" }));
    expect(onClose).toHaveBeenCalled();
  });

  it.each([
    [{ ok: false, reason: "throttled", retryAfterSeconds: 900 }, "发送太频繁，请 15 分钟后再试"],
    [{ ok: false, reason: "unavailable" }, "没有发出去，请稍后再试"],
    [{ ok: false, reason: "invalid" }, "没有发出去，请稍后再试"],
  ] as const)("发送失败 %j：留在这一步，显示原因", async (result, message) => {
    const { user } = renderDialog();
    vi.mocked(requestPasswordReset).mockResolvedValue(result);
    await user.click(screen.getByRole("button", { name: "发送设置邮件" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.getByRole("button", { name: "发送设置邮件" })).toBeInTheDocument();
  });

  it("询问态：初始焦点在发送设置邮件", async () => {
    renderDialog();
    await waitFor(() => expect(screen.getByRole("button", { name: "发送设置邮件" })).toHaveFocus());
  });

  it("倒计时不每秒朗读", async () => {
    const { user } = renderDialog();
    vi.mocked(requestPasswordReset).mockResolvedValue(SENT);
    await user.click(screen.getByRole("button", { name: "发送设置邮件" }));
    const countdown = await screen.findByText(/^已发送 · \d+ 秒后可重发$/);
    expect(countdown.closest("[aria-live]")).toBeNull();
  });

  it("取消：关闭", async () => {
    const { user, onClose } = renderDialog();
    await user.click(screen.getByRole("button", { name: "取消" }));
    expect(onClose).toHaveBeenCalled();
  });
});
