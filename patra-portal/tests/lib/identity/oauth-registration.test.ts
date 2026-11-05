// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchPendingRegistration } from "@/lib/identity/oauth-registration";

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

const PENDING = {
  provider: "github",
  accountLabel: "octocat",
  suggestedUsername: "octocat",
  suggestedEmail: null,
  expiresAt: "2026-11-04T09:30:00Z",
  pendingCode: null,
};

describe("fetchPendingRegistration", () => {
  it("没有 ticket：expired，不请求网关", async () => {
    resetCookieJar();
    await expect(fetchPendingRegistration()).resolves.toBe("expired");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("读的是 Cookie 的值：ticket 改坏放不进请求头时当过期，不请求网关", async () => {
    resetCookieJar({ patra_oauth_ticket: "bad\nheader" });
    await expect(fetchPendingRegistration()).resolves.toBe("expired");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("200：带 ticket 头，返回补填内容", async () => {
    resetCookieJar({ patra_oauth_ticket: "ticket-1" });
    fetchMock.mockResolvedValue(Response.json(PENDING));
    await expect(fetchPendingRegistration()).resolves.toEqual(PENDING);
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/auth/oauth/registration");
    expect(init?.headers).toMatchObject({ "X-Patra-Registration-Ticket": "ticket-1" });
  });

  it("绑定用的 pendingCode：purpose 是 LINK，username 为 null", async () => {
    resetCookieJar({ patra_oauth_ticket: "ticket-1" });
    const pendingCode = {
      purpose: "LINK",
      email: "chen@example.com",
      username: null,
      codeExpiresAt: "2026-11-04T09:20:00Z",
      nextResendAt: "2026-11-04T09:11:00Z",
    };
    fetchMock.mockResolvedValue(Response.json({ ...PENDING, pendingCode }));
    await expect(fetchPendingRegistration()).resolves.toMatchObject({ pendingCode });
  });

  it("pendingCode 有值时原样带回，带提交时的用户名", async () => {
    resetCookieJar({ patra_oauth_ticket: "ticket-1" });
    const pendingCode = {
      purpose: "REGISTER",
      email: "chen@example.com",
      username: "chen-yu",
      codeExpiresAt: "2026-11-04T09:20:00Z",
      nextResendAt: "2026-11-04T09:11:00Z",
    };
    fetchMock.mockResolvedValue(Response.json({ ...PENDING, pendingCode }));
    await expect(fetchPendingRegistration()).resolves.toMatchObject({ pendingCode });
  });

  it("422 ticket：expired", async () => {
    resetCookieJar({ patra_oauth_ticket: "ticket-1" });
    fetchMock.mockResolvedValue(
      Response.json({ errors: [{ field: "ticket", code: "INVALID" }] }, { status: 422 }),
    );
    await expect(fetchPendingRegistration()).resolves.toBe("expired");
  });

  it.each([
    ["其他状态码", () => new Response(null, { status: 500 })],
    ["响应体不对", () => Response.json({ provider: "wechat" })],
    [
      "pendingCode 的 purpose 不认识",
      () =>
        Response.json({
          ...PENDING,
          pendingCode: {
            purpose: "OTHER",
            username: "chen-yu",
            email: "chen@example.com",
            codeExpiresAt: "2026-11-04T09:20:00Z",
            nextResendAt: "2026-11-04T09:11:00Z",
          },
        }),
    ],
    [
      "注册用的 pendingCode 缺用户名",
      () =>
        Response.json({
          ...PENDING,
          pendingCode: {
            purpose: "REGISTER",
            username: null,
            email: "chen@example.com",
            codeExpiresAt: "2026-11-04T09:20:00Z",
            nextResendAt: "2026-11-04T09:11:00Z",
          },
        }),
    ],
  ])("%s：抛错，交给错误页", async (_name, reply) => {
    resetCookieJar({ patra_oauth_ticket: "ticket-1" });
    fetchMock.mockResolvedValue(reply());
    await expect(fetchPendingRegistration()).rejects.toThrow("补填内容暂时不可用");
  });
});
