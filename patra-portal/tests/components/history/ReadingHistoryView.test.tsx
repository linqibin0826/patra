import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithQuery } from "@tests/support/render-with-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ReadingHistoryView } from "@/components/history/ReadingHistoryView";
import { clearHistory, deleteHistoryEntry } from "@/lib/analytics/history-actions";
import type { HistoryEntry, HistoryPage } from "@/lib/analytics/history-schema";

vi.mock("@/lib/analytics/history-actions", () => ({
  clearHistory: vi.fn(),
  deleteHistoryEntry: vi.fn(),
}));
const replace = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace, push: vi.fn() }) }));

const fetchMock = vi.fn<typeof fetch>();

/// 「现在」是北京时间 2026-11-01 16:00
const NOW = new Date("2026-11-01T08:00:00.000Z");

const entry = (
  articleId: string,
  lastReadAt: string,
  title: string | null,
  fulltextRead = false,
): HistoryEntry => ({
  articleId,
  lastReadAt,
  fulltextRead,
  paper: title === null ? null : { title, journal: "Chest", year: "2017" },
});

const PAGE_1: HistoryPage = {
  recordingEnabled: true,
  items: [
    entry("1", "2026-11-01T07:42:00.000Z", "Hydrocortisone, Vitamin C, and Thiamine", true),
    entry("2", "2026-11-01T07:20:00.000Z", "VITAMINS trial"),
    entry("3", "2026-10-31T13:37:00.000Z", null),
  ],
  nextCursor: "c2",
};
const PAGE_2: HistoryPage = {
  recordingEnabled: true,
  items: [entry("4", "2026-10-30T12:16:00.000Z", "ADRENAL")],
  nextCursor: null,
};

function respond(pages: Record<string, HistoryPage | number>) {
  fetchMock.mockImplementation(async (url) => {
    const cursor = new URL(String(url), "http://x").searchParams.get("cursor") ?? "";
    const page = pages[cursor];
    return typeof page === "number"
      ? new Response(null, { status: page })
      : Response.json(page ?? { recordingEnabled: true, items: [], nextCursor: null });
  });
}

function render() {
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  renderWithQuery(<ReadingHistoryView />);
  return user;
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(NOW);
  vi.stubGlobal("fetch", fetchMock);
  vi.mocked(deleteHistoryEntry).mockReset();
  vi.mocked(clearHistory).mockReset();
  replace.mockReset();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  fetchMock.mockReset();
});

