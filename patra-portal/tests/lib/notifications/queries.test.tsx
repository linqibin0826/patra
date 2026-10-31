import { focusManager } from "@tanstack/react-query";
import { act, screen } from "@testing-library/react";
import { USER } from "@tests/fixtures/user";
import { fetchCalls, renderNotifications, unreadResponse } from "@tests/support/notifications";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { useUnreadCounts, useUnreadCountsPolling } from "@/lib/notifications/queries";
import { queryKeys } from "@/lib/query-keys";

const fetchMock = vi.fn<typeof fetch>();
let renders = 0;

/// 只读未读数的组件，记渲染次数
function Reader() {
  const { counts, unavailable } = useUnreadCounts();
  renders += 1;
  return <p>{unavailable ? "不可用" : counts ? `共 ${counts.total}` : "等待"}</p>;
}

/// 轮询观察者 + 读者，同 TopNav 的挂法
function Nav() {
  useUnreadCountsPolling();
  return <Reader />;
}

const headerAt = (n: number) => new Headers(fetchMock.mock.calls[n]?.[1]?.headers);

beforeEach(() => {
  renders = 0;
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  fetchMock.mockReset();
  focusManager.setFocused(undefined);
});

describe("未读数轮询", () => {
  it("第一次不带 If-None-Match、不走浏览器缓存；之后带上一次的 ETag", async () => {
    fetchMock.mockResolvedValueOnce(
      unreadResponse({ total: 3, replies: 2, mentions: 1, helpful: 0 }, '"2-1"'),
    );
    fetchMock.mockResolvedValueOnce(unreadResponse(304, '"2-1"'));
    renderNotifications(<Nav />);
    expect(await screen.findByText("共 3")).toBeInTheDocument();
    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/me/notifications/unread-count");
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ cache: "no-store" });
    expect(headerAt(0).has("If-None-Match")).toBe(false);
    await act(() => vi.advanceTimersByTimeAsync(60_000));
    await fetchCalls(fetchMock, 2);
    expect(headerAt(1).get("If-None-Match")).toBe('"2-1"');
  });

  it("304 时缓存是同一个对象，读未读数的组件不重新渲染", async () => {
    fetchMock.mockResolvedValueOnce(
      unreadResponse({ total: 3, replies: 2, mentions: 1, helpful: 0 }, '"2-1"'),
    );
    fetchMock.mockResolvedValueOnce(unreadResponse(304, '"2-1"'));
    const { client } = renderNotifications(<Nav />);
    await screen.findByText("共 3");
    const before = client.getQueryData(queryKeys.notificationUnread);
    const rendered = renders;
    await act(() => vi.advanceTimersByTimeAsync(60_000));
    await fetchCalls(fetchMock, 2);
    await act(() => vi.advanceTimersByTimeAsync(10));
    expect(client.getQueryData(queryKeys.notificationUnread)).toBe(before);
    expect(renders).toBe(rendered);
  });

  it("60 秒后取到新数就更新", async () => {
    fetchMock.mockResolvedValueOnce(
      unreadResponse({ total: 3, replies: 2, mentions: 1, helpful: 0 }, '"2-1"'),
    );
    fetchMock.mockResolvedValueOnce(
      unreadResponse({ total: 4, replies: 3, mentions: 1, helpful: 0 }, '"3-1"'),
    );
    renderNotifications(<Nav />);
    await screen.findByText("共 3");
    await act(() => vi.advanceTimersByTimeAsync(60_000));
    expect(await screen.findByText("共 4")).toBeInTheDocument();
  });

  it("切回页面立即取一次（不等 60 秒、不管是否过期）", async () => {
    fetchMock.mockResolvedValueOnce(
      unreadResponse({ total: 1, replies: 1, mentions: 0, helpful: 0 }, '"1-0"'),
    );
    fetchMock.mockResolvedValueOnce(
      unreadResponse({ total: 2, replies: 2, mentions: 0, helpful: 0 }, '"2-0"'),
    );
    renderNotifications(<Nav />);
    await screen.findByText("共 1");
    act(() => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });
    expect(await screen.findByText("共 2")).toBeInTheDocument();
  });

  it("未登录、冷静期账号不请求", async () => {
    renderNotifications(<Nav />, { user: null });
    renderNotifications(<Nav />, { user: { ...USER, status: "PENDING_DELETION" } });
    await act(() => vi.advanceTimersByTimeAsync(61_000));
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("401：让当前用户失效（会话在页面开着时过期）", async () => {
    fetchMock.mockResolvedValue(Response.json({ reason: "signed-out" }, { status: 401 }));
    const { client } = renderNotifications(<Nav />);
    await screen.findByText("不可用");
    expect(client.getQueryState(queryKeys.currentUser)?.isInvalidated).toBe(true);
  });

  it("取不到时不可用，下一次轮询成功就恢复", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ reason: "unavailable" }, { status: 502 }));
    fetchMock.mockResolvedValueOnce(
      unreadResponse({ total: 2, replies: 1, mentions: 1, helpful: 0 }, '"1-1"'),
    );
    renderNotifications(<Nav />);
    expect(await screen.findByText("不可用")).toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(60_000));
    expect(await screen.findByText("共 2")).toBeInTheDocument();
  });

  it("只有轮询观察者聚焦重取：多处读者不会多发请求", async () => {
    fetchMock.mockResolvedValue(
      unreadResponse({ total: 1, replies: 1, mentions: 0, helpful: 0 }, '"1-0"'),
    );
    renderNotifications(
      <>
        <Nav />
        <Reader />
        <Reader />
      </>,
    );
    await screen.findAllByText("共 1");
    await act(() => vi.advanceTimersByTimeAsync(60_000));
    await act(() => vi.advanceTimersByTimeAsync(10));
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
