import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeNotification } from "@tests/fixtures/notification";
import { renderNotifications } from "@tests/support/notifications";
import { StrictMode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Notification } from "@/lib/notification/inbox-schema";
import { type UnreadData, useNotificationList } from "@/lib/notifications/queries";
import { STREAM_RETRY_MS, STREAM_URL, useUnreadStream } from "@/lib/notifications/stream";
import { queryKeys } from "@/lib/query-keys";
import { FRESH_MS, useFreshNotifications } from "@/store/fresh-notifications";
import { useNotificationStreamStore } from "@/store/notification-stream";

/// `EventSource` 的测试替身：记下每个实例，能派发 open / 命名事件 / error
class FakeEventSource {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSED = 2;
  static instances: FakeEventSource[] = [];
  readyState = FakeEventSource.CONNECTING;
  closed = false;
  private readonly listeners = new Map<string, Set<(event: Event) => void>>();

  constructor(readonly url: string) {
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (event: Event) => void) {
    const set = this.listeners.get(type) ?? new Set();
    set.add(listener);
    this.listeners.set(type, set);
  }

  removeEventListener(type: string, listener: (event: Event) => void) {
    this.listeners.get(type)?.delete(listener);
  }

  close() {
    this.closed = true;
    this.readyState = FakeEventSource.CLOSED;
  }

  /// 连上
  open() {
    this.readyState = FakeEventSource.OPEN;
    this.dispatch(new Event("open"));
  }

  /// 收到一条命名事件
  send(type: string, data: string) {
    this.dispatch(new MessageEvent(type, { data }));
  }

  /// 出错：`CONNECTING` 是中途断开、浏览器会自己重连；`CLOSED` 是拿到了非 200、不再重连
  fail(state: number) {
    this.readyState = state;
    this.dispatch(new Event("error"));
  }

  private dispatch(event: Event) {
    for (const listener of this.listeners.get(event.type) ?? []) {
      listener(event);
    }
  }
}

const COUNTS = { total: 3, replies: 2, mentions: 1, helpful: 4 };
const fetchMock = vi.fn<typeof fetch>();
let visibility: DocumentVisibilityState = "visible";

/// 页面切到前台 / 后台
function setVisibility(next: DocumentVisibilityState) {
  visibility = next;
  act(() => {
    document.dispatchEvent(new Event("visibilitychange"));
  });
}

const sources = () => FakeEventSource.instances;
const latest = () => sources().at(-1) as FakeEventSource;
const openSources = () => sources().filter((source) => !source.closed);
const live = () => useNotificationStreamStore.getState().live;

function Stream() {
  useUnreadStream();
  return null;
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  FakeEventSource.instances = [];
  visibility = "visible";
  Object.defineProperty(document, "visibilityState", {
    configurable: true,
    get: () => visibility,
  });
  vi.stubGlobal("EventSource", FakeEventSource);
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockImplementation(async () => Response.json(COUNTS));
  useNotificationStreamStore.setState({ live: false });
  useFreshNotifications.setState({ ids: new Set() });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  fetchMock.mockReset();
});

