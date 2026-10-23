// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { callIdentity } from "@/lib/identity/request";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  resetCookieJar(
    { patra_session: "patra_user_abc" },
    { "x-forwarded-for": "10.0.0.5", "user-agent": "UA/5" },
  );
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

describe("callIdentity", () => {
  it("带令牌与浏览器来源请求 /patra-identity 下的路径，2xx 是 ok", async () => {
    fetchMock.mockResolvedValue(Response.json({ a: 1 }));
    const reply = await callIdentity("/me/profile");
    expect(reply).toMatchObject({ kind: "ok", body: { a: 1 } });
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/me/profile");
    expect(init?.headers).toEqual({
      Authorization: "Bearer patra_user_abc",
      "X-Forwarded-For": "10.0.0.5",
      "User-Agent": "UA/5",
    });
  });

  it("没有 Cookie 且要求登录：no-session，不请求网关", async () => {
    resetCookieJar();
    await expect(callIdentity("/me/profile")).resolves.toEqual({ kind: "no-session" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("令牌放不进请求头：同样 no-session", async () => {
    resetCookieJar({ patra_session: "abc\n" });
    await expect(callIdentity("/me/profile")).resolves.toEqual({ kind: "no-session" });
  });

  it("可选登录且没有 Cookie：不带令牌照常请求", async () => {
    resetCookieJar();
    fetchMock.mockResolvedValue(Response.json({}));
    await callIdentity("/users/chen-yu", {}, { auth: "optional" });
    expect(fetchMock.mock.calls[0]?.[1]?.headers).not.toHaveProperty("Authorization");
  });

  it("301 也是 ok（旧用户名跳转要读体）", async () => {
    fetchMock.mockResolvedValue(new Response(JSON.stringify({ username: "new" }), { status: 301 }));
    await expect(callIdentity("/users/old", {}, { auth: "optional" })).resolves.toMatchObject({
      kind: "ok",
      body: { username: "new" },
    });
  });

  it("422 是 problem，带字段错误", async () => {
    fetchMock.mockResolvedValue(
      Response.json(
        { code: "IDN-0422", errors: [{ field: "bio", code: "TOO_LONG" }] },
        { status: 422 },
      ),
    );
    const reply = await callIdentity("/me/profile", { method: "PUT", json: {} });
    expect(reply.kind).toBe("problem");
    expect(reply.kind === "problem" && reply.problem.errors).toEqual([
      { field: "bio", code: "TOO_LONG" },
    ]);
  });

  it("Next 的动态渲染信号原样抛出，不当成 error", async () => {
    const dynamic = Object.assign(new Error("Dynamic server usage"), {
      digest: "DYNAMIC_SERVER_USAGE",
    });
    const jar = await import("@tests/stubs/cookie-jar");
    vi.spyOn(jar, "cookies").mockRejectedValueOnce(dynamic);
    await expect(callIdentity("/me/profile")).rejects.toBe(dynamic);
  });

  it("请求抛错：error，并记日志", async () => {
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    await expect(callIdentity("/me/profile")).resolves.toEqual({ kind: "error" });
    expect(console.error).toHaveBeenCalled();
  });
});
