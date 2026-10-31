import { focusManager, type InfiniteData } from "@tanstack/react-query";
import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeNotification } from "@tests/fixtures/notification";
import { renderNotifications, unreadResponse } from "@tests/support/notifications";
import { toast } from "sonner";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import type { Notification, NotificationPage } from "@/lib/notification/inbox-schema";
import { useMarkAllRead, useMarkRead } from "@/lib/notifications/mark-read";
import { type UnreadData, useUnreadCountsPolling } from "@/lib/notifications/queries";
import { queryKeys } from "@/lib/query-keys";

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const fetchMock = vi.fn<typeof fetch>();
const REPLY = makeNotification({ notificationId: "1", type: "COMMENT_REPLY" });
const MENTION = makeNotification({ notificationId: "2", type: "MENTION" });
const HELPFUL = makeNotification({
  notificationId: "3",
  type: "POST_HELPFUL",
  actorCount: 2,
  commentId: null,
});

type Client = ReturnType<typeof renderNotifications>["client"];

/// 三个标签各一页、未读 3 条（回复 1、@ 1、有帮助 1）
function seed(client: Client) {
  const page = (items: Notification[]): InfiniteData<NotificationPage> => ({
    pages: [{ items, nextCursor: null }],
    pageParams: [null],
  });
  client.setQueryData(queryKeys.notificationList("replies"), page([REPLY]));
  client.setQueryData(queryKeys.notificationList("mentions"), page([MENTION]));
  client.setQueryData(queryKeys.notificationList("helpful"), page([HELPFUL]));
  client.setQueryData<UnreadData>(queryKeys.notificationUnread, {
    counts: { total: 2, replies: 1, mentions: 1, helpful: 1 },
    etag: '"1-1"',
  });
}

const unread = (client: Client) => client.getQueryData<UnreadData>(queryKeys.notificationUnread);
const readFlag = (client: Client, tab: "replies" | "mentions" | "helpful") =>
  client
    .getQueryData<InfiniteData<NotificationPage>>(queryKeys.notificationList(tab))
    ?.pages[0]?.items.map((n) => n.read);

function MarkOne({ notification }: { notification: Notification }) {
  const markRead = useMarkRead();
  return (
    <button type="button" onClick={() => markRead(notification)}>
      标一条
    </button>
  );
}

function MarkAll() {
  const { markAll, pending, failed } = useMarkAllRead();
  return (
    <>
      <button type="button" disabled={pending} onClick={markAll}>
        全部
      </button>
      {failed && <p>失败了</p>}
    </>
  );
}

/// 一个挂起的响应：手动决定什么时候回来
function deferred() {
  let resolve: (res: Response) => void = () => {};
  const promise = new Promise<Response>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
  vi.mocked(toast.success).mockClear();
});

