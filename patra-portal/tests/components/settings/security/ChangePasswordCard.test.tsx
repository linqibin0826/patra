import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ChangePasswordCard } from "@/components/settings/security/ChangePasswordCard";
import { changePassword } from "@/lib/identity/security-actions";
import { queryKeys } from "@/lib/query-keys";

const router = { replace: vi.fn(), refresh: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/identity/security-actions", () => ({ changePassword: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { toast } from "sonner";

afterEach(() => vi.clearAllMocks());

function renderCard() {
  const client = new QueryClient();
  client.setQueryData(queryKeys.currentUser, USER);
  const invalidate = vi.spyOn(client, "invalidateQueries");
  render(
    <QueryClientProvider client={client}>
      <ChangePasswordCard />
    </QueryClientProvider>,
  );
  return { client, invalidate };
}

/// 填三个框并提交
async function fill(current: string, next: string, confirm = next) {
  const user = userEvent.setup();
  if (current) await user.type(screen.getByLabelText("当前密码"), current);
  if (next) await user.type(screen.getByLabelText("新密码"), next);
  if (confirm) await user.type(screen.getByLabelText("确认新密码"), confirm);
  await user.click(screen.getByRole("button", { name: "修改密码" }));
  return user;
}

describe("ChangePasswordCard", () => {
  it("忘记当前密码链到找回密码页；说明与常驻提示", () => {
    renderCard();
    expect(screen.getByRole("link", { name: "忘记当前密码？" })).toHaveAttribute(
      "href",
      "/forgot-password",
    );
    expect(
      screen.getByText("修改后，除本机外的所有设备都会退出登录，你会收到一封通知邮件。"),
    ).toBeInTheDocument();
    expect(screen.getByText("至少 8 位")).toBeInTheDocument();
  });

  it("页面接管之前就提交：表单按 POST 提交，密码不进地址栏", () => {
    renderCard();
    expect(screen.getByRole("button", { name: "修改密码" }).closest("form")).toHaveAttribute(
      "method",
      "post",
    );
  });

  it("前端校验：为空与不一致，不请求", async () => {
    renderCard();
    await fill("", "new-pass-1", "new-pass-2");
    expect(screen.getByText("请输入当前密码")).toBeInTheDocument();
    expect(screen.getByText("两次输入的密码不一致")).toBeInTheDocument();
    expect(changePassword).not.toHaveBeenCalled();
  });

  it("成功：清空三个框、提示、写入用户、刷新设备列表", async () => {
    vi.mocked(changePassword).mockResolvedValue({ ok: true, user: { ...USER, displayName: "新" } });
    const { client, invalidate } = renderCard();
    await fill("old-pass-1", "new-pass-1");
    expect(changePassword).toHaveBeenCalledWith({
      currentPassword: "old-pass-1",
      newPassword: "new-pass-1",
    });
    expect(toast.success).toHaveBeenCalledWith("密码已修改，其他设备已退出登录");
    expect(client.getQueryData(queryKeys.currentUser)).toMatchObject({ displayName: "新" });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.sessions });
    expect(screen.getByLabelText("当前密码")).toHaveValue("");
    expect(screen.getByLabelText("新密码")).toHaveValue("");
    expect(screen.getByLabelText("确认新密码")).toHaveValue("");
  });

  it("修改中：按钮显示修改中…", async () => {
    vi.mocked(changePassword).mockReturnValue(new Promise(() => {}));
    renderCard();
    await fill("old-pass-1", "new-pass-1");
    expect(screen.getByRole("button", { name: "修改中…" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("当前密码不对：当前密码下方提示并聚焦，不登出", async () => {
    vi.mocked(changePassword).mockResolvedValue({ ok: false, reason: "wrong-password" });
    renderCard();
    await fill("bad-pass-1", "new-pass-1");
    expect(await screen.findByText("当前密码不正确")).toBeInTheDocument();
    expect(screen.getByLabelText("当前密码")).toHaveFocus();
    expect(router.replace).not.toHaveBeenCalled();
  });

  it.each([
    ["UNCHANGED", "新密码不能和当前密码相同"],
    ["TOO_COMMON", "这个密码太常见，容易被猜到，请换一个"],
  ])("新密码 %s：新密码下方提示", async (code, message) => {
    vi.mocked(changePassword).mockResolvedValue({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "newPassword", code }],
    });
    renderCard();
    await fill("old-pass-1", "new-pass-1");
    expect(await screen.findByText(message)).toBeInTheDocument();
  });

  it("429：卡片提示条按分钟说，并提醒登录也受限", async () => {
    vi.mocked(changePassword).mockResolvedValue({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: 900,
    });
    renderCard();
    await fill("old-pass-1", "new-pass-1");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "尝试次数过多，请 15 分钟后再试。在此期间登录也会被限制。",
    );
  });

  it("服务错误：卡片提示条", async () => {
    vi.mocked(changePassword).mockResolvedValue({ ok: false, reason: "unavailable" });
    renderCard();
    await fill("old-pass-1", "new-pass-1");
    expect(await screen.findByRole("alert")).toHaveTextContent("没有保存成功，请稍后再试");
  });

  it("冷静期：去取消注销页", async () => {
    vi.mocked(changePassword).mockResolvedValue({ ok: false, reason: "pending-deletion" });
    renderCard();
    await fill("old-pass-1", "new-pass-1");
    expect(router.replace).toHaveBeenCalledWith(
      "/account/deletion-pending?next=%2Fsettings%2Fsecurity",
    );
  });
});
