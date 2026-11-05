// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "@/app/api/me/login-methods/route";

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

const BODY = {
  hasPassword: true,
  linked: [
    {
      provider: "github",
      accountLabel: "octocat",
      linkedAt: "2026-10-21T00:00:00Z",
      lastLoginAt: null,
    },
  ],
};

describe("GET /api/me/login-methods", () => {
  it("200：原样给浏览器；不缓存", async () => {
    resetCookieJar({ patra_session: "patra_user_abc" });
    fetchMock.mockResolvedValue(Response.json(BODY));
    const res = await GET();
    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual(BODY);
    expect(res.headers.get("Cache-Control")).toBe("private, no-store");
    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://gw.test/patra-identity/me/login-methods");
  });

  it("没有会话：401，不请求", async () => {
    resetCookieJar();
    const res = await GET();
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("会话失效：清 Cookie，401", async () => {
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    fetchMock.mockResolvedValue(Response.json({ code: "GW-0401" }, { status: 401 }));
    const res = await GET();
    expect(res.status).toBe(401);
    expect(jar.written[0]).toMatch(/^patra_session=;/);
  });

  it.each([
    ["其他状态码", () => Response.json({ code: "IDN-0500" }, { status: 500 })],
    ["响应体不对", () => Response.json({ linked: "x" })],
    ["平台不认识", () => Response.json({ hasPassword: true, linked: [{ provider: "wechat" }] })],
  ])("%s：502", async (_name, reply) => {
    resetCookieJar({ patra_session: "patra_user_abc" });
    fetchMock.mockResolvedValue(reply());
    expect((await GET()).status).toBe(502);
  });
});
