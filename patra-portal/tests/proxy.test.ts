// @vitest-environment node
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { proxy } from "@/proxy";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  fetchMock.mockReset();
});

/** 浏览器地址栏直接打开（文档请求）；`rsc` 为站内跳转 / 预取带的请求头 */
function request(path: string, headers: Record<string, string> = {}) {
  return new NextRequest(`http://portal.test${path}`, { headers });
}

const rewrittenTo = (res: Response) => res.headers.get("x-middleware-rewrite");

describe("详情页直接打开时先确认存在", () => {
  it("非正整数 id 不问后端，直接改写到 404 页", async () => {
    const res = await proxy(request("/papers/not-a-real-id"));
    expect(rewrittenTo(res)).toBe("http://portal.test/missing/paper");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([404, 400, 422])("后端回 %i → 用 HEAD 问一次后改写到 404 页", async (status) => {
    fetchMock.mockResolvedValue(new Response(null, { status }));
    const res = await proxy(request("/papers/42"));
    expect(rewrittenTo(res)).toBe("http://portal.test/missing/paper");
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-catalog/portal/publications/42");
    expect(init?.method).toBe("HEAD");
  });

  it("期刊详情问 venues 端点，不存在时改写到期刊的 404 页", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 404 }));
    const res = await proxy(request("/journals/7"));
    expect(rewrittenTo(res)).toBe("http://portal.test/missing/journal");
    expect(fetchMock.mock.calls[0]?.[0]).toBe("http://gw.test/patra-catalog/portal/venues/7");
  });

  it.each([200, 500])("后端回 %i → 放行，由页面照常渲染或报错", async (status) => {
    fetchMock.mockResolvedValue(new Response(null, { status }));
    const res = await proxy(request("/papers/42"));
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("网络错误或超时不拦，交给页面按原路处理（报错页）", async () => {
    fetchMock.mockRejectedValue(new DOMException("timed out", "TimeoutError"));
    const res = await proxy(request("/papers/42"));
    expect(res.headers.get("x-middleware-next")).toBe("1");
    expect(fetchMock.mock.calls[0]?.[1]?.signal).toBeInstanceOf(AbortSignal);
  });

  it("站内跳转 / 预取（带 rsc 头）不问后端，照常流式出骨架", async () => {
    const res = await proxy(request("/papers/not-a-real-id", { rsc: "1" }));
    expect(res.headers.get("x-middleware-next")).toBe("1");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
