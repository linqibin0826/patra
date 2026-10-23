// @vitest-environment node
import { SESSIONS, SESSIONS_RESPONSE } from "@tests/fixtures/sessions";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "@/app/api/me/sessions/route";

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

describe("GET /api/me/sessions", () => {
  it("没有会话 Cookie：401 signed-out，不请求网关", async () => {
    resetCookieJar();
    const res = await GET();
    expect(res.status).toBe(401);
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "signed-out" });
    expect(res.headers.get("Cache-Control")).toBe("private, no-store");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("200：转发列表", async () => {
    fetchMock.mockResolvedValue(Response.json(SESSIONS_RESPONSE));
    resetCookieJar({ patra_session: "patra_user_abc" });
    const res = await GET();
    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual({ sessions: SESSIONS });
    expect(res.headers.get("Cache-Control")).toBe("private, no-store");
    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://gw.test/patra-identity/me/sessions");
  });

  it("id 是数字（会丢精度）：按响应体不符处理", async () => {
    fetchMock.mockResolvedValue(
      Response.json({ sessions: [{ ...SESSIONS_RESPONSE.sessions[0], id: 1 }] }),
    );
    resetCookieJar({ patra_session: "patra_user_abc" });
    expect((await GET()).status).toBe(502);
  });

  it("响应体不符：502 unavailable 并记日志", async () => {
    fetchMock.mockResolvedValue(Response.json({ sessions: [{ id: "x" }] }));
    resetCookieJar({ patra_session: "patra_user_abc" });
    const res = await GET();
    expect(res.status).toBe(502);
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(console.error).toHaveBeenCalled();
  });

  it("401：会话失效，清 Cookie，401 signed-out", async () => {
    fetchMock.mockResolvedValue(Response.json({ code: "GW-0401" }, { status: 401 }));
    const jar = resetCookieJar({ patra_session: "patra_user_abc" });
    const res = await GET();
    expect(res.status).toBe(401);
    expect(jar.written).toEqual([CLEARED]);
  });

  it("网关抛错：502", async () => {
    fetchMock.mockRejectedValue(new Error("boom"));
    resetCookieJar({ patra_session: "patra_user_abc" });
    const res = await GET();
    expect(res.status).toBe(502);
  });
});
