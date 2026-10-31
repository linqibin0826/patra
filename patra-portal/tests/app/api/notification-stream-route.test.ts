// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "@/app/api/me/notifications/stream/route";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const ORIGIN = "http://localhost:4000";
const EVENT =
  'retry: 3000\nevent: unread-count\ndata: {"total":3,"replies":2,"mentions":1,"helpful":0}\n\n';

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

const open = (controller = new AbortController()) =>
  GET(new Request(`${ORIGIN}/api/me/notifications/stream`, { signal: controller.signal }));

/// 一段事件流响应体
const streamOf = (text: string) =>
  new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(text));
      controller.close();
    },
  });

describe("GET /api/me/notifications/stream", () => {
  it("没有会话：401，不请求网关", async () => {
    resetCookieJar();
    const res = await open();
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("带会话令牌连网关的事件流", async () => {
    fetchMock.mockResolvedValue(new Response(streamOf(EVENT), { status: 200 }));
    await open();
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-notification/me/notifications/stream");
    const headers = new Headers(init?.headers);
    expect(headers.get("Authorization")).toBe("Bearer patra_user_abc");
    expect(headers.get("Accept")).toBe("text/event-stream");
  });

  it("上游 200：事件流原样透传，不缓存、不压缩", async () => {
    fetchMock.mockResolvedValue(
      new Response(streamOf(EVENT), {
        status: 200,
        headers: { "Content-Type": "text/event-stream" },
      }),
    );
    const res = await open();
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toBe("text/event-stream");
    expect(res.headers.get("cache-control")).toBe("no-cache, no-transform");
    expect(res.headers.get("x-accel-buffering")).toBe("no");
    expect(await res.text()).toBe(EVENT);
  });

  it("浏览器断开：中止上游请求", async () => {
    fetchMock.mockResolvedValue(new Response(streamOf(EVENT), { status: 200 }));
    const controller = new AbortController();
    await open(controller);
    const upstream = fetchMock.mock.calls[0]?.[1]?.signal;
    expect(upstream?.aborted).toBe(false);
    controller.abort();
    expect(upstream?.aborted).toBe(true);
  });

  it.each([401, 503, 504])(
    "上游 %i：原样回状态码、空体（浏览器据此不再自动重连）",
    async (status) => {
      fetchMock.mockResolvedValue(Response.json({ code: "GW" }, { status }));
      const res = await open();
      expect(res.status).toBe(status);
      expect(await res.text()).toBe("");
    },
  );

  it("网关连不上：502 并记日志；浏览器自己断开引起的中止不记", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));
    expect((await open()).status).toBe(502);
    expect(console.error).toHaveBeenCalledTimes(1);
    fetchMock.mockRejectedValueOnce(new DOMException("aborted", "AbortError"));
    await open();
    expect(console.error).toHaveBeenCalledTimes(1);
  });
});
