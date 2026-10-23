// @vitest-environment node
import { ME_RESPONSE, USER } from "@tests/fixtures/user";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { lookupSession, lookupSessionByToken } from "@/lib/auth/current-user";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const TOKEN = "patra_user_abc";
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
    fetchMock.mockResolvedValue(json(200, ME_RESPONSE));
    await lookupSession();
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test:9528/patra-identity/auth/me");
    expect(init?.headers).toEqual({ Authorization: `Bearer ${TOKEN}` });
  });

  it("转发浏览器的 IP 与 UA", async () => {
    resetCookieJar(
      { patra_session: TOKEN },
      { "x-forwarded-for": "10.0.0.8", "user-agent": "UA/8" },
    );
    fetchMock.mockResolvedValue(json(200, ME_RESPONSE));
    await lookupSession();
    expect(fetchMock.mock.calls[0]?.[1]?.headers).toEqual({
      Authorization: `Bearer ${TOKEN}`,
      "X-Forwarded-For": "10.0.0.8",
      "User-Agent": "UA/8",
    });
  });

  it("200 且字段齐全时是 authenticated，结果里没有 accountType", async () => {
    fetchMock.mockResolvedValue(json(200, ME_RESPONSE));
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
    ["200 但 userId 是数字", () => json(200, { ...ME_RESPONSE, userId: 1 })],
    ["200 但 avatarPalette 超出 0–11", () => json(200, { ...ME_RESPONSE, avatarPalette: 12 })],
    ["200 但 status 是 BANNED", () => json(200, { ...ME_RESPONSE, status: "BANNED" })],
    ["200 但缺 username", () => json(200, { ...ME_RESPONSE, username: undefined })],
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

describe("lookupSessionByToken", () => {
  it.each([null, ""])("没有令牌（%j）时是 anonymous，不请求网关", async (token) => {
    await expect(lookupSessionByToken(token)).resolves.toEqual({ status: "anonymous" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("带着传入的令牌请求网关的 /auth/me，不读 Cookie", async () => {
    // 读 Cookie 会因为开关写错而抛错；不读 Cookie 才能照常拿到用户
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "yes");
    fetchMock.mockResolvedValue(json(200, ME_RESPONSE));
    await expect(lookupSessionByToken("patra_user_other")).resolves.toEqual({
      status: "authenticated",
      user: USER,
    });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test:9528/patra-identity/auth/me");
    expect(init?.headers).toEqual({ Authorization: "Bearer patra_user_other" });
  });

  it.each(["GW-0401", "IDN-0401"])("401 %s 都是 rejected", async (code) => {
    fetchMock.mockResolvedValue(problem(401, code));
    await expect(lookupSessionByToken(TOKEN)).resolves.toEqual({ status: "rejected" });
  });

  it.each([`${TOKEN}\nX`, `${TOKEN}中`])(
    "令牌放不进请求头（%j）时是 rejected，不请求网关，也不记日志",
    async (token) => {
      await expect(lookupSessionByToken(token)).resolves.toEqual({ status: "rejected" });
      expect(fetchMock).not.toHaveBeenCalled();
      expect(console.error).not.toHaveBeenCalled();
    },
  );

  it.each([
    ["503 GW-0503", () => problem(503, "GW-0503")],
    ["200 但缺 email", () => json(200, { userId: USER.userId })],
    ["200 但响应体不是 JSON", () => new Response("<html></html>", { status: 200 })],
  ])("%s 时是 unavailable", async (_label, response) => {
    fetchMock.mockResolvedValue(response());
    await expect(lookupSessionByToken(TOKEN)).resolves.toEqual({ status: "unavailable" });
  });

  it.each([
    ["超时", new DOMException("The operation was aborted due to timeout", "TimeoutError")],
    ["网络错误", new TypeError("fetch failed")],
  ])("%s时是 unavailable", async (_label, error) => {
    fetchMock.mockRejectedValue(error);
    await expect(lookupSessionByToken(TOKEN)).resolves.toEqual({ status: "unavailable" });
  });

  it("超时按传入的毫秒数，不传时是 10 秒", async () => {
    const timeout = vi.spyOn(AbortSignal, "timeout");
    fetchMock.mockImplementation(async () => json(200, USER));
    await lookupSessionByToken(TOKEN, { timeoutMs: 3_000 });
    await lookupSessionByToken(TOKEN);
    expect(timeout.mock.calls).toEqual([[3_000], [10_000]]);
  });
});
