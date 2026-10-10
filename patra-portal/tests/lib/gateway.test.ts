// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { gatewayFetch } from "@/lib/gateway";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test:9528");
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

/// 第 n 次调用 fetch 时传入的 [url, init]
const call = (n = 0) => fetchMock.mock.calls[n] ?? [];

describe("gatewayFetch", () => {
  it("在网关地址后拼上路径，默认 GET，不缓存", async () => {
    await gatewayFetch("/patra-catalog/portal/publications/42");
    const [url, init] = call();
    expect(url).toBe("http://gw.test:9528/patra-catalog/portal/publications/42");
    expect(init).toMatchObject({ method: "GET", cache: "no-store" });
    expect(init?.signal).toBeInstanceOf(AbortSignal);
  });

  it("超时默认 10 秒，可以按调用覆盖", async () => {
    const timeout = vi.spyOn(AbortSignal, "timeout");
    await gatewayFetch("/a");
    await gatewayFetch("/b", { method: "HEAD", timeoutMs: 3_000 });
    expect(timeout.mock.calls).toEqual([[10_000], [3_000]]);
    expect(call(1)[1]?.method).toBe("HEAD");
  });

  it("有 json 时序列化成请求体，并带 Content-Type", async () => {
    await gatewayFetch("/patra-identity/auth/login", {
      method: "POST",
      json: { email: "reader@example.com", password: "secret-pass" },
    });
    const [, init] = call();
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe('{"email":"reader@example.com","password":"secret-pass"}');
    expect(init?.headers).toEqual({ "Content-Type": "application/json" });
  });

  it("有令牌时带 Authorization: Bearer", async () => {
    await gatewayFetch("/patra-identity/auth/me", { token: "patra_user_abc" });
    expect(call()[1]?.headers).toEqual({ Authorization: "Bearer patra_user_abc" });
  });

  it("没有令牌也没有 json 时，不带请求头和请求体", async () => {
    await gatewayFetch("/patra-catalog/portal/venues");
    const [, init] = call();
    expect(init?.headers).toEqual({});
    expect(init).not.toHaveProperty("body");
  });

  it("空字符串令牌按没有令牌处理", async () => {
    await gatewayFetch("/patra-identity/auth/me", { token: "" });
    expect(call()[1]?.headers).toEqual({});
  });

  it("原样返回网关的响应，不解读状态码", async () => {
    const res = new Response("{}", { status: 503 });
    fetchMock.mockResolvedValue(res);
    await expect(gatewayFetch("/x")).resolves.toBe(res);
  });

  it("缺网关地址时报错，不发请求", async () => {
    vi.stubEnv("PATRA_GATEWAY_BASE_URL", undefined);
    await expect(gatewayFetch("/x")).rejects.toThrow("PATRA_GATEWAY_BASE_URL 未配置");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("网络错误照常抛出", async () => {
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    await expect(gatewayFetch("/x")).rejects.toThrow("fetch failed");
  });
});
