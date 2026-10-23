import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { USER } from "@tests/fixtures/user";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { DeleteAccountCard } from "@/components/settings/account/DeleteAccountCard";
import { endDeletedSession, requestDeletion } from "@/lib/identity/account-actions";
import { queryKeys } from "@/lib/query-keys";

const router = { replace: vi.fn(), refresh: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/lib/identity/account-actions", () => ({
  requestDeletion: vi.fn(),
  endDeletedSession: vi.fn(),
}));
const withReauth = vi.fn(async (call: () => Promise<unknown>) => call());
vi.mock("@/lib/reauth/use-reauth", () => ({ useReauth: () => ({ withReauth }) }));

const NOW = Date.parse("2026-10-23T08:00:00Z");

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(NOW);
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

function renderCard() {
  const client = new QueryClient();
  client.setQueryData(queryKeys.currentUser, USER);
  render(
    <QueryClientProvider client={client}>
      <DeleteAccountCard user={USER} />
    </QueryClientProvider>,
  );
  return client;
}

/// 打开对话框并走到第 2 步，返回用户名输入框
async function toStepTwo() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "注销账号…" }));
  await user.click(await screen.findByRole("button", { name: "继续" }));
  return { user, input: await screen.findByLabelText("用户名") };
}

describe("DeleteAccountCard / DeleteAccountDialog", () => {
  it("卡片：三条说明与危险按钮", () => {
    renderCard();
    expect(screen.getByRole("heading", { name: "注销账号" })).toBeInTheDocument();
    expect(screen.getByText("被封禁的账号不能自助注销。")).toBeInTheDocument();
  });

  it("第 1 步：后果说明与到期日（今天 + 15 天）", async () => {
    renderCard();
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "注销账号…" }));
    const dialog = await screen.findByRole("dialog", { name: "注销账号" });
    expect(dialog).toHaveTextContent("到期日：2026 年 11 月 7 日");
    expect(dialog).toHaveTextContent("用户名 @chen-yu 永久保留、不能再被使用。");
  });

  it("第 2 步：要求输入用户名；不一致时按钮不可用，离开字段提示", async () => {
    renderCard();
    const { user, input } = await toStepTwo();
    expect(screen.getByRole("dialog", { name: "确认注销" })).toHaveTextContent(
      "请输入你的用户名 chen-yu 以确认。",
    );
    const submit = screen.getByRole("button", { name: "注销账号" });
    expect(submit).toHaveAttribute("aria-disabled", "true");
    await user.type(input, "chen");
    await user.tab();
    expect(screen.getByText("和你的用户名不一致")).toBeInTheDocument();
    await user.click(submit);
    expect(requestDeletion).not.toHaveBeenCalled();
  });

  it("accepts_pasted_username_with_spaces_and_capitals", async () => {
    vi.mocked(requestDeletion).mockResolvedValue({
      ok: true,
      deletionDueAt: "2026-11-07T08:00:00Z",
    });
    renderCard();
    const { user, input } = await toStepTwo();
    await user.click(input);
    await user.paste(" Chen-Yu ");
    await user.click(screen.getByRole("button", { name: "注销账号" }));
    expect(requestDeletion).toHaveBeenCalledWith("chen-yu");
    expect(withReauth).toHaveBeenCalledTimes(1);
  });

  it("提交中：提交中…", async () => {
    vi.mocked(requestDeletion).mockReturnValue(new Promise(() => {}));
    renderCard();
    const { user, input } = await toStepTwo();
    await user.type(input, "chen-yu");
    await user.click(screen.getByRole("button", { name: "注销账号" }));
    expect(screen.getByRole("button", { name: "提交中…" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("成功：结果屏写到期日与邮箱；好的 → 当前用户写成 null 并结束会话", async () => {
    vi.mocked(requestDeletion).mockResolvedValue({
      ok: true,
      deletionDueAt: "2026-11-07T08:00:00Z",
    });
    vi.mocked(endDeletedSession).mockResolvedValue(undefined as never);
    const client = renderCard();
    const { user, input } = await toStepTwo();
    await user.type(input, "chen-yu");
    await user.click(screen.getByRole("button", { name: "注销账号" }));
    const dialog = await screen.findByRole("dialog", { name: "注销申请已提交" });
    expect(dialog).toHaveTextContent("账号将于 2026 年 11 月 7 日删除。");
    expect(dialog).toHaveTextContent("确认邮件已发到 chen.yu@example.com。");
    await user.click(screen.getByRole("button", { name: "好的" }));
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull();
    expect(endDeletedSession).toHaveBeenCalledTimes(1);
  });

  it("用户名不一致（后端）：字段错误", async () => {
    vi.mocked(requestDeletion).mockResolvedValue({ ok: false, reason: "mismatch" });
    renderCard();
    const { user, input } = await toStepTwo();
    await user.type(input, "chen-yu");
    await user.click(screen.getByRole("button", { name: "注销账号" }));
    expect(await screen.findByText("和你的用户名不一致")).toBeInTheDocument();
  });

  it("服务错误：对话框提示条", async () => {
    vi.mocked(requestDeletion).mockResolvedValue({ ok: false, reason: "unavailable" });
    renderCard();
    const { user, input } = await toStepTwo();
    await user.type(input, "chen-yu");
    await user.click(screen.getByRole("button", { name: "注销账号" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("没有提交成功，请稍后再试");
  });

  it("在再认证里取消：停在第 2 步，按钮可点，没有错误", async () => {
    withReauth.mockResolvedValueOnce({ ok: false, reason: "cancelled" });
    renderCard();
    const { user, input } = await toStepTwo();
    await user.type(input, "chen-yu");
    await user.click(screen.getByRole("button", { name: "注销账号" }));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "注销账号" })).not.toHaveAttribute("aria-disabled"),
    );
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("冷静期：去取消注销页", async () => {
    vi.mocked(requestDeletion).mockResolvedValue({ ok: false, reason: "pending-deletion" });
    renderCard();
    const { user, input } = await toStepTwo();
    await user.type(input, "chen-yu");
    await user.click(screen.getByRole("button", { name: "注销账号" }));
    expect(router.replace).toHaveBeenCalledWith(
      "/account/deletion-pending?next=%2Fsettings%2Faccount",
    );
  });

  it("返回：回到第 1 步", async () => {
    renderCard();
    const { user } = await toStepTwo();
    await user.click(screen.getByRole("button", { name: "返回" }));
    expect(await screen.findByRole("dialog", { name: "注销账号" })).toBeInTheDocument();
  });
});
