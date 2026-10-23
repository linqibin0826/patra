// @vitest-environment node
import { ME_RESPONSE, USER } from "@tests/fixtures/user";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "@/app/api/auth/reauth/route";

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

/// 浏览器发来的再认证请求，默认同源
function request(body: unknown, headers: Record<string, string> = {}) {
  return new Request("http://portal.test/api/auth/reauth", {
    method: "POST",
    headers: {
      origin: "http://portal.test",
      host: "portal.test",
      "content-type": "application/json",
      ...headers,
    },
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}

describe("POST /api/auth/reauth", () => {
  it("跨站请求：403，不打网关", async () => {
    resetCookieJar({ patra_session: "patra_user_abc" });
    const res = await POST(request({ password: "x" }, { origin: "http://evil.test" }));
    expect(res.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("请求体不是 { password: string }：400 unavailable，不打网关", async () => {
    resetCookieJar({ patra_session: "patra_user_abc" });
    const res = await POST(request("not json"));
    expect(res.status).toBe(400);
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("200：转给 identity，换 Cookie，回用户；不缓存", async () => {
    fetchMock.mockResolvedValue(
      Response.json({ sessionToken: "patra_user_new", user: ME_RESPONSE }),
    );
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    const res = await POST(request({ password: "secret-pass" }));
    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual({ ok: true, user: USER });
    expect(res.headers.get("Cache-Control")).toBe("private, no-store");
    expect(jar.written[0]).toMatch(/^patra_session=patra_user_new;/);
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({
      password: "secret-pass",
    });
  });

  it.each([
    [
      401,
      { code: "IDN-0401", traits: ["PASSWORD_MISMATCH"] },
      401,
      { ok: false, reason: "wrong-password" },
    ],
    [
      429,
      { code: "IDN-0429", retryAfterSeconds: 900 },
      429,
      { ok: false, reason: "throttled", retryAfterSeconds: 900 },
    ],
    [401, { code: "GW-0401" }, 401, { ok: false, reason: "signed-out" }],
  ])("网关 %i：门户结果与状态码", async (gwStatus, body, status, expected) => {
    fetchMock.mockResolvedValue(Response.json(body, { status: gwStatus }));
    resetCookieJar({ patra_session: "patra_user_abc" });
    const res = await POST(request({ password: "x" }));
    expect(res.status).toBe(status);
    await expect(res.json()).resolves.toEqual(expected);
  });
});
