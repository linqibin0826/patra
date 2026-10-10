// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { getCurrentUser, lookupSession } from "@/lib/auth/current-user";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const TOKEN = "patra_user_abc";
const USER = { userId: "319041872872550658", email: "reader@example.com" };
const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test:9528");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar({ patra_session: TOKEN });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
const problem = (status: number, code: string) =>
  new Response(JSON.stringify({ status, code, title: "x" }), {
    status,
    headers: { "Content-Type": "application/problem+json" },
  });

describe("lookupSession", () => {
  it("没有 Cookie 时是 anonymous，不请求网关", async () => {
    resetCookieJar();
    await expect(lookupSession()).resolves.toEqual({ status: "anonymous" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("带着令牌请求网关的 /auth/me", async () => {
    fetchMock.mockResolvedValue(json(200, { ...USER, accountType: "user" }));
    await lookupSession();
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test:9528/patra-identity/auth/me");
    expect(init?.headers).toEqual({ Authorization: `Bearer ${TOKEN}` });
  });

  it("200 且字段齐全时是 authenticated，结果里没有 accountType", async () => {
    fetchMock.mockResolvedValue(json(200, { ...USER, accountType: "user" }));
    await expect(lookupSession()).resolves.toEqual({ status: "authenticated", user: USER });
  });

  it.each(["GW-0401", "IDN-0401"])("401 %s 都是 rejected", async (code) => {
    fetchMock.mockResolvedValue(problem(401, code));
    await expect(lookupSession()).resolves.toEqual({ status: "rejected" });
  });

  // Cookie 被改坏：Next 读 Cookie 时做 URL 解码，%0A、%E4%B8%AD 读出来就是换行和「中」
  it.each([`${TOKEN}\nX`, `${TOKEN}中`])(
    "Cookie 值放不进请求头（%j）时是 rejected，不请求网关，也不记日志",
    async (value) => {
      resetCookieJar({ patra_session: value });
      await expect(lookupSession()).resolves.toEqual({ status: "rejected" });
      expect(fetchMock).not.toHaveBeenCalled();
      expect(console.error).not.toHaveBeenCalled();
    },
  );

  it.each([
    ["503 GW-0503", () => problem(503, "GW-0503")],
    ["500 GW-0500", () => problem(500, "GW-0500")],
    ["200 但缺 email", () => json(200, { userId: USER.userId })],
    ["200 但 userId 是数字", () => json(200, { userId: 1, email: USER.email })],
    ["200 但响应体不是 JSON", () => new Response("<html></html>", { status: 200 })],
  ])("%s 时是 unavailable", async (_label, response) => {
    fetchMock.mockResolvedValue(response());
    await expect(lookupSession()).resolves.toEqual({ status: "unavailable" });
  });

  it.each([
    ["超时", new DOMException("The operation was aborted due to timeout", "TimeoutError")],
    ["网络错误", new TypeError("fetch failed")],
  ])("%s时是 unavailable", async (_label, error) => {
    fetchMock.mockRejectedValue(error);
    await expect(lookupSession()).resolves.toEqual({ status: "unavailable" });
  });

  it("缺网关地址时是 unavailable", async () => {
    vi.stubEnv("PATRA_GATEWAY_BASE_URL", undefined);
    await expect(lookupSession()).resolves.toEqual({ status: "unavailable" });
  });

  it("Secure 开关写错时是 unavailable，不请求网关", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "yes");
    await expect(lookupSession()).resolves.toEqual({ status: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("意外的响应只记接口、状态码和错误码，日志里没有令牌", async () => {
    fetchMock.mockResolvedValue(problem(503, "GW-0503"));
    await lookupSession();
    expect(console.error).toHaveBeenCalledWith("[auth] /patra-identity/auth/me 返回了意外的结果", {
      status: 503,
      code: "GW-0503",
    });
    expect(JSON.stringify(vi.mocked(console.error).mock.calls)).not.toContain(TOKEN);
  });
});

describe("getCurrentUser", () => {
  it("已登录时返回用户", async () => {
    fetchMock.mockResolvedValue(json(200, USER));
    await expect(getCurrentUser()).resolves.toEqual(USER);
  });

  it("401 时返回 null，不清 Cookie", async () => {
    fetchMock.mockResolvedValue(problem(401, "GW-0401"));
    const jar = resetCookieJar({ patra_session: TOKEN });
    await expect(getCurrentUser()).resolves.toBeNull();
    expect(jar.written).toEqual([]);
  });

  it("没有 Cookie 或网关不可用时返回 null", async () => {
    resetCookieJar();
    await expect(getCurrentUser()).resolves.toBeNull();
    resetCookieJar({ patra_session: TOKEN });
    fetchMock.mockResolvedValue(problem(503, "GW-0503"));
    await expect(getCurrentUser()).resolves.toBeNull();
  });
});