describe("useUnreadStream", () => {
  it("已登录且页面可见：连门户的推送地址；未登录不连", () => {
    renderNotifications(<Stream />, { user: null });
    expect(sources()).toHaveLength(0);
    renderNotifications(<Stream />);
    expect(sources()).toHaveLength(1);
    expect(latest().url).toBe(STREAM_URL);
  });

  it("连上：live；收到未读数事件写进缓存（ETag 置空），铃铛立刻读到", async () => {
    const { client } = renderNotifications(<Stream />);
    act(() => latest().open());
    expect(live()).toBe(true);
    act(() => latest().send("unread-count", JSON.stringify(COUNTS)));
    await vi.waitFor(() =>
      expect(client.getQueryData<UnreadData>(queryKeys.notificationUnread)).toEqual({
        counts: COUNTS,
        etag: null,
      }),
    );
  });

  it("数据不是 JSON 或形状不对：丢掉，缓存不变", () => {
    const { client } = renderNotifications(<Stream />);
    act(() => latest().send("unread-count", "{oops"));
    act(() => latest().send("unread-count", JSON.stringify({ total: 1, replies: 1, mentions: 0 })));
    expect(client.getQueryData(queryKeys.notificationUnread)).toBeUndefined();
  });

  it("中途断开（浏览器会自己重连）：live 为假，不关连接", () => {
    renderNotifications(<Stream />);
    act(() => latest().open());
    act(() => latest().fail(FakeEventSource.CONNECTING));
    expect(live()).toBe(false);
    expect(latest().closed).toBe(false);
    expect(sources()).toHaveLength(1);
  });

  it("拿到非 200（不再自动重连）：关掉、立刻取一次未读数、60 秒后再试推送", async () => {
    renderNotifications(<Stream />);
    act(() => latest().open());
    act(() => latest().fail(FakeEventSource.CLOSED));
    expect(live()).toBe(false);
    await vi.waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/me/notifications/unread-count",
        expect.anything(),
      ),
    );
    expect(sources()).toHaveLength(1);
    await act(() => vi.advanceTimersByTimeAsync(STREAM_RETRY_MS));
    expect(sources()).toHaveLength(2);
    expect(openSources()).toHaveLength(1);
  });

  it("那次未读数请求是 401：当前用户失效后关掉，不再重试", async () => {
    fetchMock.mockImplementation(async (url) =>
      String(url) === "/api/auth/me"
        ? Response.json({ user: null })
        : Response.json({ reason: "signed-out" }, { status: 401 }),
    );
    renderNotifications(<Stream />);
    act(() => latest().fail(FakeEventSource.CLOSED));
    await vi.waitFor(() =>
      expect(fetchMock.mock.calls.some(([url]) => String(url) === "/api/auth/me")).toBe(true),
    );
    await act(() => vi.advanceTimersByTimeAsync(STREAM_RETRY_MS * 2));
    expect(sources()).toHaveLength(1);
    expect(openSources()).toHaveLength(0);
  });

  it("切到后台关掉，切回来立刻重连；后台期间等着的重试被取消", async () => {
    renderNotifications(<Stream />);
    act(() => latest().fail(FakeEventSource.CLOSED));
    setVisibility("hidden");
    expect(openSources()).toHaveLength(0);
    expect(live()).toBe(false);
    await act(() => vi.advanceTimersByTimeAsync(STREAM_RETRY_MS));
    expect(sources()).toHaveLength(1);
    setVisibility("visible");
    expect(sources()).toHaveLength(2);
    expect(openSources()).toHaveLength(1);
  });

  it("可见时连着，切到后台关掉", () => {
    renderNotifications(<Stream />);
    act(() => latest().open());
    setVisibility("hidden");
    expect(latest().closed).toBe(true);
    expect(live()).toBe(false);
  });

  it("登出（当前用户变成 null）：关掉", async () => {
    const { client } = renderNotifications(<Stream />);
    act(() => latest().open());
    await act(async () => {
      client.setQueryData(queryKeys.currentUser, null);
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(openSources()).toHaveLength(0);
    expect(live()).toBe(false);
  });

  it("严格模式下只留一条连接", () => {
    renderNotifications(
      <StrictMode>
        <Stream />
      </StrictMode>,
    );
    expect(openSources()).toHaveLength(1);
  });

  it("浏览器不支持 EventSource：什么都不做", () => {
    vi.stubGlobal("EventSource", undefined);
    expect(() => renderNotifications(<Stream />)).not.toThrow();
  });
});

