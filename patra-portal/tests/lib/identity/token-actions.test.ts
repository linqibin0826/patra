// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { requestPasswordReset, resetPassword, verifyEmail } from "@/lib/identity/token-actions";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
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

const respond = (status: number, body?: unknown) =>
  fetchMock.mockImplementation(async () =>
    body === undefined ? new Response(null, { status }) : Response.json(body, { status }),
  );

/// 最近一次请求的地址、方法、请求体与请求头
function lastRequest() {
  const [url, init] = fetchMock.mock.calls.at(-1) ?? [];
  return {
    url,
    method: init?.method,
    body: typeof init?.body === "string" ? JSON.parse(init.body) : undefined,
    headers: (init?.headers ?? {}) as Record<string, string>,
  };
}

describe("requestPasswordReset", () => {
  it("202：没有会话也请求，返回邮箱与下次可申请时间", async () => {
    respond(202, { email: "a@b.com", nextRequestAt: "2026-10-23T08:01:00Z" });
    resetCookieJar();
    await expect(requestPasswordReset("a@b.com")).resolves.toEqual({
      ok: true,
      email: "a@b.com",
      nextRequestAt: "2026-10-23T08:01:00Z",
    });
    const request = lastRequest();
    expect(request).toMatchObject({
      url: "http://gw.test/patra-identity/auth/password/forgot",
      method: "POST",
      body: { email: "a@b.com" },
    });
    expect(request.headers.Authorization).toBeUndefined();
  });

  it("422 email：invalid", async () => {
    respond(422, { code: "IDN-0422", errors: [{ field: "email", code: "INVALID_FORMAT" }] });
    resetCookieJar();
    await expect(requestPasswordReset("x")).resolves.toEqual({ ok: false, reason: "invalid" });
  });

  it("429：throttled，带等待秒数", async () => {
    respond(429, { code: "IDN-0429", retryAfterSeconds: 125 });
    resetCookieJar();
    await expect(requestPasswordReset("a@b.com")).resolves.toEqual({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: 125,
    });
  });

  it("网关抛错：unavailable", async () => {
    fetchMock.mockRejectedValue(new Error("boom"));
    resetCookieJar();
    await expect(requestPasswordReset("a@b.com")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
  });
});

const TOKEN = "t".repeat(43);

describe("verifyEmail", () => {
  it("200：有会话 Cookie 时带上令牌，返回邮箱", async () => {
    respond(200, { email: "a@b.com", emailVerifiedAt: "2026-10-23T08:00:00Z" });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(verifyEmail(TOKEN)).resolves.toEqual({ ok: true, email: "a@b.com" });
    const request = lastRequest();
    expect(request).toMatchObject({
      url: "http://gw.test/patra-identity/auth/email/verify",
      method: "POST",
      body: { token: TOKEN },
    });
    expect(request.headers.Authorization).toBe("Bearer patra_user_abc");
  });

  it("没有会话也请求，不带 Authorization", async () => {
    respond(200, { email: "a@b.com", emailVerifiedAt: "2026-10-23T08:00:00Z" });
    resetCookieJar();
    await verifyEmail(TOKEN);
    expect(lastRequest().headers.Authorization).toBeUndefined();
  });

  it("422 token：invalid", async () => {
    respond(422, { code: "IDN-0422", errors: [{ field: "token", code: "INVALID" }] });
    resetCookieJar();
    await expect(verifyEmail(TOKEN)).resolves.toEqual({ ok: false, reason: "invalid" });
  });

  it("422 其他字段：unavailable 并记日志", async () => {
    respond(422, { code: "IDN-0422", errors: [{ field: "other", code: "X" }] });
    resetCookieJar();
    await expect(verifyEmail(TOKEN)).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(console.error).toHaveBeenCalled();
  });
});

describe("resetPassword", () => {
  it("200：POST /auth/password/reset，返回邮箱", async () => {
    respond(200, { email: "a@b.com" });
    resetCookieJar();
    await expect(resetPassword({ token: TOKEN, newPassword: "new-pass-1" })).resolves.toEqual({
      ok: true,
      email: "a@b.com",
    });
    expect(lastRequest()).toMatchObject({
      url: "http://gw.test/patra-identity/auth/password/reset",
      method: "POST",
      body: { token: TOKEN, newPassword: "new-pass-1" },
    });
  });

  it("422 token：invalid", async () => {
    respond(422, { code: "IDN-0422", errors: [{ field: "token", code: "INVALID" }] });
    resetCookieJar();
    await expect(resetPassword({ token: TOKEN, newPassword: "x" })).resolves.toEqual({
      ok: false,
      reason: "invalid",
    });
  });

  it("422 newPassword：invalid-password，带原因码", async () => {
    respond(422, { code: "IDN-0422", errors: [{ field: "newPassword", code: "TOO_COMMON" }] });
    resetCookieJar();
    await expect(resetPassword({ token: TOKEN, newPassword: "password" })).resolves.toEqual({
      ok: false,
      reason: "invalid-password",
      code: "TOO_COMMON",
    });
  });

  it("429：throttled", async () => {
    respond(429, { code: "IDN-0429", retryAfterSeconds: 125 });
    resetCookieJar();
    await expect(resetPassword({ token: TOKEN, newPassword: "new-pass-1" })).resolves.toEqual({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: 125,
    });
  });
});
