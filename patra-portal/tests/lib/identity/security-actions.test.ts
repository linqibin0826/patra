// @vitest-environment node
import { ME_RESPONSE, USER } from "@tests/fixtures/user";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  changePassword,
  revokeOtherSessions,
  revokeSession,
} from "@/lib/identity/security-actions";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
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

const respond = (status: number, body?: unknown) =>
  fetchMock.mockImplementation(async () =>
    body === undefined ? new Response(null, { status }) : Response.json(body, { status }),
  );

/// 最近一次请求的方法、地址与 JSON 请求体
function lastRequest() {
  const [url, init] = fetchMock.mock.calls.at(-1) ?? [];
  return {
    url,
    method: init?.method,
    body: typeof init?.body === "string" ? JSON.parse(init.body) : undefined,
  };
}

const INPUT = { currentPassword: "old-pass-1", newPassword: "new-pass-1" };

describe("changePassword", () => {
  it("200：PUT /me/password，写入新令牌，返回用户", async () => {
    respond(200, { sessionToken: "patra_user_new", user: ME_RESPONSE });
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(changePassword(INPUT)).resolves.toEqual({ ok: true, user: USER });
    expect(lastRequest()).toEqual({
      url: "http://gw.test/patra-identity/me/password",
      method: "PUT",
      body: INPUT,
    });
    expect(jar.written).toHaveLength(1);
    expect(jar.written[0]).toMatch(WRITTEN_NEW);
  });

  it("401 PASSWORD_MISMATCH：wrong-password，不清 Cookie", async () => {
    respond(401, { code: "IDN-0401", traits: ["PASSWORD_MISMATCH", "UNAUTHORIZED"] });
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(changePassword(INPUT)).resolves.toEqual({ ok: false, reason: "wrong-password" });
    expect(jar.written).toEqual([]);
  });

  it("422：invalid-fields，原样带回字段错误", async () => {
    respond(422, { code: "IDN-0422", errors: [{ field: "newPassword", code: "UNCHANGED" }] });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(changePassword(INPUT)).resolves.toEqual({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "newPassword", code: "UNCHANGED" }],
    });
  });

  it("429：throttled", async () => {
    respond(429, { code: "IDN-0429", retryAfterSeconds: 900 });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(changePassword(INPUT)).resolves.toEqual({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: 900,
    });
  });

  it("409 PENDING_DELETION：pending-deletion", async () => {
    respond(409, { code: "IDN-0409", traits: ["PENDING_DELETION"] });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(changePassword(INPUT)).resolves.toEqual({ ok: false, reason: "pending-deletion" });
  });

  it("200 缺令牌：unavailable，不写 Cookie", async () => {
    respond(200, { sessionToken: null, user: ME_RESPONSE });
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(changePassword(INPUT)).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(jar.written).toEqual([]);
  });
});

describe("revokeSession / revokeOtherSessions", () => {
  it("踢单个：DELETE /me/sessions/{id}，返回被踢数量", async () => {
    respond(200, { revokedSessions: 1 });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(revokeSession("723511223344556678")).resolves.toEqual({
      ok: true,
      revokedSessions: 1,
    });
    expect(lastRequest()).toMatchObject({
      url: "http://gw.test/patra-identity/me/sessions/723511223344556678",
      method: "DELETE",
    });
  });

  it("踢其他全部：DELETE /me/sessions/others", async () => {
    respond(200, { revokedSessions: 2 });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(revokeOtherSessions()).resolves.toEqual({ ok: true, revokedSessions: 2 });
    expect(lastRequest()).toMatchObject({
      url: "http://gw.test/patra-identity/me/sessions/others",
      method: "DELETE",
    });
  });

  it("403 REAUTH_REQUIRED：reauth-required", async () => {
    respond(403, { code: "IDN-0403", traits: ["REAUTH_REQUIRED"] });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(revokeOtherSessions()).resolves.toEqual({ ok: false, reason: "reauth-required" });
  });

  it("409 CURRENT_SESSION：unavailable", async () => {
    respond(409, { code: "IDN-0409", traits: ["CURRENT_SESSION"] });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(revokeSession("1")).resolves.toEqual({ ok: false, reason: "unavailable" });
  });

  it("id 不是数字：unavailable，不请求", async () => {
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(revokeSession("../others")).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
