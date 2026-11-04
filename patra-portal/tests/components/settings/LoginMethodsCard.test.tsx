import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { LoginMethodsCard } from "@/components/settings/security/LoginMethodsCard";
import type { OAuthNotice } from "@/lib/auth/oauth-notice";
import { startProviderLink, unlinkProvider } from "@/lib/identity/login-method-actions";
import { leaveSite } from "@/lib/leave-site";
import { useReauthStore } from "@/store/reauth";

const router = { replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/settings/security",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/identity/login-method-actions", () => ({
  startProviderLink: vi.fn(),
  unlinkProvider: vi.fn(),
}));
vi.mock("@/lib/identity/token-actions", () => ({ requestPasswordReset: vi.fn() }));
vi.mock("@/lib/identity/verification-actions", () => ({ resendVerificationEmail: vi.fn() }));
vi.mock("@/lib/leave-site", () => ({ leaveSite: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { toast } from "sonner";

const fetchMock = vi.fn<typeof fetch>();
const GITHUB = {
  provider: "github",
  accountLabel: "chenyu-med",
  linkedAt: "2026-10-21T00:00:00Z",
  lastLoginAt: null,
};
const GOOGLE = {
  provider: "google",
  accountLabel: "chen@example.com",
  linkedAt: "2026-10-30T00:00:00Z",
  lastLoginAt: null,
};
const ORCID = {
  provider: "orcid",
  accountLabel: "0000-0002-1825-0097",
  linkedAt: "2026-11-02T00:00:00Z",
  lastLoginAt: null,
};

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  window.history.replaceState(null, "", "/settings/security");
});

afterEach(() => {
  act(() => useReauthStore.getState().settle(false));
  vi.unstubAllGlobals();
  vi.clearAllMocks();
  fetchMock.mockReset();
});

function methods(hasPassword: boolean, linked: unknown[]) {
  fetchMock.mockImplementation(async () => Response.json({ hasPassword, linked }));
}

function renderCard(
  props: { oauthEnabled?: boolean; emailVerified?: boolean; notice?: OAuthNotice | null } = {},
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <LoginMethodsCard
        oauthEnabled={props.oauthEnabled ?? true}
        email="chen@example.com"
        emailVerified={props.emailVerified ?? true}
        notice={props.notice ?? null}
      />
    </QueryClientProvider>,
  );
  return { user: userEvent.setup(), client };
}

/// 列表里的一行
async function row(name: string) {
  const list = await screen.findByRole("list", { name: "登录方式" });
  const item = within(list)
    .getAllByRole("listitem")
    .find((li) => within(li).queryByText(name));
  if (!item) throw new Error(`没有 ${name} 这一行`);
  return within(item);
}

describe("显示", () => {
  it("有密码，绑了 GitHub：四行，账号标识和绑定日期", async () => {
    methods(true, [GITHUB]);
    renderCard();
    expect((await row("邮箱和密码")).getByText("已设置")).toBeInTheDocument();
    const github = await row("GitHub");
    expect(github.getByText("chenyu-med")).toBeInTheDocument();
    expect(github.getByText("绑定于 2026-10-21")).toBeInTheDocument();
    expect(github.getByRole("button", { name: "解绑 GitHub" })).toBeInTheDocument();
    expect((await row("Google")).getByText("未绑定")).toBeInTheDocument();
    expect((await row("ORCID")).getByText("未绑定 · 科研人员身份标识")).toBeInTheDocument();
    expect((await row("ORCID")).getByRole("button", { name: "绑定 ORCID" })).toBeInTheDocument();
    expect(
      screen.getByText("这些方式都能登录这个账号。绑定和解绑要先验证密码，并会发一封通知邮件。"),
    ).toBeInTheDocument();
  });

  it("ORCID 的账号标识链到它的主页", async () => {
    methods(true, [ORCID]);
    renderCard();
    expect((await row("ORCID")).getByRole("link", { name: ORCID.accountLabel })).toHaveAttribute(
      "href",
      `https://orcid.org/${ORCID.accountLabel}`,
    );
  });

  it("没有密码：显示设置密码按钮，说明要先设置", async () => {
    methods(false, [GITHUB, GOOGLE]);
    renderCard();
    const password = await row("邮箱和密码");
    expect(password.getByText("还没有设置")).toBeInTheDocument();
    expect(password.getByRole("button", { name: "设置密码" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "这些方式都能登录这个账号。绑定和解绑要先验证密码，你还没有密码，需要先设置一个。",
      ),
    ).toBeInTheDocument();
  });

  it("加载中是骨架，失败可以重试", async () => {
    fetchMock.mockImplementation(async () => new Response(null, { status: 502 }));
    const { user } = renderCard();
    expect(screen.getByRole("status", { name: "正在加载登录方式" })).toBeInTheDocument();
    expect(await screen.findByRole("alert", undefined, { timeout: 3_000 })).toHaveTextContent(
      "登录方式加载失败",
    );
    methods(true, []);
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByRole("list", { name: "登录方式" })).toBeInTheDocument();
  });

  it("会话没了：去登录页", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 401 }));
    renderCard();
    await waitFor(() => expect(router.replace).toHaveBeenCalled());
  });
});

