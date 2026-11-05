// @vitest-environment node
import { ME_RESPONSE, USER } from "@tests/fixtures/user";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { POST as postCode } from "@/app/api/auth/oauth/registration/code/route";
import { POST as postComplete } from "@/app/api/auth/oauth/registration/complete/route";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const DEVICE = "d".repeat(43);

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

const FILL = { username: "chen-yu", email: "chen@example.com" };

function withTicket(extra: Record<string, string> = {}) {
  return resetCookieJar({ patra_oauth_ticket: "ticket-1", patra_device: DEVICE, ...extra });
}

describe("POST /api/auth/oauth/registration/code", () => {
  it("跨站：403，不请求网关", async () => {
    withTicket();
    const res = await postCode(request("code", FILL, { origin: "http://evil.test" }));
    expect(res.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("没有 ticket Cookie：expired，不请求网关", async () => {
    resetCookieJar();
    const res = await postCode(request("code", FILL));
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "expired" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("请求体形状不对：unavailable，不请求网关", async () => {
    withTicket();
    const res = await postCode(request("code", { username: 1 }));
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("202：带 ticket 头发给 identity，只交用户名和邮箱", async () => {
    withTicket();
    const sent = {
      email: FILL.email,
      codeExpiresAt: "2026-11-04T09:20:00Z",
      nextResendAt: "2026-11-04T09:11:00Z",
    };
    fetchMock.mockResolvedValue(Response.json(sent, { status: 202 }));
    const res = await postCode(request("code", { ...FILL, extra: "x" }));
    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual({ ok: true, ...sent });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/auth/oauth/registration/code");
    expect(init?.headers).toMatchObject({ "X-Patra-Registration-Ticket": "ticket-1" });
    expect(JSON.parse(String(init?.body))).toEqual(FILL);
  });

  it.each([
    [
      "ticket 失效",
      problem(422, { errors: [{ field: "ticket", code: "INVALID" }] }),
      { ok: false, reason: "expired" },
    ],
    [
      "字段错误",
      problem(422, { errors: [{ field: "username", code: "RESERVED" }] }),
      {
        ok: false,
        reason: "invalid-fields",
        fieldErrors: [{ field: "username", code: "RESERVED" }],
      },
    ],
    [
      "邮箱已注册",
      problem(409, { errors: [{ field: "email", code: "ALREADY_REGISTERED" }] }),
      { ok: false, reason: "taken", email: true, username: false },
    ],
    [
      "用户名被占用",
      problem(409, { errors: [{ field: "username", code: "ALREADY_TAKEN" }] }),
      { ok: false, reason: "taken", email: false, username: true },
    ],
    [
      "太频繁",
      problem(429, { retryAfterSeconds: 900 }),
      { ok: false, reason: "throttled", retryAfterSeconds: 900 },
    ],
    ["其他", problem(500), { ok: false, reason: "unavailable" }],
    ["409 认不出", problem(409), { ok: false, reason: "unavailable" }],
    ["422 没有用户字段", problem(422, { errors: [] }), { ok: false, reason: "unavailable" }],
  ])("%s", async (_name, reply, result) => {
    withTicket();
    fetchMock.mockResolvedValue(reply);
    const res = await postCode(request("code", FILL));
    await expect(res.json()).resolves.toEqual(result);
    expect(res.status).toBeGreaterThanOrEqual(400);
  });

  it("网关不可达：unavailable；日志里没有 ticket", async () => {
    withTicket();
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    const res = await postCode(request("code", FILL));
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(JSON.stringify(vi.mocked(console.error).mock.calls)).not.toContain("ticket-1");
  });
});

describe("POST /api/auth/oauth/registration/complete", () => {
  it("跨站：403", async () => {
    withTicket();
    const res = await postComplete(
      request("complete", { code: "483920" }, { origin: "http://evil.test" }),
    );
    expect(res.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("201：写会话、清 ticket，回用户；带 clientType 和设备标识", async () => {
    const jar = withTicket();
    fetchMock.mockResolvedValue(
      Response.json({ sessionToken: "patra_user_new", user: ME_RESPONSE }, { status: 201 }),
    );
    const res = await postComplete(request("complete", { code: "483920" }));
    await expect(res.json()).resolves.toEqual({ ok: true, user: USER });
    expect(res.headers.get("Cache-Control")).toBe("private, no-store");
    expect(jar.written.some((line) => line.startsWith("patra_session=patra_user_new;"))).toBe(true);
    expect(jar.written.some((line) => /^patra_oauth_ticket=;.*Max-Age=0/.test(line))).toBe(true);
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/auth/oauth/registration/complete");
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
      "ticket 失效",
      problem(422, { errors: [{ field: "ticket", code: "INVALID" }] }),
      { ok: false, reason: "expired" },
    ],
    [
      "用户名被抢",
      problem(409, { errors: [{ field: "username", code: "ALREADY_TAKEN" }] }),
      { ok: false, reason: "taken", email: false, username: true },
    ],
    [
      "邮箱被抢",
      problem(409, { errors: [{ field: "email", code: "ALREADY_REGISTERED" }] }),
      { ok: false, reason: "taken", email: true, username: false },
    ],
    [
      "第三方账号已注册",
      problem(409, { traits: ["CONFLICT", "PROVIDER_ACCOUNT_TAKEN"] }),
      { ok: false, reason: "provider-taken" },
    ],
    ["其他", problem(500), { ok: false, reason: "unavailable" }],
  ])("%s", async (_name, reply, result) => {
    const jar = withTicket();
    fetchMock.mockResolvedValue(reply);
    const res = await postComplete(request("complete", { code: "483920" }));
    await expect(res.json()).resolves.toEqual(result);
    expect(jar.written.some((line) => line.startsWith("patra_session="))).toBe(false);
  });

  it("201 响应体不对：unavailable，不写会话，ticket 留着", async () => {
    const jar = withTicket();
    fetchMock.mockResolvedValue(Response.json({ sessionToken: "x" }, { status: 201 }));
    const res = await postComplete(request("complete", { code: "483920" }));
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "unavailable" });
    expect(jar.written.some((line) => line.startsWith("patra_oauth_ticket="))).toBe(false);
  });
});

describe("ticket 只认 Cookie（identity 不把它绑定到浏览器，防注入全靠门户）", () => {
  const INJECTED = "attacker-ticket";

  /// 带着三种注入方式的请求：查询串、请求体、浏览器自己带的请求头
  function injected(path: string, body: Record<string, string>) {
    return new Request(
      `http://portal.test/api/auth/oauth/registration/${path}?ticket=${INJECTED}`,
      {
        method: "POST",
        headers: {
          origin: "http://portal.test",
          host: "portal.test",
          "content-type": "application/json",
          "X-Patra-Registration-Ticket": INJECTED,
        },
        body: JSON.stringify({ ...body, ticket: INJECTED }),
      },
    );
  }

  it.each([
    ["发验证码", "code", postCode, FILL],
    ["提交验证码", "complete", postComplete, { code: "483920" }],
  ])("%s · 没有 Cookie：别处给的 ticket 不算数，不请求网关", async (_name, path, post, body) => {
    resetCookieJar();
    const res = await post(injected(path, body));
    await expect(res.json()).resolves.toEqual({ ok: false, reason: "expired" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["发验证码", "code", postCode, FILL],
    ["提交验证码", "complete", postComplete, { code: "483920" }],
  ])(
    "%s · 有 Cookie：发给 identity 的是 Cookie 的值，请求体里没有 ticket",
    async (_name, path, post, body) => {
      withTicket();
      fetchMock.mockResolvedValue(problem(500));
      await post(injected(path, body));
      const init = fetchMock.mock.calls[0]?.[1];
      expect(init?.headers).toMatchObject({ "X-Patra-Registration-Ticket": "ticket-1" });
      expect(String(init?.body)).not.toContain(INJECTED);
      expect(JSON.stringify(init?.headers)).not.toContain(INJECTED);
    },
  );
});
