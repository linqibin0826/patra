// @vitest-environment node
import { makeItemStatus, makeOverview } from "@tests/fixtures/interaction";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { POST as postKeys } from "@/app/api/library/keys/route";
import { GET as getItems } from "@/app/api/me/library/items/route";
import { GET as getOverview } from "@/app/api/me/library/route";
import { POST as postStatus } from "@/app/api/me/library/status/route";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();

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

const SAME_ORIGIN = { origin: "http://localhost:4000", host: "localhost:4000" };

function post(path: string, body: unknown, headers: Record<string, string> = SAME_ORIGIN) {
  return new Request(`http://localhost:4000${path}`, {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
}

describe("GET /api/me/library", () => {
  it("200：概览，不缓存", async () => {
    fetchMock.mockResolvedValue(Response.json(makeOverview()));
    const res = await getOverview();
    expect(res.status).toBe(200);
    expect(res.headers.get("cache-control")).toBe("private, no-store");
    expect(await res.json()).toEqual(makeOverview());
  });

  it("没有会话：401", async () => {
    resetCookieJar();
    expect((await getOverview()).status).toBe(401);
  });

  it("interaction 不可用：502", async () => {
    fetchMock.mockResolvedValue(Response.json({}, { status: 503 }));
    expect((await getOverview()).status).toBe(502);
  });
});

describe("GET /api/me/library/items", () => {
  const items = (query: string) =>
    getItems(new Request(`http://localhost:4000/api/me/library/items?${query}`));

  it("收藏夹视图按 ID 走 /me/collections/{id}/items", async () => {
    fetchMock.mockResolvedValue(
      Response.json({ page: 2, pageSize: 50, total: 0, totalPages: 0, items: [] }),
    );
    const res = await items("view=9&sort=journal&page=2");
    expect(res.status).toBe(200);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://gw.test/patra-interaction/me/collections/9/items?sort=journal&page=2&size=50",
    );
  });

  it("视图不合格：404，不打网关", async () => {
    expect((await items("view=abc")).status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("POST /api/me/library/status", () => {
  it("keys 去重后批量查", async () => {
    fetchMock.mockResolvedValue(Response.json({ items: [makeItemStatus()] }));
    const res = await postStatus(post("/api/me/library/status", { keys: ["11", "11", "12"] }));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ items: [makeItemStatus()] });
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({
      type: "publication",
      keys: ["11", "12"],
    });
  });

  it("超过 100 个：422，不打网关", async () => {
    const keys = Array.from({ length: 101 }, (_, i) => String(i + 1));
    expect((await postStatus(post("/api/me/library/status", { keys }))).status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("异源或缺 Origin：403，不打网关", async () => {
    const res = await postStatus(
      post(
        "/api/me/library/status",
        { keys: ["1"] },
        { origin: "http://evil.test", host: "localhost:4000" },
      ),
    );
    expect(res.status).toBe(403);
    expect((await postStatus(post("/api/me/library/status", { keys: ["1"] }, {}))).status).toBe(
      403,
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("POST /api/library/keys", () => {
  it("返回范围里的 key 与跳过篇数", async () => {
    fetchMock.mockResolvedValue(Response.json({ keys: ["11"], skippedUnavailable: 1 }));
    const res = await postKeys(
      post("/api/library/keys", { scope: { kind: "collection", id: "9", sort: "added" } }),
    );
    expect(await res.json()).toEqual({ keys: ["11"], skippedUnavailable: 1 });
  });

  it("超 5,000：200 { tooLarge: true }", async () => {
    fetchMock.mockResolvedValue(Response.json({ traits: ["EXPORT_TOO_LARGE"] }, { status: 422 }));
    const res = await postKeys(
      post("/api/library/keys", { scope: { kind: "library", view: "all", sort: "added" } }),
    );
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ tooLarge: true });
  });

  it("范围不合格：422", async () => {
    const res = await postKeys(post("/api/library/keys", { scope: { kind: "x" } }));
    expect(res.status).toBe(422);
  });
});
