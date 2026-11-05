import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithQuery } from "@tests/support/render-with-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ClearHistoryDialog } from "@/components/history/ClearHistoryDialog";
import { clearHistory } from "@/lib/analytics/history-actions";

vi.mock("@/lib/analytics/history-actions", () => ({ clearHistory: vi.fn() }));
const replace = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace, push: vi.fn() }) }));

const onCleared = vi.fn();
const onOpenChange = vi.fn();

function open(recordingEnabled = true) {
  const user = userEvent.setup();
  renderWithQuery(
    <ClearHistoryDialog
      open
      page="/history"
      recordingEnabled={recordingEnabled}
      onOpenChange={onOpenChange}
      onCleared={onCleared}
    />,
  );
  return user;
}

beforeEach(() => {
  vi.mocked(clearHistory).mockReset();
  onCleared.mockReset();
  onOpenChange.mockReset();
  replace.mockReset();
});

describe("ClearHistoryDialog（简报 §3.7 第 7 条）", () => {
  it("history.clear.confirm：标题、说明，焦点默认在「取消」", async () => {
    open();
    const dialog = screen.getByRole("dialog", { name: "清空全部阅读历史？" });
    expect(dialog).toHaveTextContent(
      "已有的阅读记录会全部删除，不能恢复。之后打开的文献会继续记录。文献页上的浏览数是汇总后的人数，不受影响。",
    );
    await waitFor(() => expect(screen.getByRole("button", { name: "取消" })).toHaveFocus());
  });

  it("记录关着时说明换中间一句", () => {
    open(false);
    expect(screen.getByRole("dialog")).toHaveTextContent(
      "已有的阅读记录会全部删除，不能恢复。阅读记录现在是关闭的，清空后这里会一直是空的。文献页上的浏览数是汇总后的人数，不受影响。",
    );
  });

  it("history.clearing → done：清空中两个按钮都不可点，成功后通知并关闭", async () => {
    let finish: (value: { ok: true }) => void = () => {};
    vi.mocked(clearHistory).mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const user = open();
    await user.click(screen.getByRole("button", { name: "清空" }));
    expect(screen.getByRole("button", { name: "清空中…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "取消" })).toBeDisabled();
    finish({ ok: true });
    await waitFor(() => expect(onCleared).toHaveBeenCalledTimes(1));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("history.clear.failed：对话框留着，红色提示条", async () => {
    vi.mocked(clearHistory).mockResolvedValue({ ok: false, reason: "unavailable" });
    const user = open();
    await user.click(screen.getByRole("button", { name: "清空" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("暂时没能清空，请稍后再试。");
    expect(onCleared).not.toHaveBeenCalled();
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
    expect(screen.getByRole("button", { name: "清空" })).toBeEnabled();
  });

  it("会话过期：去登录页，登录后回来", async () => {
    vi.mocked(clearHistory).mockResolvedValue({ ok: false, reason: "signed-out" });
    const user = open();
    await user.click(screen.getByRole("button", { name: "清空" }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login?next=%2Fhistory"));
  });
});
