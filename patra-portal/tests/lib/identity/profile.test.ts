// @vitest-environment node
import { MY_PROFILE, PUBLIC_PRIVACY } from "@tests/fixtures/profile";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchMyPrivacy, fetchMyProfile } from "@/lib/identity/profile";

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

describe("fetchMyProfile", () => {
  it("200：解析出资料", async () => {
    respond(200, MY_PROFILE);
    await expect(fetchMyProfile()).resolves.toEqual(MY_PROFILE);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://gw.test/patra-identity/me/profile");
  });

  it("401 或没有 Cookie：signed-out", async () => {
    respond(401, { code: "GW-0401" });
    await expect(fetchMyProfile()).resolves.toBe("signed-out");
    resetCookieJar();
    await expect(fetchMyProfile()).resolves.toBe("signed-out");
  });

  it.each([
    ["500", () => respond(500, { code: "IDN-0500" })],
    ["200 但形状不对", () => respond(200, { displayName: 1 })],
    ["网络错误", () => fetchMock.mockRejectedValue(new TypeError("fetch failed"))],
  ])("%s：抛错", async (_label, arrange) => {
    arrange();
    await expect(fetchMyProfile()).rejects.toThrow();
  });
});

describe("fetchMyPrivacy", () => {
  it("200：解析出三项设置", async () => {
    respond(200, PUBLIC_PRIVACY);
    await expect(fetchMyPrivacy()).resolves.toEqual(PUBLIC_PRIVACY);
    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://gw.test/patra-identity/me/privacy");
  });

  it("401：signed-out", async () => {
    respond(401, { code: "GW-0401" });
    await expect(fetchMyPrivacy()).resolves.toBe("signed-out");
  });
});