describe("推送后刷新开着的提醒列表", () => {
  const A = makeNotification({ notificationId: "A", occurredAt: "2026-10-31T01:00:00Z" });
  const B = makeNotification({ notificationId: "B", occurredAt: "2026-10-31T02:00:00Z" });
  const H = makeNotification({
    notificationId: "H",
    type: "POST_HELPFUL",
    actorCount: 2,
    commentId: null,
    read: true,
    occurredAt: "2026-10-31T00:30:00Z",
  });

  /// 开着的提醒页：一个列表观察者 + 推送
  function Inbox() {
    useNotificationList("replies");
    useUnreadStream();
    return null;
  }

  /// 列表接口每次回下一份；未读数接口照常
  function stubLists(...answers: (Notification[] | number)[]) {
    const queue = [...answers];
    fetchMock.mockImplementation(async (url) => {
      if (String(url).startsWith("/api/me/notifications?")) {
        const next = queue.length > 1 ? queue.shift() : queue[0];
        return typeof next === "number"
          ? Response.json({ reason: "unavailable" }, { status: next })
          : Response.json({ items: next, nextCursor: null });
      }
      return Response.json(COUNTS);
    });
  }

  const listCalls = () =>
    fetchMock.mock.calls.filter(([url]) => String(url).startsWith("/api/me/notifications?")).length;
  const listIds = (client: ReturnType<typeof renderNotifications>["client"]) =>
    client
      .getQueryData<{ pages: { items: Notification[] }[] }>(queryKeys.notificationList("replies"))
      ?.pages.flatMap((page) => page.items.map((n) => n.notificationId));

  it("每条推送都重取开着的列表；新出现的条目记为「新」，2 秒后清掉", async () => {
    stubLists([A], [B, A]);
    const { client } = renderNotifications(<Inbox />);
    await vi.waitFor(() => expect(listIds(client)).toEqual(["A"]));
    act(() => latest().send("unread-count", JSON.stringify(COUNTS)));
    await vi.waitFor(() => expect(listIds(client)).toEqual(["B", "A"]));
    expect([...useFreshNotifications.getState().ids]).toEqual(["B"]);
    await act(() => vi.advanceTimersByTimeAsync(FRESH_MS));
    expect(useFreshNotifications.getState().ids.size).toBe(0);
  });

  it("当天又有人点、挪上来变回未读的有帮助条目也记为「新」", async () => {
    stubLists([A, H], [{ ...H, read: false, occurredAt: "2026-10-31T03:00:00Z" }, A]);
    const { client } = renderNotifications(<Inbox />);
    await vi.waitFor(() => expect(listIds(client)).toEqual(["A", "H"]));
    act(() => latest().send("unread-count", JSON.stringify(COUNTS)));
    await vi.waitFor(() => expect(listIds(client)).toEqual(["H", "A"]));
    expect([...useFreshNotifications.getState().ids]).toEqual(["H"]);
  });

  it("没有开着的列表（只有缓存）：不重取", async () => {
    stubLists([A]);
    const { client } = renderNotifications(<Stream />);
    client.setQueryData(queryKeys.notificationList("replies"), {
      pages: [{ items: [A], nextCursor: null }],
      pageParams: [null],
    });
    act(() => latest().send("unread-count", JSON.stringify(COUNTS)));
    await act(() => vi.advanceTimersByTimeAsync(100));
    expect(listCalls()).toBe(0);
  });

  it("列表还在首次加载：不打断、不记「新」", async () => {
    let release: (res: Response) => void = () => {};
    fetchMock.mockImplementation((url) =>
      String(url).startsWith("/api/me/notifications?")
        ? new Promise((resolve) => {
            release = resolve;
          })
        : Promise.resolve(Response.json(COUNTS)),
    );
    renderNotifications(<Inbox />);
    await vi.waitFor(() => expect(listCalls()).toBe(1));
    act(() => latest().send("unread-count", JSON.stringify(COUNTS)));
    await act(() => vi.advanceTimersByTimeAsync(100));
    expect(listCalls()).toBe(1);
    release(Response.json({ items: [A], nextCursor: null }));
    expect(useFreshNotifications.getState().ids.size).toBe(0);
  });

  it("重取失败：列表照旧，不记「新」", async () => {
    stubLists([A], 502);
    const { client } = renderNotifications(<Inbox />);
    await vi.waitFor(() => expect(listIds(client)).toEqual(["A"]));
    act(() => latest().send("unread-count", JSON.stringify(COUNTS)));
    await vi.waitFor(() => expect(listCalls()).toBe(2));
    expect(listIds(client)).toEqual(["A"]);
    expect(useFreshNotifications.getState().ids.size).toBe(0);
  });

  /// 开着的提醒页，带「加载更多」
  function PagedInbox() {
    const list = useNotificationList("replies");
    useUnreadStream();
    return (
      <button type="button" onClick={() => void list.fetchNextPage()}>
        加载更多
      </button>
    );
  }

  /// 列表接口：带游标的请求回 `next()`，第一页的请求依次回 `firsts`
  function stubPaged(firsts: (() => Promise<Response>)[], next: () => Promise<Response>) {
    const queue = [...firsts];
    fetchMock.mockImplementation((url) => {
      const text = String(url);
      if (text.startsWith("/api/me/notifications?")) {
        if (text.includes("cursor=")) {
          return next();
        }
        const answer = queue.length > 1 ? queue.shift() : queue[0];
        return answer ? answer() : Promise.resolve(Response.json({ items: [], nextCursor: null }));
      }
      return Promise.resolve(Response.json(COUNTS));
    });
  }
  const page = (items: Notification[], nextCursor: string | null) => () =>
    Promise.resolve(Response.json({ items, nextCursor }));
  const OLD = makeNotification({ notificationId: "OLD", occurredAt: "2026-10-30T01:00:00Z" });

  it("推送重取还在路上时点了「加载更多」：追加进来的旧条目不记为「新」", async () => {
    let releaseRefetch: (res: Response) => void = () => {};
    const refetch = () =>
      new Promise<Response>((resolve) => {
        releaseRefetch = resolve;
      });
    stubPaged([page([A], "c2"), refetch], page([OLD], null));
    const { client } = renderNotifications(<PagedInbox />);
    await vi.waitFor(() => expect(listIds(client)).toEqual(["A"]));
    act(() => latest().send("unread-count", JSON.stringify(COUNTS)));
    await vi.waitFor(() => expect(listCalls()).toBe(2));
    await userEvent.click(screen.getByRole("button", { name: "加载更多" }));
    await vi.waitFor(() => expect(listIds(client)).toEqual(["A", "OLD"]));
    releaseRefetch(Response.json({ items: [A], nextCursor: "c2" }));
    await act(() => vi.advanceTimersByTimeAsync(50));
    expect(useFreshNotifications.getState().ids.size).toBe(0);
  });

  it("「加载更多」还在路上时推送到了：不打断翻页", async () => {
    let releaseNext: (res: Response) => void = () => {};
    stubPaged(
      [page([A], "c2")],
      () =>
        new Promise<Response>((resolve) => {
          releaseNext = resolve;
        }),
    );
    const { client } = renderNotifications(<PagedInbox />);
    await vi.waitFor(() => expect(listIds(client)).toEqual(["A"]));
    await userEvent.click(screen.getByRole("button", { name: "加载更多" }));
    await vi.waitFor(() => expect(listCalls()).toBe(2));
    act(() => latest().send("unread-count", JSON.stringify(COUNTS)));
    await act(() => vi.advanceTimersByTimeAsync(50));
    expect(listCalls()).toBe(2);
    releaseNext(Response.json({ items: [OLD], nextCursor: null }));
    await vi.waitFor(() => expect(listIds(client)).toEqual(["A", "OLD"]));
  });
});

describe("推送与在途的轮询", () => {
  it("轮询比推送晚回来：不用旧数覆盖推送的新数", async () => {
    let releasePoll: (res: Response) => void = () => {};
    fetchMock.mockImplementation(
      () =>
        new Promise<Response>((resolve) => {
          releasePoll = resolve;
        }),
    );
    const { client } = renderNotifications(<Stream />);
    const poll = client
      .fetchQuery({
        queryKey: queryKeys.notificationUnread,
        queryFn: async () => ({
          counts: (await (await fetch("/x")).json()) as typeof COUNTS,
          etag: null,
        }),
      })
      .catch(() => {});
    act(() => latest().send("unread-count", JSON.stringify(COUNTS)));
    await act(() => vi.advanceTimersByTimeAsync(0));
    releasePoll(Response.json({ ...COUNTS, total: 1, replies: 0 }));
    await poll;
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(client.getQueryData<UnreadData>(queryKeys.notificationUnread)?.counts).toEqual(COUNTS);
  });
});
