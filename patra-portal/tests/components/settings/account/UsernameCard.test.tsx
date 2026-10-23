import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { afterEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { UsernameCard } from "@/components/settings/account/UsernameCard";
import type { CurrentUser } from "@/lib/auth/types";
import { changeUsername } from "@/lib/identity/account-actions";
import { queryKeys } from "@/lib/query-keys";

const router = { replace: vi.fn(), refresh: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/identity/account-actions", () => ({ changeUsername: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

import { toast } from "sonner";

afterEach(() => vi.clearAllMocks());

const NOW = Date.parse("2026-10-23T08:00:00Z");

function renderCard(user: Partial<CurrentUser> = {}) {
  const client = new QueryClient();
  const current = { ...USER, ...user };
  client.setQueryData(queryKeys.currentUser, current);
  const invalidate = vi.spyOn(client, "invalidateQueries");
  const view = (next: CurrentUser) => (
    <QueryClientProvider client={client}>
      <UsernameCard user={next} now={NOW} />
    </QueryClientProvider>
  );
  const { rerender } = render(view(current));
  return {
    invalidate,
    rerender: (next: Partial<CurrentUser>) => rerender(view({ ...current, ...next })),
  };
}

/// 点「修改」进入编辑态，返回输入框
async function startEditing() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "修改" }));
  return { user, input: screen.getByLabelText("新用户名") as HTMLInputElement };
}

describe("UsernameCard", () => {
  it("展示态：@用户名、主页地址新标签页打开、常驻说明", () => {
    renderCard();
    expect(screen.getByText("@chen-yu")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "/u/chen-yu" });
    expect(link).toHaveAttribute("href", "/u/chen-yu");
    expect(link).toHaveAttribute("target", "_blank");
    expect(
      screen.getByText(
        "改名后，旧用户名保留 90 天：访问旧地址会跳到新地址，期间只有你能改回。两次修改至少间隔 30 天。",
      ),
    ).toBeInTheDocument();
  });

  it("冷却中：修改禁用，显示下次可修改的日期", () => {
    renderCard({ nextUsernameChangeAt: "2026-11-19T08:00:00Z" });
    expect(screen.getByRole("button", { name: "修改" })).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByText("下次可修改：2026 年 11 月 19 日")).toBeInTheDocument();
  });

  it("冷却已过：可以修改，不显示日期", () => {
    renderCard({ nextUsernameChangeAt: "2026-09-01T08:00:00Z" });
    expect(screen.getByRole("button", { name: "修改" })).not.toHaveAttribute("aria-disabled");
    expect(screen.queryByText(/下次可修改/)).not.toBeInTheDocument();
  });

  it("编辑态：预填当前用户名并全选，提示主页地址", async () => {
    renderCard();
    const { input } = await startEditing();
    expect(input).toHaveValue("chen-yu");
    await waitFor(() => expect(input).toHaveFocus());
    expect(input.selectionStart).toBe(0);
    expect(input.selectionEnd).toBe("chen-yu".length);
    expect(screen.getByText(/用作主页地址 \/u\/chen-yu/)).toBeInTheDocument();
  });

  it("与当前相同：就地提示，不请求", async () => {
    renderCard();
    const { user } = await startEditing();
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(screen.getByText("新用户名和现在的一样")).toBeInTheDocument();
    expect(changeUsername).not.toHaveBeenCalled();
  });

  it("前端规则：太短", async () => {
    renderCard();
    const { user, input } = await startEditing();
    await user.clear(input);
    await user.type(input, "ab");
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(screen.getByText("用户名至少 3 个字符")).toBeInTheDocument();
    expect(changeUsername).not.toHaveBeenCalled();
  });

  it("成功：提示、刷新当前用户与页面、回到展示态", async () => {
    vi.mocked(changeUsername).mockResolvedValue({
      ok: true,
      username: "new-name",
      nextUsernameChangeAt: "2026-11-22T08:00:00Z",
    });
    const { invalidate } = renderCard();
    const { user, input } = await startEditing();
    await user.clear(input);
    await user.type(input, "New-Name");
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(changeUsername).toHaveBeenCalledWith("new-name");
    expect(toast.success).toHaveBeenCalledWith("用户名已改为 @new-name");
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.currentUser });
    expect(router.refresh).toHaveBeenCalled();
    expect(screen.queryByLabelText("新用户名")).not.toBeInTheDocument();
  });

  it("保存中：按钮显示保存中…", async () => {
    vi.mocked(changeUsername).mockReturnValue(new Promise(() => {}));
    renderCard();
    const { user, input } = await startEditing();
    await user.clear(input);
    await user.type(input, "new-name");
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(screen.getByRole("button", { name: "保存中…" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it.each([
    [{ ok: false, reason: "taken" } as const, "这个用户名已被使用"],
    [{ ok: false, reason: "invalid", code: "RESERVED" } as const, "这个用户名不能使用，请换一个"],
  ])("%j：输入框下方提示", async (result, message) => {
    vi.mocked(changeUsername).mockResolvedValue(result);
    renderCard();
    const { user, input } = await startEditing();
    await user.clear(input);
    await user.type(input, "admin-x");
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(await screen.findByText(message)).toBeInTheDocument();
  });

  it("冷却（409）：卡片提示条，刷新页面与当前用户，回到展示态", async () => {
    vi.mocked(changeUsername).mockResolvedValue({ ok: false, reason: "cooldown" });
    const { invalidate, rerender } = renderCard();
    const { user, input } = await startEditing();
    await user.clear(input);
    await user.type(input, "again");
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("30 天内只能修改一次");
    expect(router.refresh).toHaveBeenCalled();
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.currentUser });
    expect(screen.queryByLabelText("新用户名")).not.toBeInTheDocument();
    // 页面刷新后带回日期，提示条补上「下次可修改」，按钮进入冷却
    rerender({ nextUsernameChangeAt: "2026-11-19T08:00:00Z" });
    expect(screen.getByRole("alert")).toHaveTextContent(
      "30 天内只能修改一次，下次可修改：2026 年 11 月 19 日",
    );
    expect(screen.getByRole("button", { name: "修改" })).toHaveAttribute("aria-disabled", "true");
  });

  it("服务错误：卡片提示条", async () => {
    vi.mocked(changeUsername).mockResolvedValue({ ok: false, reason: "unavailable" });
    renderCard();
    const { user, input } = await startEditing();
    await user.clear(input);
    await user.type(input, "new-name");
    await user.click(screen.getByRole("button", { name: "保存" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("没有保存成功，请稍后再试");
  });

  it("冷静期：去取消注销页", async () => {
    vi.mocked(changeUsername).mockResolvedValue({ ok: false, reason: "pending-deletion" });
    renderCard();
    const { user, input } = await startEditing();
    await user.clear(input);
    await user.type(input, "new-name");
    await user.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith(
        "/account/deletion-pending?next=%2Fsettings%2Faccount",
      ),
    );
  });

  it("取消：回到展示态", async () => {
    renderCard();
    const { user } = await startEditing();
    await user.click(screen.getByRole("button", { name: "取消" }));
    expect(screen.queryByLabelText("新用户名")).not.toBeInTheDocument();
  });
});
