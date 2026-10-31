// @vitest-environment node
import { makeNotification } from "@tests/fixtures/notification";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PUT as markOne } from "@/app/api/me/notifications/[id]/read/route";
import { PUT as markAll } from "@/app/api/me/notifications/read/route";
import { GET as getList } from "@/app/api/me/notifications/route";
import { GET as getUnread } from "@/app/api/me/notifications/unread-count/route";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const COUNTS = { total: 3, replies: 2, mentions: 1, helpful: 4 };
const ORIGIN = "http://localhost:4000";

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

const get = (path: string, headers: Record<string, string> = {}) =>
  new Request(`${ORIGIN}${path}`, { headers });
const put = (path: string, origin: string | null = ORIGIN) =>
  new Request(`${ORIGIN}${path}`, {
    method: "PUT",
    headers: { host: "localhost:4000", ...(origin ? { origin } : {}) },
  });
const params = <T>(value: T) => ({ params: Promise.resolve(value) });
const gatewayCall = () => fetchMock.mock.calls[0] ?? [];

describe("未读数的 Route Handler", () => {
  it("把 If-None-Match 原样转给网关；304 原样回给浏览器，无响应体，带 ETag 与 no-cache", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 304, headers: { ETag: '"2-1"' } }));
    const res = await getUnread(
      get("/api/me/notifications/unread-count", { "If-None-Match": '"2-1"' }),
    );
    expect(gatewayCall()[0]).toBe(
      "http://gw.test/patra-notification/me/notifications/unread-count",
    );
    expect(new Headers(gatewayCall()[1]?.headers).get("If-None-Match")).toBe('"2-1"');
    expect(res.status).toBe(304);
    expect(await res.text()).toBe("");
    expect(res.headers.get("etag")).toBe('"2-1"');
    expect(res.headers.get("cache-control")).toBe("no-cache, private");
  });

  it("200：JSON 计数，带 ETag 与 no-cache", async () => {
    fetchMock.mockResolvedValue(Response.json(COUNTS, { headers: { ETag: '"2-1"' } }));
    const res = await getUnread(get("/api/me/notifications/unread-count"));
    expect(new Headers(gatewayCall()[1]?.headers).has("If-None-Match")).toBe(false);
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual(COUNTS);
    expect(res.headers.get("etag")).toBe('"2-1"');
    expect(res.headers.get("cache-control")).toBe("no-cache, private");
  });

  it("没有会话 401，网关不可用 502", async () => {
    resetCookieJar({});
    expect((await getUnread(get("/api/me/notifications/unread-count"))).status).toBe(401);
    resetCookieJar({ patra_session: "patra_user_abc" });
    fetchMock.mockResolvedValue(Response.json({ code: "X" }, { status: 503 }));
    expect((await getUnread(get("/api/me/notifications/unread-count"))).status).toBe(502);
  });
});

describe("提醒列表的 Route Handler", () => {
  it("按标签与游标取一页，不缓存", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [makeNotification()], nextCursor: null }));
    const res = await getList(get("/api/me/notifications?tab=mentions&cursor=c1"));
    expect(res.status).toBe(200);
    expect(res.headers.get("cache-control")).toBe("private, no-store");
    expect(gatewayCall()[0]).toBe(
      "http://gw.test/patra-notification/me/notifications?tab=mentions&cursor=c1",
    );
  });

  it("有帮助标签照转", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [], nextCursor: null }));
    const res = await getList(get("/api/me/notifications?tab=helpful"));
    expect(res.status).toBe(200);
    expect(gatewayCall()[0]).toBe("http://gw.test/patra-notification/me/notifications?tab=helpful");
  });

  it("标签不认识时 404，不打网关", async () => {
    const res = await getList(get("/api/me/notifications?tab=all"));
    expect(res.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("已读的 Route Handler", () => {
  it("单条：跨站 403、ID 不合格 404，都不打网关", async () => {
    expect(
      (await markOne(put("/api/me/notifications/7/read", null), params({ id: "7" }))).status,
    ).toBe(403);
    expect((await markOne(put("/api/me/notifications/x/read"), params({ id: "x" }))).status).toBe(
      404,
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("单条：返回新的计数；网关 404 照转", async () => {
    fetchMock.mockResolvedValueOnce(Response.json(COUNTS));
    const ok = await markOne(put("/api/me/notifications/7/read"), params({ id: "7" }));
    expect(await ok.json()).toEqual(COUNTS);
    expect(gatewayCall()[0]).toBe("http://gw.test/patra-notification/me/notifications/7/read");
    expect(gatewayCall()[1]?.method).toBe("PUT");
    fetchMock.mockResolvedValueOnce(Response.json({ status: 404 }, { status: 404 }));
    expect((await markOne(put("/api/me/notifications/7/read"), params({ id: "7" }))).status).toBe(
      404,
    );
  });

  it("全部：跨站 403；同站返回新的计数", async () => {
    expect((await markAll(put("/api/me/notifications/read", "http://evil.test"))).status).toBe(403);
    const zero = { total: 0, replies: 0, mentions: 0, helpful: 0 };
    fetchMock.mockResolvedValue(Response.json(zero));
    const res = await markAll(put("/api/me/notifications/read"));
    expect(await res.json()).toEqual(zero);
    expect(gatewayCall()[0]).toBe("http://gw.test/patra-notification/me/notifications/read");
  });
});
