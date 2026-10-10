import { findAll, format } from "@tests/design-system/source-scan";
import { describe, expect, it } from "vitest";

const GATEWAY_CLIENT = "src/lib/gateway.ts";
const SESSION_COOKIE = "src/lib/auth/session-cookie.ts";

/// 读网关地址
const GATEWAY_ADDRESS = /PATRA_GATEWAY_BASE_URL/;
/// 直接 fetch。第一个参数是以 / 开头的字符串或模板字面量时除外：那是浏览器请求门户自己的接口
const DIRECT_FETCH = /\bfetch\((?!\s*["'`]\/)/;
/// 调用 next/headers 的 cookies()
const COOKIES_CALL = /\bcookies\(\)/;
/// 手写 Authorization 头或 Bearer 前缀
const AUTHORIZATION_HEADER = /["'`](?:Authorization["'`]|Bearer\s)/;

/// src 下除 allowed 以外命中 pattern 的位置，格式是 `文件:行 片段`
const hitsOutside = (allowed: string, pattern: RegExp): string[] =>
  format(findAll(pattern).filter((hit) => hit.file !== allowed));

describe("网关调用和会话 Cookie 的边界", () => {
  it("网关地址只在统一网关客户端里读取", () => {
    expect(hitsOutside(GATEWAY_CLIENT, GATEWAY_ADDRESS)).toEqual([]);
  });

  it("只有统一网关客户端直接 fetch，浏览器请求门户自己的接口除外", () => {
    expect(hitsOutside(GATEWAY_CLIENT, DIRECT_FETCH)).toEqual([]);
  });

  it("只有会话 Cookie 模块调用 cookies()", () => {
    expect(hitsOutside(SESSION_COOKIE, COOKIES_CALL)).toEqual([]);
  });

  it("只有统一网关客户端写 Authorization 头", () => {
    expect(hitsOutside(GATEWAY_CLIENT, AUTHORIZATION_HEADER)).toEqual([]);
  });
});

describe("守护规则本身", () => {
  it.each([
    [DIRECT_FETCH, "const res = await fetch(url, {"],
    [DIRECT_FETCH, "const res = await fetch(`http://gw.test/patra-catalog/portal/venues`);"],
    [DIRECT_FETCH, "const status = await fetch("],
    [COOKIES_CALL, "const store = await cookies();"],
    [AUTHORIZATION_HEADER, 'const headers = { "Authorization": token };'],
    [AUTHORIZATION_HEADER, "const headers = { Authorization: `Bearer abc` };"],
  ])("%s 能抓到：%s", (pattern, line) => {
    expect(pattern.test(line)).toBe(true);
  });

  it.each([
    [DIRECT_FETCH, 'const res = await fetch("/api/auth/me");'],
    [DIRECT_FETCH, "const res = await fetch(`/api/venues/suggest?q=lan`);"],
    [DIRECT_FETCH, "return gatewayFetch(path);"],
    [DIRECT_FETCH, "router.prefetch(href);"],
    [COOKIES_CALL, "const value = request.cookies.get(name);"],
  ])("%s 放过：%s", (pattern, line) => {
    expect(pattern.test(line)).toBe(false);
  });
});
