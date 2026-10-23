// @vitest-environment node
import { ME_RESPONSE, USER } from "@tests/fixtures/user";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cancelDeletion, changeUsername } from "@/lib/identity/account-actions";

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

describe("cancelDeletion", () => {
  it("200：DELETE /me/deletion，返回恢复后的用户", async () => {
    respond(200, ME_RESPONSE);
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(cancelDeletion()).resolves.toEqual({ ok: true, user: USER });
    expect(lastRequest()).toMatchObject({
      url: "http://gw.test/patra-identity/me/deletion",
      method: "DELETE",
    });
  });

  it("200 但响应体缺字段：unavailable", async () => {
    respond(200, { userId: "1" });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(cancelDeletion()).resolves.toEqual({ ok: false, reason: "unavailable" });
  });

  it("401：signed-out，清掉会话 Cookie", async () => {
    respond(401, { code: "GW-0401" });
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(cancelDeletion()).resolves.toEqual({ ok: false, reason: "signed-out" });
    expect(jar.written).toEqual([CLEARED]);
  });

  it("没有会话 Cookie：signed-out，不请求网关", async () => {
    resetCookieJar();
    await expect(cancelDeletion()).resolves.toEqual({ ok: false, reason: "signed-out" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("网关抛错：unavailable", async () => {
    fetchMock.mockRejectedValue(new Error("boom"));
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(cancelDeletion()).resolves.toEqual({ ok: false, reason: "unavailable" });
  });
});

describe("changeUsername", () => {
  it("200：PATCH /me/username，返回新用户名与下次可改时间", async () => {
    respond(200, { username: "new-name", nextUsernameChangeAt: "2026-11-22T08:00:00Z" });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(changeUsername("new-name")).resolves.toEqual({
      ok: true,
      username: "new-name",
      nextUsernameChangeAt: "2026-11-22T08:00:00Z",
    });
    expect(lastRequest()).toEqual({
      url: "http://gw.test/patra-identity/me/username",
      method: "PATCH",
      body: { username: "new-name" },
    });
  });

  it("422：invalid，带原因码", async () => {
    respond(422, { code: "IDN-0422", errors: [{ field: "username", code: "UNCHANGED" }] });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(changeUsername("chen-yu")).resolves.toEqual({
      ok: false,
      reason: "invalid",
      code: "UNCHANGED",
    });
  });

  it("409 errors[] username / ALREADY_TAKEN：taken", async () => {
    respond(409, {
      code: "IDN-0409",
      traits: ["CONFLICT"],
      errors: [{ field: "username", code: "ALREADY_TAKEN" }],
    });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(changeUsername("taken")).resolves.toEqual({ ok: false, reason: "taken" });
  });

  it("409 USERNAME_CHANGE_COOLDOWN：cooldown", async () => {
    respond(409, { code: "IDN-0409", traits: ["CONFLICT", "USERNAME_CHANGE_COOLDOWN"] });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(changeUsername("again")).resolves.toEqual({ ok: false, reason: "cooldown" });
  });

  it("409 PENDING_DELETION：pending-deletion", async () => {
    respond(409, { code: "IDN-0409", traits: ["PENDING_DELETION", "CONFLICT"] });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(changeUsername("x-name")).resolves.toEqual({
      ok: false,
      reason: "pending-deletion",
    });
  });

  it("其余：unavailable", async () => {
    respond(500, { code: "IDN-0500" });
    resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(changeUsername("x-name")).resolves.toEqual({ ok: false, reason: "unavailable" });
  });
});