describe("tailnet 地址", () => {
  it("没有绑定按钮，有说明；解绑照常", async () => {
    methods(true, [GITHUB]);
    renderCard({ oauthEnabled: false });
    const google = await row("Google");
    expect(google.queryByRole("button", { name: "绑定 Google" })).not.toBeInTheDocument();
    expect(screen.getByText("要绑定新账号，请到 patra.linqibin.dev 操作。")).toBeInTheDocument();
    expect((await row("GitHub")).getByRole("button", { name: "解绑 GitHub" })).toBeInTheDocument();
  });

  it("邮箱未验证也不显示警示条", async () => {
    methods(true, []);
    renderCard({ oauthEnabled: false, emailVerified: false });
    await screen.findByRole("list", { name: "登录方式" });
    expect(screen.queryByText(/绑定前要先验证邮箱/)).not.toBeInTheDocument();
  });
});

describe("绑定", () => {
  it("成功发起：显示正在前往，整页跳走", async () => {
    methods(true, []);
    const { user } = renderCard();
    vi.mocked(startProviderLink).mockResolvedValue({
      ok: true,
      authorizationUrl: "https://orcid.org/oauth/authorize?state=s",
    });
    await user.click((await row("ORCID")).getByRole("button", { name: "绑定 ORCID" }));
    expect(startProviderLink).toHaveBeenCalledWith("orcid");
    await waitFor(() =>
      expect(leaveSite).toHaveBeenCalledWith("https://orcid.org/oauth/authorize?state=s"),
    );
    expect(screen.getByRole("button", { name: "绑定 ORCID" })).toHaveTextContent("正在前往 ORCID…");
  });

  it("要再认证：弹框，验证后重试", async () => {
    methods(true, []);
    const { user } = renderCard();
    vi.mocked(startProviderLink)
      .mockResolvedValueOnce({ ok: false, reason: "reauth-required" })
      .mockResolvedValueOnce({ ok: true, authorizationUrl: "https://x.test/?state=s" });
    await user.click((await row("GitHub")).getByRole("button", { name: "绑定 GitHub" }));
    await waitFor(() => expect(useReauthStore.getState().open).toBe(true));
    act(() => useReauthStore.getState().settle(true));
    await waitFor(() => expect(leaveSite).toHaveBeenCalledWith("https://x.test/?state=s"));
  });

  it("取消再认证：什么也不显示，按钮复位", async () => {
    methods(true, []);
    const { user } = renderCard();
    vi.mocked(startProviderLink).mockResolvedValue({ ok: false, reason: "reauth-required" });
    await user.click((await row("GitHub")).getByRole("button", { name: "绑定 GitHub" }));
    await waitFor(() => expect(useReauthStore.getState().open).toBe(true));
    act(() => useReauthStore.getState().settle(false));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "绑定 GitHub" })).toHaveTextContent("绑定"),
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it.each([
    ["already-linked", "你已经绑定了一个 GitHub 账号，每家只能绑一个。要换账号，先解绑现在这个。"],
    ["unavailable", "绑定没有成功，请稍后再试"],
  ] as const)("%s：提示条", async (reason, message) => {
    methods(true, []);
    const { user } = renderCard();
    vi.mocked(startProviderLink).mockResolvedValue({ ok: false, reason });
    await user.click((await row("GitHub")).getByRole("button", { name: "绑定 GitHub" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
  });

  it("邮箱未验证：警示条带重发，绑定按钮不可点", async () => {
    methods(true, []);
    const { user } = renderCard({ emailVerified: false });
    const button = (await row("GitHub")).getByRole("button", { name: "绑定 GitHub" });
    expect(button).toHaveAttribute("aria-disabled", "true");
    expect(
      screen.getByText(/绑定前要先验证邮箱。验证邮件已发到 chen@example.com。/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "重新发送" })).toBeInTheDocument();
    await user.click(button);
    expect(startProviderLink).not.toHaveBeenCalled();
  });

  it("服务端才发现邮箱未验证：同样显示警示条", async () => {
    methods(true, []);
    const { user } = renderCard();
    vi.mocked(startProviderLink).mockResolvedValue({ ok: false, reason: "email-not-verified" });
    await user.click((await row("GitHub")).getByRole("button", { name: "绑定 GitHub" }));
    expect(await screen.findByText(/绑定前要先验证邮箱/)).toBeInTheDocument();
  });

  it("没有密码：不调接口，直接开设置密码", async () => {
    methods(false, [GOOGLE]);
    const { user } = renderCard();
    await user.click((await row("GitHub")).getByRole("button", { name: "绑定 GitHub" }));
    expect(await screen.findByRole("dialog", { name: "先设置一个密码" })).toBeInTheDocument();
    expect(startProviderLink).not.toHaveBeenCalled();
  });

  it("冷静期：去取消注销页", async () => {
    methods(true, []);
    const { user } = renderCard();
    vi.mocked(startProviderLink).mockResolvedValue({ ok: false, reason: "pending-deletion" });
    await user.click((await row("GitHub")).getByRole("button", { name: "绑定 GitHub" }));
    await waitFor(() => expect(router.replace).toHaveBeenCalled());
  });
});

describe("从第三方回来", () => {
  it("绑定成功：toast，清地址栏参数", async () => {
    methods(true, [ORCID]);
    window.history.replaceState(null, "", "/settings/security?oauth=linked&provider=orcid");
    renderCard({ notice: { kind: "linked", provider: "orcid" } });
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledExactlyOnceWith(
        "已绑定 ORCID，以后可以用它登录。通知邮件已发到你的邮箱。",
      ),
    );
    expect(window.location.search).toBe("");
  });

  it.each([
    [
      { kind: "taken", provider: "github" },
      "alert",
      "你刚才授权的 GitHub 账号已经绑定了另一个 Patra 账号",
    ],
    [{ kind: "cancelled", provider: "github" }, "status", "你取消了 GitHub 授权，没有绑定。"],
    [{ kind: "rejected", provider: "orcid" }, "alert", "这次授权没有成功"],
    [{ kind: "unreachable", provider: "orcid" }, "alert", "暂时连不上 ORCID，没有绑定。"],
  ] as const)("%j：提示条", async (notice, role, message) => {
    methods(true, []);
    renderCard({ notice });
    await screen.findByRole("list", { name: "登录方式" });
    expect(screen.getByRole(role, { name: "" })).toHaveTextContent(message);
  });
});

