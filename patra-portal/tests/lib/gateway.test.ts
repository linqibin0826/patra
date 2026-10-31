// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { canSendToken, gatewayFetch, gatewayStream } from "@/lib/gateway";

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

describe("canSendToken", () => {
  // false 的几种，Node 的 fetch 都在发出请求之前报错：换行、回车、NUL 在构造请求头时，
  // 其他控制字符和 DEL 在发送前，0xFF 以上的字符在转成字节串时
  it.each([
    ["patra_user_abc", true],
    ["a b\tc", true],
    ["café", true],
    ["a\nb", false],
    ["a\rb", false],
    ["a\u0000b", false],
    ["a\u0001b", false],
    ["a\u007fb", false],
    ["中", false],
  ])("%j 能不能放进请求头：%s", (token, expected) => {
    expect(canSendToken(token)).toBe(expected);
  });
});

describe("gatewayStream", () => {
  it("GET 网关地址 + 路径，不缓存；有 If-None-Match 时原样带上", async () => {
    await gatewayStream("/patra-catalog/x/pdf", { ifNoneMatch: '"abc"' });
    const [url, init] = call();
    expect(url).toBe("http://gw.test:9528/patra-catalog/x/pdf");
    expect(init).toMatchObject({ method: "GET", cache: "no-store" });
    expect(new Headers(init?.headers).get("If-None-Match")).toBe('"abc"');
  });

  it("没有 If-None-Match 时不带请求头", async () => {
    await gatewayStream("/x");
    expect(new Headers(call()[1]?.headers).has("If-None-Match")).toBe(false);
  });

  it("带会话令牌与 Accept（推送的事件流）；不传时都没有", async () => {
    await gatewayStream("/patra-notification/me/notifications/stream", {
      token: "patra_user_abc",
      accept: "text/event-stream",
    });
    const headers = new Headers(call()[1]?.headers);
    expect(headers.get("Authorization")).toBe("Bearer patra_user_abc");
    expect(headers.get("Accept")).toBe("text/event-stream");
    fetchMock.mockClear();
    await gatewayStream("/x");
    const plain = new Headers(call()[1]?.headers);
    expect(plain.has("Authorization")).toBe(false);
    expect(plain.has("Accept")).toBe(false);
  });

  it("超时只计到响应头到达：之后慢慢下载大文件不会被掐断", async () => {
    vi.useFakeTimers();
    try {
      await gatewayStream("/x", { headerTimeoutMs: 1_000 });
      vi.advanceTimersByTime(60_000);
      expect(call()[1]?.signal?.aborted).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it("响应头迟迟不到时超时中止", async () => {
    vi.useFakeTimers();
    try {
      fetchMock.mockImplementation(
        (_url, init) =>
          new Promise((_resolve, reject) => {
            init?.signal?.addEventListener("abort", () => reject(init.signal?.reason));
          }),
      );
      const pending = gatewayStream("/x", { headerTimeoutMs: 1_000 });
      vi.advanceTimersByTime(1_000);
      await expect(pending).rejects.toBeDefined();
    } finally {
      vi.useRealTimers();
    }
  });

  it("调用方的 signal 中止时（浏览器断开下载）一并取消上游请求", async () => {
    const browser = new AbortController();
    await gatewayStream("/x", { signal: browser.signal });
    browser.abort();
    expect(call()[1]?.signal?.aborted).toBe(true);
  });
});

describe("gatewayFetch 的方法、请求体、重定向与转发头", () => {
  it("sends_put_and_delete", async () => {
    await gatewayFetch("/patra-identity/me/profile", { method: "PUT", json: {} });
    await gatewayFetch("/patra-identity/me/avatar", { method: "DELETE" });
    expect(call(0)[1]?.method).toBe("PUT");
    expect(call(1)[1]?.method).toBe("DELETE");
  });

  it("sends_form_data_without_content_type", async () => {
    const form = new FormData();
    form.append("file", new Blob(["x"], { type: "image/png" }), "a.png");
    await gatewayFetch("/patra-identity/me/avatar", { method: "POST", body: form });
    const [, init] = call();
    expect(init?.body).toBe(form);
    expect(init?.headers).toEqual({});
  });

  it("json 和 body 同时给时报错，不发请求", async () => {
    await expect(
      gatewayFetch("/x", { method: "POST", json: {}, body: new FormData() }),
    ).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("writes_only_two_forwarding_headers", async () => {
    await gatewayFetch("/patra-identity/auth/me", {
      token: "patra_user_abc",
      client: { ip: "10.0.0.2", userAgent: "UA/1" },
    });
    expect(call()[1]?.headers).toEqual({
      Authorization: "Bearer patra_user_abc",
      "X-Forwarded-For": "10.0.0.2",
      "User-Agent": "UA/1",
    });
  });

  it("omits_null_client_fields", async () => {
    await gatewayFetch("/patra-identity/auth/me", { client: { ip: null, userAgent: null } });
    expect(call()[1]?.headers).toEqual({});
  });

  it("does_not_follow_redirects_by_default", async () => {
    await gatewayFetch("/patra-identity/users/old-name");
    expect(call()[1]?.redirect).toBe("manual");
  });

  it("gatewayStream_forwards_client", async () => {
    await gatewayStream("/patra-identity/users/a/avatar?size=128&v=0123456789abcdef", {
      client: { ip: "::1", userAgent: "UA/3" },
    });
    expect(call()[1]?.headers).toEqual({ "X-Forwarded-For": "::1", "User-Agent": "UA/3" });
  });

  it("gatewayStream_posts_json", async () => {
    await gatewayStream("/patra-catalog/portal/publications/export", {
      method: "POST",
      json: { ids: ["1"], format: "ris" },
    });
    const init = call()[1];
    expect(init?.method).toBe("POST");
    expect(init?.headers).toEqual({ "Content-Type": "application/json" });
    expect(JSON.parse(String(init?.body))).toEqual({ ids: ["1"], format: "ris" });
  });
});

describe("gatewayFetch 的条件请求", () => {
  it("有 ifNoneMatch 时原样写成 If-None-Match", async () => {
    await gatewayFetch("/patra-interaction/me/notifications/unread-count", {
      ifNoneMatch: '"2-1"',
    });
    expect(new Headers(call()[1]?.headers).get("If-None-Match")).toBe('"2-1"');
  });

  it("没有 ifNoneMatch 时不带这个头", async () => {
    await gatewayFetch("/x", { ifNoneMatch: null });
    expect(new Headers(call()[1]?.headers).has("If-None-Match")).toBe(false);
  });
});
