// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resendVerificationEmail } from "@/lib/identity/verification-actions";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";

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

const respond = (status: number, body: unknown) =>
  fetchMock.mockImplementation(async () => Response.json(body, { status }));

describe("resendVerificationEmail", () => {
  it("202：带令牌 POST 重发接口，返回下次可重发的时间", async () => {
    respond(202, { email: "a@example.com", nextResendAt: "2026-10-23T08:01:00Z" });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(resendVerificationEmail()).resolves.toEqual({
      ok: true,
      nextResendAt: "2026-10-23T08:01:00Z",
    });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/me/email/verification/resend");
    expect(init?.method).toBe("POST");
  });

  it("429：throttled，带等待秒数", async () => {
    respond(429, { code: "IDN-0429", traits: ["QUOTA_EXCEEDED"], retryAfterSeconds: 125 });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(resendVerificationEmail()).resolves.toEqual({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: 125,
    });
  });

  it.each([
    [["CONFLICT", "EMAIL_ALREADY_VERIFIED"], "already-verified"],
    [["PENDING_DELETION", "CONFLICT"], "pending-deletion"],
  ])("409 %j：%s", async (traits, reason) => {
    respond(409, { code: "IDN-0409", traits });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(resendVerificationEmail()).resolves.toEqual({ ok: false, reason });
  });

  it("401：signed-out，并清掉会话 Cookie", async () => {
    respond(401, { code: "GW-0401" });
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(resendVerificationEmail()).resolves.toEqual({ ok: false, reason: "signed-out" });
    expect(jar.written).toEqual([CLEARED]);
  });

  it("没有会话 Cookie：signed-out，不请求网关", async () => {
    resetCookieJar();
    await expect(resendVerificationEmail()).resolves.toEqual({ ok: false, reason: "signed-out" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["503", () => respond(503, { code: "GW-0503" })],
    ["202 但缺 nextResendAt", () => respond(202, { email: "a@example.com" })],
    ["网络错误", () => fetchMock.mockRejectedValue(new TypeError("fetch failed"))],
  ])("%s：unavailable", async (_label, arrange) => {
    arrange();
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(resendVerificationEmail()).resolves.toEqual({ ok: false, reason: "unavailable" });
  });
});
