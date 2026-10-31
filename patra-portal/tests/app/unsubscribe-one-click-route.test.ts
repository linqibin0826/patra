// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET, POST } from "@/app/unsubscribe/one-click/route";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const fetchMock = vi.fn<typeof fetch>();
const TOKEN = "Ab3_-".repeat(8).concat("xyz");
const ORIGIN = "http://localhost:4000";
const PATH = `/unsubscribe/one-click?token=${TOKEN}&category=REPLY`;

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  vi.spyOn(console, "error").mockImplementation(() => {});
  // 即使这台浏览器登录着，转发也不能带会话令牌
  resetCookieJar({ patra_session: "patra_user_abc" });
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

/// 邮箱服务商发来的一键退订（RFC 8058）：表单体、没有 Origin、没有 Cookie
const oneClick = (path = PATH) =>
  new NextRequest(`${ORIGIN}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "List-Unsubscribe=One-Click",
  });

const gatewayCall = () => fetchMock.mock.calls[0] ?? [];

/// `console.error` 收到的全部参数，拼成一个字符串
const errorLog = () =>
  vi
    .mocked(console.error)
    .mock.calls.flat()
    .map((part) => (typeof part === "string" ? part : JSON.stringify(part)))
    .join(" ");

describe("POST /unsubscribe/one-click", () => {
  it("原样转发到网关：POST、同样的令牌与类别、不带会话令牌、不跟随重定向", async () => {
    fetchMock.mockResolvedValue(Response.json({ turnedOff: ["REPLY"] }));
    await POST(oneClick());
    const [url, init] = gatewayCall();
    expect(url).toBe(
      `http://gw.test/patra-notification/unsubscribe/one-click?token=${TOKEN}&category=REPLY`,
    );
    expect(init?.method).toBe("POST");
    expect(init?.headers).not.toHaveProperty("Authorization");
    expect(init?.redirect).toBe("manual");
  });

  it("把网关的状态码交回去，空响应体，不缓存", async () => {
    fetchMock.mockResolvedValue(Response.json({ turnedOff: ["REPLY"] }));
    const ok = await POST(oneClick());
    expect(ok.status).toBe(200);
    expect(await ok.text()).toBe("");
    expect(ok.headers.get("cache-control")).toBe("no-store");
    expect(ok.headers.get("location")).toBeNull();

    fetchMock.mockResolvedValue(
      Response.json({ traits: ["UNSUBSCRIBE_LINK_INVALID"] }, { status: 404 }),
    );
    expect((await POST(oneClick())).status).toBe(404);
  });

  it("只转第一个 token 与 category，别的参数不转", async () => {
    fetchMock.mockResolvedValue(Response.json({}));
    await POST(oneClick("/unsubscribe/one-click?token=a&token=b&category=REPLY&next=%2Fx"));
    expect(gatewayCall()[0]).toBe(
      "http://gw.test/patra-notification/unsubscribe/one-click?token=a&category=REPLY",
    );
  });

  it("网关连不上：502，日志里没有令牌", async () => {
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    const res = await POST(oneClick());
    expect(res.status).toBe(502);
    expect(errorLog()).not.toContain(TOKEN);
  });

  it("网关地址配错（报错信息带着完整地址）：502，日志里没有令牌", async () => {
    fetchMock.mockImplementation(async (url) => {
      throw new TypeError(`Failed to parse URL from ${String(url)}`);
    });
    const res = await POST(oneClick());
    expect(res.status).toBe(502);
    expect(errorLog()).not.toContain(TOKEN);
  });
});

describe("GET /unsubscribe/one-click", () => {
  it("303 到确认页，令牌与类别放进片段；不请求网关", () => {
    const res = GET(new NextRequest(`${ORIGIN}${PATH}`));
    expect(res.status).toBe(303);
    expect(res.headers.get("location")).toBe(`/unsubscribe#token=${TOKEN}&category=REPLY`);
    expect(res.headers.get("referrer-policy")).toBe("no-referrer");
    expect(res.headers.get("cache-control")).toBe("no-store");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("没有参数：跳到不带片段的确认页（落地页出无效屏）", () => {
    const res = GET(new NextRequest(`${ORIGIN}/unsubscribe/one-click`));
    expect(res.headers.get("location")).toBe("/unsubscribe");
  });
});
