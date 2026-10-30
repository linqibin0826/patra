// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { saveNotificationMode } from "@/lib/notification/preference-actions";
import type { NotificationMode, PreferenceCategory } from "@/lib/notification/schema";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const CLEARED = "patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax";

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar({ patra_session: "patra_user_abc" });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

const respond = (status: number, body: unknown) =>
  fetchMock.mockImplementation(async () => Response.json(body, { status }));

describe("saveNotificationMode", () => {
  it("PUT 这一类的档位，返回两类当前的档位", async () => {
    respond(200, { reply: "INSTANT", mention: "OFF" });
    await expect(saveNotificationMode("MENTION", "OFF")).resolves.toEqual({
      ok: true,
      preferences: { reply: "INSTANT", mention: "OFF" },
    });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-notification/me/notification-preferences/MENTION");
    expect(init?.method).toBe("PUT");
    expect(JSON.parse(String(init?.body))).toEqual({ mode: "OFF" });
    expect(init?.headers).toMatchObject({ Authorization: "Bearer patra_user_abc" });
  });

  it("入参不合格（有帮助、不认识的档位）：unavailable，不请求网关", async () => {
    await expect(saveNotificationMode("HELPFUL" as PreferenceCategory, "OFF")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    await expect(saveNotificationMode("REPLY", "LOUD" as NotificationMode)).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("没有 Cookie：signed-out，不请求网关", async () => {
    resetCookieJar();
    await expect(saveNotificationMode("REPLY", "OFF")).resolves.toEqual({
      ok: false,
      reason: "signed-out",
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("401：signed-out，并清掉会话 Cookie", async () => {
    respond(401, { code: "GW-0401" });
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    await expect(saveNotificationMode("REPLY", "OFF")).resolves.toEqual({
      ok: false,
      reason: "signed-out",
    });
    expect(jar.written).toEqual([CLEARED]);
  });

  it("422（门户发错了值）、503、形状不对：unavailable 并记日志", async () => {
    respond(422, { code: "NOTIFICATION-0422", traits: ["RULE_VIOLATION"] });
    await expect(saveNotificationMode("REPLY", "OFF")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    respond(503, { code: "GW-0503" });
    await expect(saveNotificationMode("REPLY", "OFF")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    respond(200, { reply: "OFF" });
    await expect(saveNotificationMode("REPLY", "OFF")).resolves.toEqual({
      ok: false,
      reason: "unavailable",
    });
    expect(console.error).toHaveBeenCalledTimes(3);
  });
});
