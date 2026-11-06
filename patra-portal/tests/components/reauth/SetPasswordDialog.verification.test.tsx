import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { SetPasswordDialog } from "@/components/reauth/SetPasswordDialog";
import { fetchHumanVerification } from "@/lib/auth/human-verification-actions";
import { VERIFICATION_MESSAGES } from "@/lib/auth/messages";
import { requestPasswordReset } from "@/lib/identity/token-actions";

vi.mock("@/lib/identity/token-actions", () => ({ requestPasswordReset: vi.fn() }));
vi.mock("@/lib/auth/human-verification-actions", () => ({ fetchHumanVerification: vi.fn() }));
vi.mock(
  "@/components/auth/human-verification/HumanVerificationWidget",
  () => import("@tests/stubs/manual-human-verification-widget"),
);

const EMAIL = "chen@example.com";
const CONFIG = { provider: "turnstile", siteKey: "site-1" } as const;
/// 冷却早已结束
const SENT = { ok: true, email: EMAIL, nextRequestAt: "2020-01-01T00:00:00Z" } as const;

const dialog = (open: boolean) => (
  <SetPasswordDialog open={open} onClose={vi.fn()} email={EMAIL} reason={false} />
);

function setup() {
  const view = render(dialog(true));
  return { ...view, user: userEvent.setup() };
}

const sendButton = () => screen.getByRole("button", { name: "发送设置邮件" });
const pass = () => screen.findByRole("button", { name: "通过验证" });

beforeEach(() => {
  vi.mocked(fetchHumanVerification).mockResolvedValue(CONFIG);
});

afterEach(() => vi.clearAllMocks());

describe("SetPasswordDialog 的人机验证", () => {
  it("每次打开都读一次厂商配置", async () => {
    const { rerender } = setup();
    await pass();

    rerender(dialog(false));
    rerender(dialog(true));

    await waitFor(() => expect(fetchHumanVerification).toHaveBeenCalledTimes(2));
  });

  it("读取期间不能发送", async () => {
    vi.mocked(fetchHumanVerification).mockReturnValue(new Promise(() => {}));
    const { user } = setup();

    expect(sendButton()).toHaveAttribute("aria-disabled", "true");
    await user.click(sendButton());

    expect(requestPasswordReset).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: "通过验证" })).not.toBeInTheDocument();
  });

  it("读取失败按读不到处理：显示不可用，不能发送", async () => {
    vi.mocked(fetchHumanVerification).mockRejectedValue(new Error("network"));
    setup();

    expect(await screen.findByText(/组件 不可用/)).toBeInTheDocument();
    expect(sendButton()).toHaveAttribute("aria-disabled", "true");
  });

  it("读到 null：显示不可用，不能发送", async () => {
    vi.mocked(fetchHumanVerification).mockResolvedValue(null);
    setup();

    expect(await screen.findByText(/组件 不可用/)).toBeInTheDocument();
    expect(sendButton()).toHaveAttribute("aria-disabled", "true");
  });

  it("拿到令牌之前不能发送，发送带上令牌", async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue(SENT);
    const { user } = setup();
    const button = await pass();

    await user.click(sendButton());
    expect(requestPasswordReset).not.toHaveBeenCalled();

    await user.click(button);
    expect(sendButton()).not.toHaveAttribute("aria-disabled");
    await user.click(sendButton());

    await waitFor(() => expect(requestPasswordReset).toHaveBeenCalledWith(EMAIL, "token-0"));
  });

  it("失败后令牌不能再用，并显示验证的提示", async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue({
      ok: false,
      reason: "verification-failed",
    });
    const { user } = setup();
    await user.click(await pass());

    await user.click(sendButton());

    expect(await screen.findByText(VERIFICATION_MESSAGES.failed)).toBeInTheDocument();
    expect(sendButton()).toHaveAttribute("aria-disabled", "true");
  });

  it("重新发送要新令牌", async () => {
    vi.mocked(requestPasswordReset).mockResolvedValue(SENT);
    const { user } = setup();
    await user.click(await pass());
    await user.click(sendButton());
    await screen.findByRole("dialog", { name: "查看你的邮箱" });

    expect(screen.queryByRole("button", { name: "重新发送" })).not.toBeInTheDocument();
    await user.click(await pass());
    await user.click(await screen.findByRole("button", { name: "重新发送" }));

    await waitFor(() => expect(requestPasswordReset).toHaveBeenCalledTimes(2));
    expect(vi.mocked(requestPasswordReset).mock.calls[1]).toEqual([EMAIL, "token-1"]);
  });
});