describe("单条已读", () => {
  it("乐观：两个标签里这条都标为已读，按类型减 1，ETag 清空；请求带 keepalive", async () => {
    fetchMock.mockReturnValue(new Promise(() => {}));
    const { client } = renderNotifications(<MarkOne notification={MENTION} />, { seed });
    await userEvent.click(screen.getByRole("button", { name: "标一条" }));
    expect(readFlag(client, "mentions")).toEqual([true]);
    expect(unread(client)).toEqual({
      counts: { total: 1, replies: 1, mentions: 0, helpful: 1 },
      etag: null,
    });
    expect(fetchMock).toHaveBeenCalledWith("/api/me/notifications/2/read", {
      method: "PUT",
      keepalive: true,
    });
  });

  it("有帮助：只减有帮助的数，铃铛用的 total 不变", async () => {
    fetchMock.mockReturnValue(new Promise(() => {}));
    const { client } = renderNotifications(<MarkOne notification={HELPFUL} />, { seed });
    await userEvent.click(screen.getByRole("button", { name: "标一条" }));
    expect(readFlag(client, "helpful")).toEqual([true]);
    expect(unread(client)?.counts).toEqual({ total: 2, replies: 1, mentions: 1, helpful: 0 });
  });

  it("用返回的计数覆盖，ETag 仍为空", async () => {
    fetchMock.mockResolvedValue(Response.json({ total: 5, replies: 4, mentions: 1, helpful: 0 }));
    const { client } = renderNotifications(<MarkOne notification={REPLY} />, { seed });
    await userEvent.click(screen.getByRole("button", { name: "标一条" }));
    await vi.waitFor(() =>
      expect(unread(client)).toEqual({
        counts: { total: 5, replies: 4, mentions: 1, helpful: 0 },
        etag: null,
      }),
    );
  });

  it("在路上的轮询晚回来，不会把计数冲回旧值", async () => {
    const poll = deferred();
    fetchMock.mockImplementation((url) =>
      String(url).endsWith("/unread-count")
        ? poll.promise
        : Promise.resolve(Response.json({ total: 1, replies: 0, mentions: 1, helpful: 0 })),
    );
    function Page() {
      useUnreadCountsPolling();
      return <MarkOne notification={REPLY} />;
    }
    const { client } = renderNotifications(<Page />, {
      seed: (c) => {
        seed(c);
        c.setQueryDefaults(queryKeys.notificationUnread, { staleTime: 0 });
      },
    });
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    await userEvent.click(screen.getByRole("button", { name: "标一条" }));
    await act(async () => {
      poll.resolve(unreadResponse({ total: 2, replies: 1, mentions: 1, helpful: 0 }, '"1-1"'));
      await poll.promise;
    });
    await vi.waitFor(() =>
      expect(unread(client)?.counts).toEqual({ total: 1, replies: 0, mentions: 1, helpful: 0 }),
    );
  });

  it("已读之后的下一次轮询不带 If-None-Match", async () => {
    fetchMock.mockImplementation((url) =>
      Promise.resolve(
        String(url).endsWith("/unread-count")
          ? unreadResponse({ total: 1, replies: 0, mentions: 1, helpful: 0 }, '"0-1"')
          : Response.json({ total: 1, replies: 0, mentions: 1, helpful: 0 }),
      ),
    );
    function Page() {
      useUnreadCountsPolling();
      return <MarkOne notification={REPLY} />;
    }
    renderNotifications(<Page />, { seed });
    await userEvent.click(screen.getByRole("button", { name: "标一条" }));
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    act(() => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    const poll = fetchMock.mock.calls[1];
    expect(poll?.[0]).toBe("/api/me/notifications/unread-count");
    expect(new Headers(poll?.[1]?.headers).has("If-None-Match")).toBe(false);
    focusManager.setFocused(undefined);
  });

  it("请求失败或 404：未读数标为过期（推送连着时没有轮询来纠正乐观的减 1）", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "not-found" }, { status: 404 }));
    const { client } = renderNotifications(<MarkOne notification={REPLY} />, { seed });
    await userEvent.click(screen.getByRole("button", { name: "标一条" }));
    await vi.waitFor(() =>
      expect(client.getQueryState(queryKeys.notificationUnread)?.isInvalidated).toBe(true),
    );
  });

  it("404 忽略：不报错，乐观结果留着；已读的再点不发请求", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "not-found" }, { status: 404 }));
    const { client } = renderNotifications(<MarkOne notification={REPLY} />, { seed });
    await userEvent.click(screen.getByRole("button", { name: "标一条" }));
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(readFlag(client, "replies")).toEqual([true]);
    fetchMock.mockClear();
    renderNotifications(<MarkOne notification={{ ...REPLY, read: true }} />, { seed });
    await userEvent.click(screen.getAllByRole("button", { name: "标一条" })[1] as HTMLElement);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("全部已读", () => {
  it("乐观清零；成功后用返回的计数并提示「已全部标为已读」", async () => {
    let finish: (res: Response) => void = () => {};
    fetchMock.mockReturnValue(
      new Promise((r) => {
        finish = r;
      }),
    );
    const { client } = renderNotifications(<MarkAll />, { seed });
    await userEvent.click(screen.getByRole("button", { name: "全部" }));
    expect(readFlag(client, "replies")).toEqual([true]);
    expect(readFlag(client, "mentions")).toEqual([true]);
    expect(readFlag(client, "helpful")).toEqual([true]);
    expect(unread(client)).toEqual({
      counts: { total: 0, replies: 0, mentions: 0, helpful: 0 },
      etag: null,
    });
    expect(screen.getByRole("button", { name: "全部" })).toBeDisabled();
    expect(fetchMock).toHaveBeenCalledWith("/api/me/notifications/read", { method: "PUT" });
    await act(async () => finish(Response.json({ total: 0, replies: 0, mentions: 0, helpful: 0 })));
    await vi.waitFor(() => expect(toast.success).toHaveBeenCalledWith("已全部标为已读"));
  });

  it("失败：圆点与计数恢复，标出失败，不出 toast", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "unavailable" }, { status: 502 }));
    const { client } = renderNotifications(<MarkAll />, { seed });
    await userEvent.click(screen.getByRole("button", { name: "全部" }));
    expect(await screen.findByText("失败了")).toBeInTheDocument();
    expect(readFlag(client, "replies")).toEqual([false]);
    expect(unread(client)).toEqual({
      counts: { total: 2, replies: 1, mentions: 1, helpful: 1 },
      etag: '"1-1"',
    });
    expect(toast.success).not.toHaveBeenCalled();
    expect(client.getQueryState(queryKeys.notificationUnread)?.isInvalidated).toBe(true);
  });
});
