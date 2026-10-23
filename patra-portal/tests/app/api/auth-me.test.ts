// @vitest-environment node
import { ME_RESPONSE, USER } from "@tests/fixtures/user";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "@/app/api/auth/me/route";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const TOKEN = "patra_user_abc";
const SIGNED_IN: Record<string, string> = { patra_session: TOKEN };
const SIGNED_OUT: Record<string, string> = {};
/// 刷新：原值重写，有效期回到 30 天
const REFRESHED =
  /^patra_session=patra_user_abc; Path=\/; Expires=[^;]+; Max-Age=2592000; HttpOnly; SameSite=lax$/;
const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";
const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test:9528");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

/// 每次调用都新建响应：同一个 Response 的响应体只能读一次
const respondWith = (status: number, body: unknown) =>
  fetchMock.mockImplementation(async () =>
    Response.json(body, { status, headers: { "Content-Type": "application/json" } }),
  );

describe("GET /api/auth/me", () => {
  it("读到用户：200 + 用户，并把 Cookie 的有效期顺延回 30 天", async () => {
    respondWith(200, ME_RESPONSE);
    const jar = resetCookieJar(SIGNED_IN);
    const res = await GET();
    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual({ user: USER });
    expect(jar.written).toEqual([expect.stringMatching(REFRESHED)]);
  });

  it("没有 Cookie：200 + null，不请求网关，不写 Cookie", async () => {
    const jar = resetCookieJar(SIGNED_OUT);
    const res = await GET();
    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual({ user: null });
    expect(fetchMock).not.toHaveBeenCalled();
    expect(jar.written).toEqual([]);
  });

  it.each(["GW-0401", "IDN-0401"])("网关回 401 %s：200 + null，并清掉 Cookie", async (code) => {
    respondWith(401, { status: 401, code });
    const jar = resetCookieJar(SIGNED_IN);
    const res = await GET();
    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual({ user: null });
    expect(jar.written).toEqual([CLEARED]);
  });

  it("网关不可用：503、响应体为空，Cookie 不动", async () => {
    respondWith(503, { status: 503, code: "GW-0503" });
    const jar = resetCookieJar(SIGNED_IN);
    const res = await GET();
    expect(res.status).toBe(503);
    await expect(res.text()).resolves.toBe("");
    expect(jar.written).toEqual([]);
  });

  it.each([
    ["已登录", 200, SIGNED_IN],
    ["没有 Cookie", 200, SIGNED_OUT],
    ["401", 401, SIGNED_IN],
    ["503", 503, SIGNED_IN],
  ] as const)("%s 时响应带 Cache-Control: private, no-store", async (_label, status, cookies) => {
    respondWith(status, status === 200 ? USER : { status });
    resetCookieJar(cookies);
    const res = await GET();
    expect(res.headers.get("cache-control")).toBe("private, no-store");
  });

  it("同一个令牌并发两次：两次都是 200，都只刷新、不清除", async () => {
    respondWith(200, USER);
    const jar = resetCookieJar(SIGNED_IN);
    const [first, second] = await Promise.all([GET(), GET()]);
    expect([first.status, second.status]).toEqual([200, 200]);
    expect(jar.written).toEqual([
      expect.stringMatching(REFRESHED),
      expect.stringMatching(REFRESHED),
    ]);
  });

  it("先 503 后 200：中间不清 Cookie，恢复后照常刷新", async () => {
    const jar = resetCookieJar(SIGNED_IN);
    respondWith(503, { status: 503, code: "GW-0503" });
    expect((await GET()).status).toBe(503);
    expect(jar.written).toEqual([]);
    respondWith(200, USER);
    expect((await GET()).status).toBe(200);
    expect(jar.written).toEqual([expect.stringMatching(REFRESHED)]);
  });
});