describe("解绑", () => {
  it("确认后调用；成功提示并刷新列表", async () => {
    methods(true, [GITHUB]);
    const { user } = renderCard();
    vi.mocked(unlinkProvider).mockResolvedValue({ ok: true });
    await user.click((await row("GitHub")).getByRole("button", { name: "解绑 GitHub" }));
    const dialog = await screen.findByRole("dialog", { name: "解绑 GitHub 账号？" });
    expect(dialog).toHaveTextContent("解绑后，不能再用 GitHub 账号 chenyu-med 登录 Patra。");
    expect(dialog).toHaveTextContent("通知邮件会发到 chen@example.com");
    expect(unlinkProvider).not.toHaveBeenCalled();
    methods(true, []);
    await user.click(within(dialog).getByRole("button", { name: "解绑" }));
    await waitFor(() => expect(unlinkProvider).toHaveBeenCalledWith("github"));
    await waitFor(() =>
      expect(toast.success).toHaveBeenCalledWith("已解绑 GitHub。通知邮件已发到你的邮箱。"),
    );
    await waitFor(async () =>
      expect((await row("GitHub")).getByText("未绑定")).toBeInTheDocument(),
    );
  });

  it("取消确认：不调用", async () => {
    methods(true, [GITHUB]);
    const { user } = renderCard();
    await user.click((await row("GitHub")).getByRole("button", { name: "解绑 GitHub" }));
    const dialog = await screen.findByRole("dialog", { name: "解绑 GitHub 账号？" });
    await user.click(within(dialog).getByRole("button", { name: "取消" }));
    expect(unlinkProvider).not.toHaveBeenCalled();
  });

  it("没有密码且只剩一种：不可点，说明原因和办法", async () => {
    methods(false, [GITHUB]);
    const { user } = renderCard();
    const github = await row("GitHub");
    const button = github.getByRole("button", { name: "解绑 GitHub" });
    expect(button).toHaveAttribute("aria-disabled", "true");
    expect(github.getByText(/这是你现在唯一的登录方式，解绑后就登录不了了。/)).toBeInTheDocument();
    await user.click(button);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(github.getByRole("button", { name: "设置密码" }));
    expect(await screen.findByRole("dialog", { name: "设置密码" })).toBeInTheDocument();
  });

  it("没有密码但绑了两家：开设置密码，不调接口", async () => {
    methods(false, [GITHUB, GOOGLE]);
    const { user } = renderCard();
    await user.click((await row("GitHub")).getByRole("button", { name: "解绑 GitHub" }));
    expect(await screen.findByRole("dialog", { name: "先设置一个密码" })).toBeInTheDocument();
    expect(unlinkProvider).not.toHaveBeenCalled();
  });

  it.each([
    ["last-method", "这是你现在唯一的登录方式，不能解绑。先设置密码，之后可以解绑。"],
    ["unavailable", "解绑没有成功，请稍后再试"],
  ] as const)("%s：提示条", async (reason, message) => {
    methods(true, [GITHUB]);
    const { user } = renderCard();
    vi.mocked(unlinkProvider).mockResolvedValue({ ok: false, reason });
    await user.click((await row("GitHub")).getByRole("button", { name: "解绑 GitHub" }));
    const dialog = await screen.findByRole("dialog", { name: "解绑 GitHub 账号？" });
    await user.click(within(dialog).getByRole("button", { name: "解绑" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
  });
});
