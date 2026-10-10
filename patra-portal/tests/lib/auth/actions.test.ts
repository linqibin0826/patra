// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { login, register } from "@/lib/auth/actions";
import type { Credentials } from "@/lib/auth/types";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const TOKEN = "patra_user_abc";
const PASSWORD = "correct-horse-battery";
const CREDENTIALS: Credentials = { email: "reader@example.com", password: PASSWORD };
const USER = { userId: "319041872872550658", email: "reader@example.com" };
const SIGNED_IN = { sessionToken: TOKEN, ...USER };
/// 写入：令牌原文，有效期 30 天
const WRITTEN =
  /^patra_session=patra_user_abc; Path=\/; Expires=[^;]+; Max-Age=2592000; HttpOnly; SameSite=lax$/;
const UNAVAILABLE = { ok: false, reason: "unavailable" };
const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test:9528");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
  const logged = JSON.stringify(vi.mocked(console.error).mock.calls);
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
  // 每个用例都检查：日志里没有密码和令牌
  expect(logged).not.toContain(PASSWORD);
  expect(logged).not.toContain(TOKEN);
});

/// 网关的响应，每次调用都新建：同一个 Response 的响应体只能读一次
const respondWith = (status: number, body?: unknown, headers: Record<string, string> = {}) =>
  fetchMock.mockImplementation(async () =>
    body === undefined
      ? new Response(null, { status, headers })
      : Response.json(body, { status, headers }),
  );

/// ProblemDetail 响应体
const problem = (status: number, extra: Record<string, unknown> = {}) => ({
  status,
  code: `IDN-0${status}`,
  title: "x",
  ...extra,
});

const timeout = () =>
  fetchMock.mockRejectedValue(new DOMException("signal timed out", "TimeoutError"));
const networkError = () => fetchMock.mockRejectedValue(new TypeError("fetch failed"));

/// 表单直接把 Server Action 当作 action 提交时，收到的是 FormData
const asFormData = (fields: Record<string, string>) => {
  const form = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    form.set(name, value);
  }
  return form;
};

/// 第一次请求网关时的 init
const sent = () => fetchMock.mock.calls[0]?.[1] ?? {};

describe("register", () => {
  it("201：写入 Cookie，返回用户", async () => {
    respondWith(201, SIGNED_IN);
    const jar = resetCookieJar();
    await expect(register(CREDENTIALS)).resolves.toEqual({ ok: true, user: USER });
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN)]);
  });

  it("只转发 email 和 password，不带 Authorization，即使已经有 Cookie", async () => {
    respondWith(201, SIGNED_IN);
    resetCookieJar({ patra_session: "patra_user_old" });
    const withExtras = { ...CREDENTIALS, clientType: "WEB", deviceId: "d-1" };
    await register(withExtras);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://gw.test:9528/patra-identity/auth/register",
      expect.objectContaining({ method: "POST", headers: { "Content-Type": "application/json" } }),
    );
    expect(JSON.parse(String(sent().body))).toEqual(CREDENTIALS);
  });

  it("422：返回字段和原因码，其余字段（包括 rejectedValue）不带回浏览器", async () => {
    respondWith(
      422,
      problem(422, {
        errors: [
          { field: "email", code: "INVALID_FORMAT", rejectedValue: "reader@", message: "格式不对" },
          { field: "password", code: "TOO_SHORT", rejectedValue: PASSWORD, message: "太短" },
        ],
      }),
    );
    const jar = resetCookieJar();
    await expect(register(CREDENTIALS)).resolves.toEqual({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [
        { field: "email", code: "INVALID_FORMAT" },
        { field: "password", code: "TOO_SHORT" },
      ],
    });
    expect(jar.written).toEqual([]);
  });

  it("422：code 不是字符串时记为 null，field 不是字符串的项跳过", async () => {
    respondWith(
      422,
      problem(422, {
        errors: [
          { field: "password", code: 7 },
          { field: "email" },
          { code: "REQUIRED" },
          "x",
          null,
        ],
      }),
    );
    resetCookieJar();
    await expect(register(CREDENTIALS)).resolves.toEqual({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [
        { field: "password", code: null },
        { field: "email", code: null },
      ],
    });
  });

  it("409：email-taken，不写 Cookie", async () => {
    respondWith(409, problem(409));
    const jar = resetCookieJar();
    await expect(register(CREDENTIALS)).resolves.toEqual({ ok: false, reason: "email-taken" });
    expect(jar.written).toEqual([]);
  });

  it.each([
    ["201 但缺 sessionToken", () => respondWith(201, USER)],
    ["201 但 userId 不是字符串", () => respondWith(201, { ...SIGNED_IN, userId: 42 })],
    ["201 但响应体不是 JSON", () => respondWith(201)],
    ["422 但没有 errors", () => respondWith(422, problem(422))],
    [
      "422 但 errors 一项都取不到",
      () => respondWith(422, problem(422, { errors: [{ code: "X" }] })),
    ],
    ["401", () => respondWith(401, problem(401))],
    ["403", () => respondWith(403, problem(403))],
    ["429", () => respondWith(429, problem(429, { retryAfterSeconds: 60 }))],
    ["500", () => respondWith(500, problem(500))],
    ["503", () => respondWith(503, problem(503))],
    ["超时", timeout],
    ["网络错误", networkError],
  ])("%s：unavailable，不写 Cookie", async (_label, arrange) => {
    arrange();
    const jar = resetCookieJar();
    await expect(register(CREDENTIALS)).resolves.toEqual(UNAVAILABLE);
    expect(jar.written).toEqual([]);
  });

  it.each([
    ["缺 password", { email: "reader@example.com" }],
    ["email 不是字符串", { email: 42, password: PASSWORD }],
    ["不是对象", "reader@example.com"],
    ["是 FormData", asFormData({ email: "reader@example.com", password: PASSWORD })],
  ])("入参%s：unavailable，不请求网关", async (_label, input) => {
    resetCookieJar();
    await expect(register(input as unknown as Credentials)).resolves.toEqual(UNAVAILABLE);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("意外的状态码：记下接口、状态码和 code，不记响应体", async () => {
    respondWith(500, problem(500, { detail: "boom" }));
    resetCookieJar();
    await register(CREDENTIALS);
    expect(console.error).toHaveBeenCalledWith(
      "[auth] /patra-identity/auth/register 返回了意外的结果",
      { status: 500, code: "IDN-0500" },
    );
  });
});

