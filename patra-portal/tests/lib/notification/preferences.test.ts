// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchMyNotificationPreferences } from "@/lib/notification/preferences";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();

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

describe("fetchMyNotificationPreferences", () => {
  it("200：两类当前的档位", async () => {
    respond(200, { reply: "DIGEST", mention: "INSTANT" });
    await expect(fetchMyNotificationPreferences()).resolves.toEqual({
      reply: "DIGEST",
      mention: "INSTANT",
    });
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "http://gw.test/patra-notification/me/notification-preferences",
    );
  });

  it("没有 Cookie：signed-out，不请求网关", async () => {
    resetCookieJar();
    await expect(fetchMyNotificationPreferences()).resolves.toBe("signed-out");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("401：signed-out", async () => {
    respond(401, { code: "GW-0401" });
    await expect(fetchMyNotificationPreferences()).resolves.toBe("signed-out");
  });

  it("503、形状不对、请求抛错：unavailable 并记日志，不抛错", async () => {
    respond(503, { code: "GW-0503" });
    await expect(fetchMyNotificationPreferences()).resolves.toBe("unavailable");
    respond(200, { reply: "SOMETIMES", mention: "INSTANT" });
    await expect(fetchMyNotificationPreferences()).resolves.toBe("unavailable");
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    await expect(fetchMyNotificationPreferences()).resolves.toBe("unavailable");
    expect(console.error).toHaveBeenCalledTimes(3);
  });
});
