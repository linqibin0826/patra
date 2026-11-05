// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "@/app/api/auth/oauth/[provider]/start/route";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const PLATFORM = "https://github.com/login/oauth/authorize?client_id=c&state=s-1&scope=read%3Auser";
const PUBLIC_HOST = { host: "patra.linqibin.dev" };

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

function start(provider: string, query = "") {
  return GET(new Request(`http://portal.test/api/auth/oauth/${provider}/start${query}`), {
    params: Promise.resolve({ provider }),
  });
}

function platformRedirect(location = PLATFORM) {
  return new Response(null, { status: 302, headers: { Location: location } });
}

describe("GET /api/auth/oauth/[provider]/start", () => {
  it("不认识的平台：404，不请求网关", async () => {
    resetCookieJar({}, PUBLIC_HOST);
    const res = await start("wechat");
    expect(res.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("成功：把平台地址原样跳过去，写 state 与发起页 Cookie；不缓存", async () => {
    const jar = resetCookieJar({}, { ...PUBLIC_HOST, "x-forwarded-for": "203.0.113.7" });
    fetchMock.mockResolvedValue(platformRedirect());
    const res = await start("github", "?from=register&next=%2Fpapers%2F1");
    expect(res.status).toBe(302);
    expect(res.headers.get("location")).toBe(PLATFORM);
    expect(res.headers.get("cache-control")).toBe("private, no-store");
    expect(jar.written[0]).toMatch(/^patra_oauth_state=s-1;/);
    expect(jar.written[1]).toContain("from%3Dregister%26next%3D%252Fpapers%252F1");
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-identity/oauth2/authorization/github");
    expect(init?.redirect).toBe("manual");
    expect(init?.headers).toMatchObject({ "X-Forwarded-For": "203.0.113.7" });
  });

  it("from 只认 login、register，next 重新校验", async () => {
    const jar = resetCookieJar({}, PUBLIC_HOST);
    fetchMock.mockResolvedValue(platformRedirect());
    await start("github", "?from=settings&next=https%3A%2F%2Fevil.com");
    expect(jar.written[1]).toContain("from%3Dlogin%26next%3D%252F");
  });

  it("tailnet 地址：回发起页，不请求网关，不写 Cookie", async () => {
    const jar = resetCookieJar({}, { host: "100.103.73.27:4000" });
    const res = await start("github", "?from=register&next=%2Fpapers%2F1");
    expect(res.status).toBe(302);
    expect(res.headers.get("location")).toBe("/register?next=%2Fpapers%2F1");
    expect(fetchMock).not.toHaveBeenCalled();
    expect(jar.written).toEqual([]);
  });

  it.each([
    ["这家平台没配置（404）", () => new Response(null, { status: 404 })],
    ["不是 302", () => new Response(null, { status: 500 })],
    ["200", () => new Response(null, { status: 200 })],
    ["没有 Location", () => new Response(null, { status: 302 })],
    ["Location 里没有 state", () => platformRedirect("https://github.com/login/oauth/authorize")],
    ["Location 不是地址", () => platformRedirect("::not a url::")],
  ])("%s：回发起页报服务错误，不写 Cookie", async (_name, reply) => {
    const jar = resetCookieJar({}, PUBLIC_HOST);
    fetchMock.mockResolvedValue(reply());
    const res = await start("orcid");
    expect(res.headers.get("location")).toBe("/login?oauth=unavailable&provider=orcid");
    expect(jar.written).toEqual([]);
  });

  it("网关不可达：回发起页报服务错误", async () => {
    resetCookieJar({}, PUBLIC_HOST);
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    const res = await start("google", "?from=register");
    expect(res.headers.get("location")).toBe("/register?oauth=unavailable&provider=google");
  });
});
