// @vitest-environment node
import { makeNotification } from "@tests/fixtures/notification";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  fetchNotifications,
  fetchUnreadCounts,
  markAllNotificationsRead,
  markNotificationRead,
} from "@/lib/notification/inbox";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const COUNTS = { total: 3, replies: 2, mentions: 1, helpful: 2 };

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar({ patra_session: "patra_user_abc" });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const call = (index = 0) => fetchMock.mock.calls[index] ?? [];
const headersOf = (index = 0) => new Headers(call(index)[1]?.headers);

describe("fetchUnreadCounts", () => {
  it("200：返回计数与响应头里的 ETag，请求带上 If-None-Match 与会话", async () => {
    fetchMock.mockResolvedValue(Response.json(COUNTS, { headers: { ETag: '"2-1"' } }));
    await expect(fetchUnreadCounts('"1-1"')).resolves.toEqual({
      kind: "ok",
      counts: COUNTS,
      etag: '"2-1"',
    });
    expect(call()[0]).toBe("http://gw.test/patra-notification/me/notifications/unread-count");
    expect(headersOf().get("If-None-Match")).toBe('"1-1"');
    expect(headersOf().get("Authorization")).toBe("Bearer patra_user_abc");
  });

  it("304：不解析响应体，返回 not-modified 与 ETag", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 304, headers: { ETag: '"2-1"' } }));
    await expect(fetchUnreadCounts('"2-1"')).resolves.toEqual({
      kind: "not-modified",
      etag: '"2-1"',
    });
  });

  it("没有 If-None-Match 时不带这个头", async () => {
    fetchMock.mockResolvedValue(Response.json(COUNTS));
    await expect(fetchUnreadCounts(null)).resolves.toEqual({
      kind: "ok",
      counts: COUNTS,
      etag: null,
    });
    expect(headersOf().has("If-None-Match")).toBe(false);
  });

  it("401 是 signed-out；没有会话不打网关", async () => {
    fetchMock.mockResolvedValue(Response.json({ code: "GW-0401" }, { status: 401 }));
    await expect(fetchUnreadCounts(null)).resolves.toEqual({ kind: "signed-out" });
    resetCookieJar({});
    fetchMock.mockClear();
    await expect(fetchUnreadCounts(null)).resolves.toEqual({ kind: "signed-out" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("5xx、形状不对、网络错误都是 unavailable", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ code: "X" }, { status: 503 }));
    await expect(fetchUnreadCounts(null)).resolves.toEqual({ kind: "unavailable" });
    fetchMock.mockResolvedValueOnce(
      Response.json({ total: -1, replies: 0, mentions: 0, helpful: 0 }),
    );
    await expect(fetchUnreadCounts(null)).resolves.toEqual({ kind: "unavailable" });
    fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));
    await expect(fetchUnreadCounts(null)).resolves.toEqual({ kind: "unavailable" });
  });
});

describe("fetchNotifications", () => {
  it("按标签与游标请求一页，过 schema", async () => {
    const page = { items: [makeNotification()], nextCursor: "c2" };
    fetchMock.mockResolvedValue(Response.json(page));
    await expect(fetchNotifications("mentions", "a+b")).resolves.toEqual({
      kind: "ok",
      data: page,
    });
    expect(call()[0]).toBe(
      "http://gw.test/patra-notification/me/notifications?tab=mentions&cursor=a%2Bb",
    );
  });

  it("有帮助标签：解析合并条目（人数、经验结果字段、评论 ID 为空）", async () => {
    const row = makeNotification({
      notificationId: "1849",
      type: "POST_HELPFUL",
      occurredAt: "2026-10-31T06:05:12.345678Z",
      actorCount: 2,
      commentId: null,
      excerpt: null,
      subject: {
        type: "post",
        key: "1832",
        available: true,
        title: null,
        journal: "Journal of the American Heart Association",
        abbreviation: "J Am Heart Assoc",
        submittedMonth: "2026-03",
        mine: true,
        experience: {
          firstDecision: "MAJOR_REVISION",
          finalOutcome: "ACCEPTED",
          firstRoundDays: 41,
          totalDays: null,
        },
      },
    });
    fetchMock.mockResolvedValue(Response.json({ items: [row], nextCursor: "opaque/=" }));
    await expect(fetchNotifications("helpful")).resolves.toEqual({
      kind: "ok",
      data: { items: [row], nextCursor: "opaque/=" },
    });
    expect(call()[0]).toBe("http://gw.test/patra-notification/me/notifications?tab=helpful");
  });

  it("没有游标时只带标签", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [], nextCursor: null }));
    await fetchNotifications("replies");
    expect(call()[0]).toBe("http://gw.test/patra-notification/me/notifications?tab=replies");
  });
});

describe("已读", () => {
  it("单条：PUT /me/notifications/{id}/read，返回新的计数", async () => {
    fetchMock.mockResolvedValue(Response.json(COUNTS));
    await expect(markNotificationRead("7")).resolves.toEqual({ kind: "ok", data: COUNTS });
    expect(call()[0]).toBe("http://gw.test/patra-notification/me/notifications/7/read");
    expect(call()[1]?.method).toBe("PUT");
  });

  it("单条：404 是 not-found", async () => {
    fetchMock.mockResolvedValue(
      Response.json({ code: "NOTIFICATION-0404", status: 404 }, { status: 404 }),
    );
    await expect(markNotificationRead("7")).resolves.toEqual({ kind: "not-found" });
  });

  it("全部：PUT /me/notifications/read", async () => {
    fetchMock.mockResolvedValue(Response.json({ total: 0, replies: 0, mentions: 0, helpful: 0 }));
    await expect(markAllNotificationsRead()).resolves.toEqual({
      kind: "ok",
      data: { total: 0, replies: 0, mentions: 0, helpful: 0 },
    });
    expect(call()[0]).toBe("http://gw.test/patra-notification/me/notifications/read");
    expect(call()[1]?.method).toBe("PUT");
  });
});
