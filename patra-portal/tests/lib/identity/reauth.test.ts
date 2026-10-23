// @vitest-environment node
import { ME_RESPONSE, USER } from "@tests/fixtures/user";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { reauthenticate } from "@/lib/identity/reauth";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";
const WRITTEN_NEW =
  /^patra_session=patra_user_new; Path=\/; Expires=[^;]+; Max-Age=2592000; HttpOnly; SameSite=lax$/;

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

describe("reauthenticate", () => {
  it("200 带新令牌：POST /auth/reauth，写入新的会话 Cookie，返回用户", async () => {
    respond(200, { sessionToken: "patra_user_new", user: ME_RESPONSE });
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(reauthenticate("secret-pass")).resolves.toEqual({ ok: true, user: USER });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/auth/reauth");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({ password: "secret-pass" });
    expect(jar.written).toHaveLength(1);
    expect(jar.written[0]).toMatch(WRITTEN_NEW);
  });

  it("200 令牌为 null：保持现有 Cookie 不动", async () => {
    respond(200, { sessionToken: null, user: ME_RESPONSE });
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(reauthenticate("secret-pass")).resolves.toEqual({ ok: true, user: USER });
    expect(jar.written).toEqual([]);
  });

  it("200 但用户缺字段：unavailable，不写 Cookie", async () => {
    respond(200, { sessionToken: "patra_user_new", user: { userId: "1" } });
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(reauthenticate("x")).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(jar.written).toEqual([]);
  });

  it("401 PASSWORD_MISMATCH：wrong-password，不清 Cookie", async () => {
    respond(401, { code: "IDN-0401", traits: ["UNAUTHORIZED", "PASSWORD_MISMATCH"] });
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(reauthenticate("bad")).resolves.toEqual({ ok: false, reason: "wrong-password" });
    expect(jar.written).toEqual([]);
  });

  it("422 password：按密码不对说", async () => {
    respond(422, { code: "IDN-0422", errors: [{ field: "password", code: "REQUIRED" }] });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(reauthenticate("")).resolves.toEqual({ ok: false, reason: "wrong-password" });
  });

  it("429：throttled，带等待秒数", async () => {
    respond(429, { code: "IDN-0429", retryAfterSeconds: 900 });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(reauthenticate("x")).resolves.toEqual({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: 900,
    });
  });

  it("其余 401：会话没了，signed-out 并清 Cookie", async () => {
    respond(401, { code: "GW-0401" });
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(reauthenticate("x")).resolves.toEqual({ ok: false, reason: "signed-out" });
    expect(jar.written).toEqual([CLEARED]);
  });

  it("网关抛错：unavailable", async () => {
    fetchMock.mockRejectedValue(new Error("boom"));
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(reauthenticate("x")).resolves.toEqual({ ok: false, reason: "unavailable" });
  });
});
