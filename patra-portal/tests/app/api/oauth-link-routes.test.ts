// @vitest-environment node
import { ME_RESPONSE, USER } from "@tests/fixtures/user";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { POST as postLink } from "@/app/api/auth/oauth/registration/link/route";
import { POST as postLinkCode } from "@/app/api/auth/oauth/registration/link-code/route";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const DEVICE = "d".repeat(43);
const EMAIL = { email: "chen.yu@example.com" };
const SENT = {
  email: EMAIL.email,
  codeExpiresAt: "2026-11-05T09:20:00Z",
  nextResendAt: "2026-11-05T09:11:00Z",
};

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

function request(path: string, body: unknown, headers: Record<string, string> = {}) {
  return new Request(`http://portal.test/api/auth/oauth/registration/${path}`, {
    method: "POST",
    headers: {
      origin: "http://portal.test",
      host: "portal.test",
      "content-type": "application/json",
      ...headers,
    },
    body: JSON.stringify(body),
  });
}

function problem(status: number, extra: Record<string, unknown> = {}) {
  return Response.json({ code: `IDN-0${status}`, ...extra }, { status });
}

function withTicket() {
  return resetCookieJar({ patra_oauth_ticket: "ticket-1", patra_device: DEVICE });
}

describe("POST /api/auth/oauth/registration/link-code", () => {
  it("跨站：403，不请求网关", async () => {
    withTicket();
    const res = await postLinkCode(request("link-code", EMAIL, { origin: "http://evil.test" }));
    expect(res.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("没有 ticket：expired，不请求网关", async () => {
    resetCookieJar();
    const res = await postLinkCode(request("link-code", EMAIL));
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "expired" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("202：带 ticket 头，只交邮箱，原样带回时间", async () => {
    withTicket();
    fetchMock.mockResolvedValue(Response.json(SENT, { status: 202 }));
    const res = await postLinkCode(
      request("link-code", { ...EMAIL, username: "x", ticket: "attacker" }),
    );
    await expect(res.json()).resolves.toEqual({ ok: true, ...SENT });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/auth/oauth/registration/link-code");
    expect(init?.headers).toMatchObject({ "X-Patra-Registration-Ticket": "ticket-1" });
    expect(JSON.parse(String(init?.body))).toEqual(EMAIL);
  });

  it.each([
    [
      "ticket 失效",
      problem(422, { errors: [{ field: "ticket", code: "INVALID" }] }),
      { ok: false, reason: "expired" },
    ],
    [
      "邮箱格式",
      problem(422, { errors: [{ field: "email", code: "INVALID_FORMAT" }] }),
      {
        ok: false,
        reason: "invalid-fields",
        fieldErrors: [{ field: "email", code: "INVALID_FORMAT" }],
      },
    ],
    [
      "太频繁",
      problem(429, { retryAfterSeconds: 60 }),
      { ok: false, reason: "throttled", retryAfterSeconds: 60 },
    ],
    ["其他", problem(500), { ok: false, reason: "unavailable" }],
    [
      "这个接口不该回撞车",
      problem(409, { errors: [{ field: "email", code: "ALREADY_REGISTERED" }] }),
      { ok: false, reason: "unavailable" },
    ],
  ])("%s", async (_name, reply, result) => {
    withTicket();
    fetchMock.mockResolvedValue(reply);
    const res = await postLinkCode(request("link-code", EMAIL));
    await expect(res.json()).resolves.toEqual(result);
    expect(res.status).toBeGreaterThanOrEqual(400);
  });
});

describe("POST /api/auth/oauth/registration/link", () => {
  it("跨站：403", async () => {
    withTicket();
    const res = await postLink(request("link", { code: "483920" }, { origin: "http://evil.test" }));
    expect(res.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("没有 ticket：expired，不请求网关", async () => {
    resetCookieJar();
    const res = await postLink(request("link", { code: "483920", ticket: "attacker" }));
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "expired" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("200：写会话、清 ticket，回用户；带 clientType 和设备标识", async () => {
    const jar = withTicket();
    fetchMock.mockResolvedValue(
      Response.json({ sessionToken: "patra_user_new", user: ME_RESPONSE }),
    );
    const res = await postLink(request("link", { code: "483920" }));
    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual({ ok: true, user: USER });
    expect(res.headers.get("Cache-Control")).toBe("private, no-store");
    expect(jar.written.some((line) => line.startsWith("patra_session=patra_user_new;"))).toBe(true);
    expect(jar.written.some((line) => /^patra_oauth_ticket=;.*Max-Age=0/.test(line))).toBe(true);
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/auth/oauth/registration/link");
    expect(init?.headers).toMatchObject({ "X-Patra-Registration-Ticket": "ticket-1" });
    expect(JSON.parse(String(init?.body))).toEqual({
      code: "483920",
      clientType: "web",
      deviceId: DEVICE,
    });
  });

  it.each([
    [
      "验证码不对",
      problem(422, { errors: [{ field: "code", code: "INVALID" }] }),
      { ok: false, reason: "code-invalid" },
    ],
    [
      "格式不对",
      problem(422, { errors: [{ field: "code", code: "INVALID_FORMAT" }] }),
      { ok: false, reason: "code-invalid" },
    ],
    [
      "验证码失效",
      problem(422, { errors: [{ field: "code", code: "EXPIRED" }] }),
      { ok: false, reason: "code-expired" },
    ],
    [
      "重放",
      problem(422, { errors: [{ field: "ticket", code: "INVALID" }] }),
      { ok: false, reason: "expired" },
    ],
    [
      "第三方账号已绑给别人",
      problem(409, { traits: ["CONFLICT", "PROVIDER_ACCOUNT_TAKEN"] }),
      { ok: false, reason: "provider-taken" },
    ],
    [
      "目标账号已绑这一家",
      problem(409, { traits: ["CONFLICT", "PROVIDER_ALREADY_LINKED"] }),
      { ok: false, reason: "already-linked" },
    ],
    ["其他", problem(500), { ok: false, reason: "unavailable" }],
  ])("%s：不写会话", async (_name, reply, result) => {
    const jar = withTicket();
    fetchMock.mockResolvedValue(reply);
    const res = await postLink(request("link", { code: "483920" }));
    await expect(res.json()).resolves.toEqual(result);
    expect(jar.written.some((line) => line.startsWith("patra_session="))).toBe(false);
  });

  it("注册的 201 不算绑定成功", async () => {
    const jar = withTicket();
    fetchMock.mockResolvedValue(
      Response.json({ sessionToken: "x", user: ME_RESPONSE }, { status: 201 }),
    );
    const res = await postLink(request("link", { code: "483920" }));
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(jar.written.some((line) => line.startsWith("patra_session="))).toBe(false);
  });
});
