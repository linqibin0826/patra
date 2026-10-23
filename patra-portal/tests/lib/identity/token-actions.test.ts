// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { requestPasswordReset } from "@/lib/identity/token-actions";

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