describe("阅读历史页", () => {
  it("页头：标题、说明、阅读记录设置入口", async () => {
    respond({ "": PAGE_1 });
    render();
    expect(screen.getByRole("heading", { level: 1, name: "阅读历史" })).toBeInTheDocument();
    expect(
      screen.getByText(
        "你在 Patra 上打开过的文献，只有你自己能看到。同一篇只留最近一次，记录保留 12 个月。",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "阅读记录设置" })).toHaveAttribute(
      "href",
      "/settings/privacy#reading",
    );
  });

  it("history.list：按天分组、时间、标题链接、刊名年份、读过全文、已不在 Patra 上", async () => {
    respond({ "": PAGE_1 });
    render();
    const today = await screen.findByRole("region", { name: "今天 · 11 月 1 日" });
    expect(within(today).getByText("15:42")).toBeInTheDocument();
    expect(
      within(today).getByRole("link", { name: "Hydrocortisone, Vitamin C, and Thiamine" }),
    ).toHaveAttribute("href", "/papers/1");
    expect(within(today).getAllByText("Chest")).toHaveLength(2);
    expect(within(today).getByText("读过全文")).toBeInTheDocument();
    const yesterday = screen.getByRole("region", { name: "昨天 · 10 月 31 日" });
    expect(within(yesterday).getByText("这篇文献已不在 Patra 上")).toBeInTheDocument();
    expect(within(yesterday).queryByRole("link")).toBeNull();
    expect(within(yesterday).getByRole("button", { name: /^从阅读历史删除/ })).toBeInTheDocument();
  });

  it("history.more / history.end：加载更早的记录，并入分组；到底写说明", async () => {
    respond({ "": PAGE_1, c2: PAGE_2 });
    const user = render();
    await user.click(await screen.findByRole("button", { name: "加载更早的记录" }));
    expect(await screen.findByRole("region", { name: "10 月 30 日 周五" })).toBeInTheDocument();
    expect(screen.getByText("没有更早的了。超过 12 个月的记录会自动清理。")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "加载更早的记录" })).toBeNull();
  });

  it("history.delete：删除中不可再点；成功后行消失，焦点到下一行的删除按钮", async () => {
    respond({ "": PAGE_1 });
    let finish: (value: { ok: true }) => void = () => {};
    vi.mocked(deleteHistoryEntry).mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const user = render();
    const remove = await screen.findByRole("button", {
      name: "从阅读历史删除：Hydrocortisone, Vitamin C, and Thiamine",
    });
    await user.click(remove);
    expect(remove).toBeDisabled();
    await user.click(remove);
    expect(deleteHistoryEntry).toHaveBeenCalledTimes(1);
    expect(deleteHistoryEntry).toHaveBeenCalledWith("1");
    finish({ ok: true });
    await waitFor(() => expect(screen.queryByRole("link", { name: /Hydrocortisone/ })).toBeNull());
    expect(screen.getByRole("button", { name: "从阅读历史删除：VITAMINS trial" })).toHaveFocus();
  });

  it("删掉一组里的最后一条：组标题也消失，焦点到上一行", async () => {
    respond({ "": PAGE_1 });
    vi.mocked(deleteHistoryEntry).mockResolvedValue({ ok: true });
    const user = render();
    await user.click(
      await screen.findByRole("button", { name: "从阅读历史删除：这篇文献已不在 Patra 上" }),
    );
    await waitFor(() => expect(screen.queryByRole("region", { name: /昨天/ })).toBeNull());
    expect(screen.getByRole("button", { name: "从阅读历史删除：VITAMINS trial" })).toHaveFocus();
  });

  it("加载更多之后删第 2 页的一条：已加载的页不重排，焦点到上一行", async () => {
    respond({ "": PAGE_1, c2: PAGE_2 });
    vi.mocked(deleteHistoryEntry).mockResolvedValue({ ok: true });
    const user = render();
    await user.click(await screen.findByRole("button", { name: "加载更早的记录" }));
    await user.click(await screen.findByRole("button", { name: "从阅读历史删除：ADRENAL" }));
    await waitFor(() => expect(screen.queryByRole("region", { name: /10 月 30 日/ })).toBeNull());
    expect(screen.getAllByRole("button", { name: /^从阅读历史删除/ })).toHaveLength(3);
    expect(
      screen.getByRole("button", { name: "从阅读历史删除：这篇文献已不在 Patra 上" }),
    ).toHaveFocus();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("加载更早的记录失败：已加载的条目留着，底部提示并可重试", async () => {
    respond({ "": PAGE_1, c2: 502 });
    const user = render();
    await user.click(await screen.findByRole("button", { name: "加载更早的记录" }));
    expect(await screen.findByText("没能加载更早的记录。")).toBeInTheDocument();
    expect(screen.getByRole("region", { name: /今天/ })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "阅读历史暂时打不开" })).toBeNull();
    respond({ "": PAGE_1, c2: PAGE_2 });
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByRole("region", { name: "10 月 30 日 周五" })).toBeInTheDocument();
  });

  it("删光已加载的条目、后面还有：接着取下一页，不显示空状态", async () => {
    respond({ "": { ...PAGE_1, items: [PAGE_1.items[1] as HistoryEntry] }, c2: PAGE_2 });
    vi.mocked(deleteHistoryEntry).mockResolvedValue({ ok: true });
    const user = render();
    await user.click(await screen.findByRole("button", { name: "从阅读历史删除：VITAMINS trial" }));
    expect(await screen.findByRole("region", { name: "10 月 30 日 周五" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "还没有阅读历史" })).toBeNull();
  });

  it("删掉最后一条：焦点到空状态的按钮（「清空历史」此时不可用）", async () => {
    respond({ "": { ...PAGE_1, items: [PAGE_1.items[1] as HistoryEntry], nextCursor: null } });
    vi.mocked(deleteHistoryEntry).mockResolvedValue({ ok: true });
    const user = render();
    await user.click(await screen.findByRole("button", { name: "从阅读历史删除：VITAMINS trial" }));
    expect(await screen.findByRole("link", { name: "去看看文献" })).toHaveFocus();
  });

  it("history.delete.failed：行恢复，红字提示", async () => {
    respond({ "": PAGE_1 });
    vi.mocked(deleteHistoryEntry).mockResolvedValue({ ok: false, reason: "unavailable" });
    const user = render();
    const remove = await screen.findByRole("button", { name: "从阅读历史删除：VITAMINS trial" });
    await user.click(remove);
    expect(await screen.findByRole("alert")).toHaveTextContent("没能删除，请重试。");
    expect(remove).toBeEnabled();
  });

  it("删除按钮的读屏名截前 40 个字，不带内联标签", async () => {
    const long = `<i>Escherichia coli</i> ${"长".repeat(60)}`;
    respond({ "": { ...PAGE_1, items: [entry("9", "2026-11-01T07:00:00.000Z", long)] } });
    render();
    const expected = `从阅读历史删除：${Array.from(`Escherichia coli ${"长".repeat(60)}`)
      .slice(0, 40)
      .join("")}`;
    expect(await screen.findByRole("button", { name: expected })).toBeInTheDocument();
  });

  it("history.clear.done：清空后变成「阅读历史已清空」", async () => {
    respond({ "": PAGE_1 });
    vi.mocked(clearHistory).mockResolvedValue({ ok: true });
    const user = render();
    await screen.findByRole("region", { name: /今天/ });
    await user.click(screen.getByRole("button", { name: "清空历史" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "清空" }));
    expect(await screen.findByRole("heading", { name: "阅读历史已清空" })).toBeInTheDocument();
    expect(screen.getByText("之后打开的文献会继续记在这里。")).toBeInTheDocument();
  });

  it("history.off.banner：记录关着、还有旧记录时的提示条", async () => {
    respond({ "": { ...PAGE_1, recordingEnabled: false } });
    render();
    const banner = await screen.findByText(
      "阅读记录已关闭：现在打开的文献不会记到这里，下面是关闭之前的记录。",
    );
    expect(banner).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "去设置里开启" })).toHaveAttribute(
      "href",
      "/settings/privacy#reading",
    );
  });

  it("history.empty：开着、没有记录；「清空历史」不可点", async () => {
    respond({ "": { recordingEnabled: true, items: [], nextCursor: null } });
    render();
    expect(await screen.findByRole("heading", { name: "还没有阅读历史" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "去看看文献" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("button", { name: "清空历史" })).toBeDisabled();
  });

  it("history.off.empty：关着、没有记录", async () => {
    respond({ "": { recordingEnabled: false, items: [], nextCursor: null } });
    render();
    expect(await screen.findByRole("heading", { name: "阅读记录已关闭" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "去设置里开启" })).toHaveAttribute(
      "href",
      "/settings/privacy#reading",
    );
  });

  it("history.unavailable：取不到时提示与重试", async () => {
    respond({ "": 502 });
    const user = render();
    expect(await screen.findByRole("heading", { name: "阅读历史暂时打不开" })).toBeInTheDocument();
    respond({ "": PAGE_1 });
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByRole("region", { name: /今天/ })).toBeInTheDocument();
  });

  it("history.loading：骨架", () => {
    fetchMock.mockReturnValue(new Promise(() => {}));
    render();
    expect(screen.getByLabelText("加载中")).toBeInTheDocument();
  });

  it("会话过期（401）：去登录页，登录后回来", async () => {
    respond({ "": 401 });
    render();
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login?next=%2Fhistory"));
  });
});
