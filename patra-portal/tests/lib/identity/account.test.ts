// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchPendingEmailChange } from "@/lib/identity/account";

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

describe("fetchPendingEmailChange", () => {
  it("200：返回待确认的新邮箱与到期时间", async () => {
    fetchMock.mockResolvedValue(
      Response.json({ newEmail: "new@example.com", expiresAt: "2026-10-23T08:42:00Z" }),
    );
    await expect(fetchPendingEmailChange()).resolves.toEqual({
      newEmail: "new@example.com",
      expiresAt: "2026-10-23T08:42:00Z",
    });
    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://gw.test/patra-identity/me/email/change");
  });

  it("204：没有待确认，null", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await expect(fetchPendingEmailChange()).resolves.toBeNull();
  });

  it("401：signed-out", async () => {
    fetchMock.mockResolvedValue(Response.json({ code: "GW-0401" }, { status: 401 }));
    await expect(fetchPendingEmailChange()).resolves.toBe("signed-out");
  });

  it("没有会话 Cookie：signed-out，不请求", async () => {
    resetCookieJar();
    await expect(fetchPendingEmailChange()).resolves.toBe("signed-out");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("500：抛错交给错误页", async () => {
    fetchMock.mockResolvedValue(Response.json({ code: "IDN-0500" }, { status: 500 }));
    await expect(fetchPendingEmailChange()).rejects.toThrow();
  });
});
