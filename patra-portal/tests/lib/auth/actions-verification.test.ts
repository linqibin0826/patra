// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { login, register } from "@/lib/auth/actions";
import { requestPasswordReset } from "@/lib/identity/token-actions";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const CREDENTIALS = { email: "reader@example.com", password: "correct-horse-battery" };
const REGISTRATION = { ...CREDENTIALS, username: "reader" };
const VERIFICATION = "V".repeat(64);
const fetchMock = vi.fn<typeof fetch>();

const respondWith = (status: number, body: unknown) =>
  fetchMock.mockImplementation(async () => Response.json(body, { status }));

const required = {
  status: 422,
  errors: [{ field: "verificationToken", code: "VERIFICATION_REQUIRED" }],
};
const failed = {
  status: 422,
  errors: [{ field: "verificationToken", code: "VERIFICATION_FAILED" }],
};
const unavailable = { status: 503, traits: ["DEP_UNAVAILABLE", "VERIFICATION_UNAVAILABLE"] };

/// 最后一次请求的 JSON 请求体
const sentBody = (): Record<string, unknown> =>
  JSON.parse(String(fetchMock.mock.calls.at(-1)?.[1]?.body));

beforeEach(() => {
  resetCookieJar();
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test:9528");
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

describe.each([
  [
    "login",
    () => login({ ...CREDENTIALS, verificationToken: VERIFICATION }),
    () => login(CREDENTIALS),
  ],
  [
    "register",
    () => register({ ...REGISTRATION, verificationToken: VERIFICATION }),
    () => register(REGISTRATION),
  ],
] as const)("%s", (_name, withToken, withoutToken) => {
  it("把令牌放进请求体", async () => {
    respondWith(401, { status: 401 });

    await withToken();

    expect(sentBody().verificationToken).toBe(VERIFICATION);
  });

  it("没有令牌时请求体里没有这个字段", async () => {
    respondWith(401, { status: 401 });

    await withoutToken();

    expect(sentBody()).not.toHaveProperty("verificationToken");
  });

  it.each([
    ["verification-required", required],
    ["verification-failed", failed],
    ["verification-unavailable", unavailable],
  ])("映射成 %s，不当作意外响应记日志", async (reason, body) => {
    respondWith(body.status, body);

    await expect(withToken()).resolves.toEqual({ ok: false, reason });
    expect(console.error).not.toHaveBeenCalled();
  });

  it("普通的 503 仍然是 unavailable", async () => {
    respondWith(503, { status: 503, traits: ["DEP_UNAVAILABLE"] });

    await expect(withToken()).resolves.toEqual({ ok: false, reason: "unavailable" });
  });
});

describe("提交的超时", () => {
  /// 后端校验令牌最坏要 10 秒（单次上限 5 秒，重试一次），门户不能比它先放弃：
  /// 否则用户看到“没有成功”，后端却可能已经把账号建好了
  const timeoutSpy = () => vi.spyOn(AbortSignal, "timeout");

  it.each([
    ["login", () => login(CREDENTIALS)],
    ["register", () => register(REGISTRATION)],
    ["requestPasswordReset", () => requestPasswordReset(CREDENTIALS.email, VERIFICATION)],
  ] as const)("%s 等 15 秒", async (_name, call) => {
    const spy = timeoutSpy();
    respondWith(401, { status: 401 });

    await call();

    expect(spy).toHaveBeenCalledWith(15_000);
  });
});

describe("requestPasswordReset", () => {
  it("把令牌放进请求体", async () => {
    respondWith(202, { email: CREDENTIALS.email, nextRequestAt: "2026-11-05T10:00:00Z" });

    await requestPasswordReset(CREDENTIALS.email, VERIFICATION);

    expect(sentBody()).toEqual({ email: CREDENTIALS.email, verificationToken: VERIFICATION });
  });

  it.each([
    ["verification-required", required],
    ["verification-failed", failed],
    ["verification-unavailable", unavailable],
  ])("映射成 %s", async (reason, body) => {
    respondWith(body.status, body);

    await expect(requestPasswordReset(CREDENTIALS.email, VERIFICATION)).resolves.toEqual({
      ok: false,
      reason,
    });
  });
});
