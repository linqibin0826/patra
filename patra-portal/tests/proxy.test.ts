// @vitest-environment node
import { ME_RESPONSE } from "@tests/fixtures/user";
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { config, proxy } from "@/proxy";

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

/** 默认是浏览器地址栏直接打开（文档请求）。Next 交给中间层之前会剥掉 `rsc` 等内部请求头，
 *  所以区分靠浏览器自带的 `Sec-Fetch-Dest`：地址栏打开是 document，页面里发起的 fetch 是 empty */
function request(path: string, headers: Record<string, string> = { "sec-fetch-dest": "document" }) {
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

  it("爬虫、curl 不带 Sec-Fetch-Dest，按直接打开处理，照样先确认存在", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 404 }));
    const res = await proxy(request("/papers/42", {}));
    expect(rewrittenTo(res)).toBe("http://portal.test/missing/paper");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("站内跳转 / 预取（页面里发起的 fetch）不问后端，照常流式出骨架", async () => {
    const res = await proxy(request("/papers/not-a-real-id", { "sec-fetch-dest": "empty" }));
    expect(res.headers.get("x-middleware-next")).toBe("1");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("matcher 按解码后的路径放进来的编码地址", () => {
  // pathname 保留编码，这里认不出是详情页还是登录页、注册页：放行，不能抛错变成 500
  it.each(["/%6Cogin", "/%72egister", "/login%2F", "/%70apers/42"])(
    "GET %s 放行，不请求网关",
    async (path) => {
      const res = await proxy(request(path));
      expect(res.headers.get("x-middleware-next")).toBe("1");
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );
});

describe("已登录的人打开登录页、注册页时直接跳走", () => {
  const TOKEN = "patra_user_abc";

  beforeEach(() => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  /// 带着会话 Cookie 的请求，默认是地址栏直接打开
  const signedIn = (path: string, headers: Record<string, string> = {}) =>
    request(path, { "sec-fetch-dest": "document", cookie: `patra_session=${TOKEN}`, ...headers });
  const json = (status: number, body: unknown) =>
    new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  const problem = (status: number, code: string) =>
    new Response(JSON.stringify({ status, code, title: "x" }), {
      status,
      headers: { "Content-Type": "application/problem+json" },
    });

  it("matcher 包含登录页和注册页，详情页的两项不变", () => {
    expect(config.matcher).toEqual(["/papers/:id", "/journals/:id", "/login", "/register"]);
  });

  it.each(["/login", "/register"])("已登录时 GET %s 回 307，跳到 next", async (page) => {
    fetchMock.mockResolvedValue(json(200, ME_RESPONSE));
    const res = await proxy(signedIn(`${page}?next=%2Fpapers%3Fq%3DGLP-1`));
    expect(res.status).toBe(307);
    expect(res.headers.get("location")).toBe("http://portal.test/papers?q=GLP-1");
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/auth/me");
    expect(init?.headers).toEqual({ Authorization: `Bearer ${TOKEN}` });
  });

  it.each([
    ["没有 next", "/login"],
    ["next 是站外地址", "/login?next=%2F%2Fevil.com"],
    ["next 是登录页自己", "/register?next=%2Flogin"],
  ])("%s，跳到首页", async (_label, path) => {
    fetchMock.mockResolvedValue(json(200, ME_RESPONSE));
    const res = await proxy(signedIn(path));
    expect(res.headers.get("location")).toBe("http://portal.test/");
  });

  it("问 /auth/me 时转发浏览器的 IP 与 UA", async () => {
    fetchMock.mockResolvedValue(json(200, ME_RESPONSE));
    await proxy(signedIn("/login", { "x-forwarded-for": "10.0.0.9", "user-agent": "UA/9" }));
    expect(fetchMock.mock.calls[0]?.[1]?.headers).toEqual({
      Authorization: `Bearer ${TOKEN}`,
      "X-Forwarded-For": "10.0.0.9",
      "User-Agent": "UA/9",
    });
  });

  it("next 出现多次时取第一个", async () => {
    fetchMock.mockResolvedValue(json(200, ME_RESPONSE));
    const res = await proxy(signedIn("/login?next=%2Fjournals&next=%2Fpapers"));
    expect(res.headers.get("location")).toBe("http://portal.test/journals");
  });

  it("站内点链接、预取（sec-fetch-dest: empty）同样会跳走", async () => {
    fetchMock.mockResolvedValue(json(200, ME_RESPONSE));
    const res = await proxy(signedIn("/login?next=%2Fjournals", { "sec-fetch-dest": "empty" }));
    expect(res.headers.get("location")).toBe("http://portal.test/journals");
  });

  it("没有 Cookie 时放行，不请求网关", async () => {
    const res = await proxy(request("/login?next=%2Fjournals"));
    expect(res.headers.get("x-middleware-next")).toBe("1");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["令牌失效", () => problem(401, "GW-0401")],
    ["网关出错", () => problem(503, "GW-0503")],
  ])("%s时放行，显示表单", async (_label, response) => {
    fetchMock.mockResolvedValue(response());
    const res = await proxy(signedIn("/login"));
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("网关超时放行；问网关的超时是 3 秒", async () => {
    const timeout = vi.spyOn(AbortSignal, "timeout");
    fetchMock.mockRejectedValue(new DOMException("timed out", "TimeoutError"));
    const res = await proxy(signedIn("/register"));
    expect(res.headers.get("x-middleware-next")).toBe("1");
    expect(timeout).toHaveBeenCalledWith(3_000);
  });

  it("POST（Server Action 发到当前页）直接放行，不请求网关", async () => {
    const req = new NextRequest("http://portal.test/login", {
      method: "POST",
      headers: { cookie: `patra_session=${TOKEN}` },
    });
    const res = await proxy(req);
    expect(res.headers.get("x-middleware-next")).toBe("1");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("Secure 开关写错、读不了 Cookie 时放行，不请求网关", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "yes");
    const res = await proxy(signedIn("/login"));
    expect(res.headers.get("x-middleware-next")).toBe("1");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
