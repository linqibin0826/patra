import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render as renderRaw, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithQuery } from "@tests/support/render-with-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { ReadingRecordCard } from "@/components/settings/privacy/ReadingRecordCard";
import { clearHistory, setReadingRecording } from "@/lib/analytics/history-actions";
import { queryKeys } from "@/lib/query-keys";

vi.mock("@/lib/analytics/history-actions", () => ({
  clearHistory: vi.fn(),
  setReadingRecording: vi.fn(),
}));
const replace = vi.fn();
const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace, refresh, push: vi.fn() }) }));

function render(initial: { enabled: boolean } | "unavailable" = { enabled: true }) {
  const user = userEvent.setup();
  const utils = renderWithQuery(<ReadingRecordCard initial={initial} />);
  return { user, ...utils };
}

const toggle = () => screen.getByRole("switch", { name: /记录我的阅读历史/ });

beforeEach(() => {
  vi.mocked(setReadingRecording).mockReset();
  vi.mocked(clearHistory).mockReset();
  replace.mockReset();
  refresh.mockReset();
});

describe("隐私设置 ›「阅读记录」卡片（简报 §3.8）", () => {
  it("privacy.reading.on：标题、开关、说明、两个入口，锚点 #reading", () => {
    const { container } = render();
    expect(container.querySelector("section#reading")).not.toBeNull();
    expect(screen.getByRole("heading", { name: "阅读记录" })).toBeInTheDocument();
    expect(toggle()).toBeChecked();
    expect(
      screen.getByText(
        "开启后，你打开过的文献会记到「阅读历史」里，只有你自己能看到，保留 12 个月。关闭后不再记录，已有的记录保留。",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/^已关闭：/)).toBeNull();
    expect(screen.getByRole("link", { name: "隐私政策" })).toHaveAttribute("href", "/privacy");
    expect(screen.getByRole("link", { name: /查看阅读历史/ })).toHaveAttribute("href", "/history");
    expect(screen.getByRole("button", { name: "清空阅读历史" })).toBeInTheDocument();
  });

  it("privacy.reading.off：关着时多一行说明", () => {
    render({ enabled: false });
    expect(toggle()).not.toBeChecked();
    expect(
      screen.getByText(
        "已关闭：现在打开的文献不会记录。之前的记录还在阅读历史里，需要的话可以清空。",
      ),
    ).toBeInTheDocument();
  });

  it("privacy.reading.saving → saved", async () => {
    let finish: (value: { ok: true; enabled: boolean }) => void = () => {};
    vi.mocked(setReadingRecording).mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const { user } = render();
    await user.click(toggle());
    expect(toggle()).not.toBeChecked();
    expect(screen.getByText("保存中…")).toBeInTheDocument();
    expect(setReadingRecording).toHaveBeenCalledWith(false);
    finish({ ok: true, enabled: false });
    expect(await screen.findByText("已保存")).toBeInTheDocument();
  });

  it("privacy.reading.failed：开关退回原值", async () => {
    vi.mocked(setReadingRecording).mockResolvedValue({ ok: false, reason: "unavailable" });
    const { user } = render();
    await user.click(toggle());
    expect(await screen.findByText("没有保存成功，请重试")).toBeInTheDocument();
    expect(toggle()).toBeChecked();
  });

  it("保存中连点两次：按顺序提交，界面停在最后一次的值", async () => {
    const finishes: ((value: { ok: true; enabled: boolean }) => void)[] = [];
    vi.mocked(setReadingRecording).mockImplementation(
      () => new Promise((resolve) => finishes.push(resolve)),
    );
    const { user } = render();
    await user.click(toggle());
    await user.click(toggle());
    expect(toggle()).toBeChecked();
    await waitFor(() => expect(finishes).toHaveLength(1));
    finishes[0]?.({ ok: true, enabled: false });
    await waitFor(() => expect(finishes).toHaveLength(2));
    expect(vi.mocked(setReadingRecording).mock.calls).toEqual([[false], [true]]);
    finishes[1]?.({ ok: true, enabled: true });
    expect(await screen.findByText("已保存")).toBeInTheDocument();
    expect(toggle()).toBeChecked();
  });

  it("会话过期：去登录页，登录后回到设置", async () => {
    vi.mocked(setReadingRecording).mockResolvedValue({ ok: false, reason: "signed-out" });
    const { user } = render();
    await user.click(toggle());
    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login?next=%2Fsettings%2Fprivacy"));
  });

  it("privacy.reading.clear：确认后清空，卡片右上角写已清空", async () => {
    vi.mocked(clearHistory).mockResolvedValue({ ok: true });
    const { user } = render();
    await user.click(screen.getByRole("button", { name: "清空阅读历史" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "清空" }));
    expect(await screen.findByText("已清空")).toBeInTheDocument();
  });

  it("privacy.reading.unavailable：只剩标题、说明与重试", async () => {
    const { user } = render("unavailable");
    expect(screen.getByRole("heading", { name: "阅读记录" })).toBeInTheDocument();
    expect(
      screen.getByText("阅读记录设置暂时打不开，请稍后再试。上面的设置不受影响。"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("switch")).toBeNull();
    await user.click(screen.getByRole("button", { name: "重试" }));
    expect(refresh).toHaveBeenCalled();
  });

  it("清空或改开关后丢掉缓存的阅读历史：再进历史页不先闪出旧记录", async () => {
    vi.mocked(clearHistory).mockResolvedValue({ ok: true });
    vi.mocked(setReadingRecording).mockResolvedValue({ ok: true, enabled: false });
    const client = new QueryClient();
    const stale = {
      pages: [{ recordingEnabled: true, items: [], nextCursor: null }],
      pageParams: [null],
    };
    client.setQueryData(queryKeys.readingHistory, stale);
    const user = userEvent.setup();
    renderRaw(
      <QueryClientProvider client={client}>
        <ReadingRecordCard initial={{ enabled: true }} />
      </QueryClientProvider>,
    );
    await user.click(screen.getByRole("switch", { name: /记录我的阅读历史/ }));
    await waitFor(() => expect(client.getQueryData(queryKeys.readingHistory)).toBeUndefined());
    client.setQueryData(queryKeys.readingHistory, stale);
    await user.click(screen.getByRole("button", { name: "清空阅读历史" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "清空" }));
    await waitFor(() => expect(client.getQueryData(queryKeys.readingHistory)).toBeUndefined());
  });
});
