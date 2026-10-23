import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { DeletionPendingPanel } from "@/components/account/DeletionPendingPanel";
import { logout } from "@/lib/auth/actions";
import { cancelDeletion } from "@/lib/identity/account-actions";
import { queryKeys } from "@/lib/query-keys";

const router = { replace: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/identity/account-actions", () => ({ cancelDeletion: vi.fn() }));
vi.mock("@/lib/auth/actions", () => ({ logout: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { toast } from "sonner";

afterEach(() => vi.clearAllMocks());

const PENDING = {
  ...USER,
  status: "PENDING_DELETION" as const,
  deletionDueAt: "2026-11-06T00:00:00Z",
};

function renderPanel() {
  const client = new QueryClient();
  client.setQueryData(queryKeys.currentUser, PENDING);
  render(
    <QueryClientProvider client={client}>
      <DeletionPendingPanel next="/papers" />
    </QueryClientProvider>,
  );
  return client;
}

/// 点一个按钮并等 action 回来
async function click(name: string) {
  await act(async () => {
    fireEvent.click(screen.getByRole("button", { name }));
  });
}

describe("DeletionPendingPanel", () => {
  it("取消成功：写入恢复后的用户、提示欢迎回来、回到 next", async () => {
    vi.mocked(cancelDeletion).mockResolvedValue({ ok: true, user: USER });
    const client = renderPanel();
    await click("取消注销，恢复账号");
    expect(client.getQueryData(queryKeys.currentUser)).toEqual(USER);
    expect(toast.success).toHaveBeenCalledWith("已取消注销，欢迎回来");
    expect(router.replace).toHaveBeenCalledWith("/papers");
  });

  it("取消中：按钮显示恢复中…", async () => {
    vi.mocked(cancelDeletion).mockReturnValue(new Promise(() => {}));
    renderPanel();
    await click("取消注销，恢复账号");
    expect(screen.getByRole("button", { name: "恢复中…" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it.each(["unavailable", "pending-deletion"] as const)("%s：提示没有取消成功", async (reason) => {
    vi.mocked(cancelDeletion).mockResolvedValue({ ok: false, reason });
    renderPanel();
    await click("取消注销，恢复账号");
    expect(screen.getByRole("alert")).toHaveTextContent("没有取消成功，请稍后再试");
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("promise 被拒绝：同样提示", async () => {
    vi.mocked(cancelDeletion).mockRejectedValue(new Error("network"));
    renderPanel();
    await click("取消注销，恢复账号");
    expect(screen.getByRole("alert")).toHaveTextContent("没有取消成功，请稍后再试");
  });

  it("会话没了：当前用户写成 null，去登录页", async () => {
    vi.mocked(cancelDeletion).mockResolvedValue({ ok: false, reason: "signed-out" });
    const client = renderPanel();
    await click("取消注销，恢复账号");
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull();
    expect(router.replace).toHaveBeenCalledWith("/login?next=%2Fpapers");
  });

  it("退出登录：调用登出后回首页，当前用户写成 null", async () => {
    vi.mocked(logout).mockResolvedValue();
    const client = renderPanel();
    await click("退出登录");
    expect(logout).toHaveBeenCalledTimes(1);
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull();
    expect(router.replace).toHaveBeenCalledWith("/");
  });
});