describe("login", () => {
  it("200：写入 Cookie，返回用户", async () => {
    respondWith(200, SIGNED_IN);
    const jar = resetCookieJar();
    await expect(login(CREDENTIALS)).resolves.toEqual({ ok: true, user: USER });
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN)]);
  });

  it("只转发 email 和 password，不带 Authorization，即使已经有 Cookie", async () => {
    respondWith(200, SIGNED_IN);
    resetCookieJar({ patra_session: "patra_user_old" });
    const withExtras = { ...CREDENTIALS, clientType: "WEB", deviceId: "d-1" };
    await login(withExtras);
    expect(fetchMock).toHaveBeenCalledWith(
      "http://gw.test:9528/patra-identity/auth/login",
      expect.objectContaining({ method: "POST", headers: { "Content-Type": "application/json" } }),
    );
    expect(JSON.parse(String(sent().body))).toEqual(CREDENTIALS);
  });

  it("已经登录时再登录：新令牌覆盖 Cookie，不去作废旧会话", async () => {
    respondWith(200, SIGNED_IN);
    const jar = resetCookieJar({ patra_session: "patra_user_old" });
    await expect(login(CREDENTIALS)).resolves.toEqual({ ok: true, user: USER });
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN)]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("422：invalid-fields", async () => {
    respondWith(422, problem(422, { errors: [{ field: "password", code: "REQUIRED" }] }));
    resetCookieJar();
    await expect(login(CREDENTIALS)).resolves.toEqual({
      ok: false,
      reason: "invalid-fields",
      fieldErrors: [{ field: "password", code: "REQUIRED" }],
    });
  });

  it.each([
    [401, "bad-credentials"],
    [403, "banned"],
  ])("%i：%s，不写 Cookie", async (status, reason) => {
    respondWith(status, problem(status));
    const jar = resetCookieJar();
    await expect(login(CREDENTIALS)).resolves.toEqual({ ok: false, reason });
    expect(jar.written).toEqual([]);
  });

  it.each([
    ["响应体的 retryAfterSeconds 优先", { retryAfterSeconds: 120 }, { "Retry-After": "90" }, 120],
    ["响应体为 0", { retryAfterSeconds: 0 }, {}, 0],
    ["响应体没有，取 Retry-After 头", {}, { "Retry-After": "90" }, 90],
    ["响应体是负数，取 Retry-After 头", { retryAfterSeconds: -1 }, { "Retry-After": "90" }, 90],
    ["响应体是小数，没有头", { retryAfterSeconds: 1.5 }, {}, null],
    ["Retry-After 是日期", {}, { "Retry-After": "Sat, 10 Oct 2026 08:00:00 GMT" }, null],
    ["都没有", {}, {}, null],
  ])("429（%s）：throttled", async (_label, extra, headers, expected) => {
    respondWith(429, problem(429, extra), headers);
    const jar = resetCookieJar();
    await expect(login(CREDENTIALS)).resolves.toEqual({
      ok: false,
      reason: "throttled",
      retryAfterSeconds: expected,
    });
    expect(jar.written).toEqual([]);
  });

  it.each([
    ["200 但缺 sessionToken", () => respondWith(200, USER)],
    ["422 但没有 errors", () => respondWith(422, problem(422))],
    ["409", () => respondWith(409, problem(409))],
    ["500", () => respondWith(500, problem(500))],
    ["503", () => respondWith(503, problem(503))],
    ["超时", timeout],
    ["网络错误", networkError],
  ])("%s：unavailable，不写 Cookie", async (_label, arrange) => {
    arrange();
    const jar = resetCookieJar();
    await expect(login(CREDENTIALS)).resolves.toEqual(UNAVAILABLE);
    expect(jar.written).toEqual([]);
  });

  it("入参形状不对：unavailable，不请求网关", async () => {
    resetCookieJar();
    const input = { email: "reader@example.com" } as unknown as Credentials;
    await expect(login(input)).resolves.toEqual(UNAVAILABLE);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
