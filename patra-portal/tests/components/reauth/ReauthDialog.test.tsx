import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ReauthDialog } from "@/components/reauth/ReauthDialog";
import { queryKeys } from "@/lib/query-keys";
import { postReauth as reauthenticate } from "@/lib/reauth/post-reauth";
import { useReauthStore } from "@/store/reauth";

const router = { replace: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => router,
  usePathname: () => "/settings/security",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/reauth/post-reauth", () => ({ postReauth: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { toast } from "sonner";

afterEach(() => {
  act(() => useReauthStore.getState().settle(false));
  vi.clearAllMocks();
});

/// 渲染对话框并发起一次再认证请求，返回等待结果的 promise
function openDialog() {
  const client = new QueryClient();
  client.setQueryData(queryKeys.currentUser, USER);
  render(
    <QueryClientProvider client={client}>
      <ReauthDialog />
    </QueryClientProvider>,
  );
  let outcome!: Promise<boolean>;
  act(() => {
    outcome = useReauthStore.getState().request();
  });
  return { client, outcome };
}

async function submit(password: string) {
  const user = userEvent.setup();
  const input = await screen.findByLabelText("密码");
  if (password) await user.type(input, password);
  await user.click(screen.getByRole("button", { name: "确认" }));
  return { user, input };
}

describe("ReauthDialog", () => {
  it("标题、说明、忘记密码新标签页打开，焦点在密码框", async () => {
    openDialog();
    expect(await screen.findByRole("dialog", { name: "确认是你本人" })).toHaveAccessibleDescription(
      "这项操作需要验证密码。验证后 10 分钟内不用再输。",
    );
    const forgot = screen.getByRole("link", { name: "忘记密码？" });
    expect(forgot).toHaveAttribute("href", "/forgot-password");
    expect(forgot).toHaveAttribute("target", "_blank");
    expect(forgot.getAttribute("rel")).toContain("noopener");
    await waitFor(() => expect(screen.getByLabelText("密码")).toHaveFocus());
  });

  it("空提交：请输入密码，不请求", async () => {
    openDialog();
    await submit("");
    expect(await screen.findByText("请输入密码")).toBeInTheDocument();
    expect(reauthenticate).not.toHaveBeenCalled();
  });

  it("成功：写入用户、关闭、settle(true)，不弹提示", async () => {
    vi.mocked(reauthenticate).mockResolvedValue({ ok: true, user: { ...USER, displayName: "新" } });
    const { client, outcome } = openDialog();
    await submit("right-pass");
    await expect(outcome).resolves.toBe(true);
    expect(reauthenticate).toHaveBeenCalledWith("right-pass");
    expect(client.getQueryData(queryKeys.currentUser)).toMatchObject({ displayName: "新" });
    expect(toast.success).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("密码不对：提示、清空、焦点回框内，对话框不关", async () => {
    vi.mocked(reauthenticate).mockResolvedValue({ ok: false, reason: "wrong-password" });
    openDialog();
    const { input } = await submit("bad-pass");
    expect(await screen.findByText("密码不正确")).toBeInTheDocument();
    expect(input).toHaveValue("");
    expect(input).toHaveFocus();
    expect(useReauthStore.getState().open).toBe(true);
  });

  it("429：对话框提示条按分钟说", async () => {
    vi.mocked(reauthenticate).mockResolvedValue({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: 900,
    });
    openDialog();
    await submit("x-pass");
    expect(await screen.findByRole("alert")).toHaveTextContent("尝试次数过多，请 15 分钟后再试");
  });

  it("服务错误：没有验证成功，请稍后再试", async () => {
    vi.mocked(reauthenticate).mockResolvedValue({ ok: false, reason: "unavailable" });
    openDialog();
    await submit("x-pass");
    expect(await screen.findByRole("alert")).toHaveTextContent("没有验证成功，请稍后再试");
  });

  it("验证中：按钮显示验证中…", async () => {
    vi.mocked(reauthenticate).mockReturnValue(new Promise(() => {}));
    openDialog();
    await submit("x-pass");
    expect(await screen.findByRole("button", { name: "验证中…" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("会话没了：关对话框、当前用户写成 null、去登录页", async () => {
    vi.mocked(reauthenticate).mockResolvedValue({ ok: false, reason: "signed-out" });
    const { client, outcome } = openDialog();
    await submit("x-pass");
    await expect(outcome).resolves.toBe(false);
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull();
    expect(router.replace).toHaveBeenCalledWith("/login?next=%2Fsettings%2Fsecurity");
  });

  it("取消：settle(false)，不显示错误", async () => {
    const { outcome } = openDialog();
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "取消" }));
    await expect(outcome).resolves.toBe(false);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("再次打开时清掉上一次的输入与错误", async () => {
    vi.mocked(reauthenticate).mockResolvedValue({ ok: false, reason: "unavailable" });
    openDialog();
    const { user } = await submit("x-pass");
    await screen.findByRole("alert");
    await user.click(screen.getByRole("button", { name: "取消" }));
    act(() => {
      void useReauthStore.getState().request();
    });
    expect(await screen.findByLabelText("密码")).toHaveValue("");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
