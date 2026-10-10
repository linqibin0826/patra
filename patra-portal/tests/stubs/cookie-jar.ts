import { NextResponse } from "next/server";

type SetCookieArgs = Parameters<NextResponse["cookies"]["set"]>;

/// 测试用的 Cookie 存储，替换 `next/headers` 的 `cookies()`：
/// `vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"))`。
/// 读到的是 `resetCookieJar` 传入的请求 Cookie；每次写入都交给 Next 自己的 ResponseCookies
/// 序列化成一条 Set-Cookie 原文记下来，测试直接断言原文。
export interface CookieJar {
  get(name: string): { name: string; value: string } | undefined;
  set(...args: SetCookieArgs): void;
  /// 按写入顺序记下的 Set-Cookie 原文
  readonly written: string[];
}

function createCookieJar(incoming: Record<string, string>): CookieJar {
  const written: string[] = [];
  return {
    get: (name) => {
      const value = incoming[name];
      return value === undefined ? undefined : { name, value };
    },
    set: (...args) => {
      const response = new NextResponse(null);
      response.cookies.set(...args);
      written.push(...response.headers.getSetCookie());
    },
    written,
  };
}

let current = createCookieJar({});

/// 换一个新的 Cookie 存储，`incoming` 是这次请求带来的 Cookie。每个测试开头调用。
export function resetCookieJar(incoming: Record<string, string> = {}): CookieJar {
  current = createCookieJar(incoming);
  return current;
}

/// 替换 `next/headers` 的 `cookies()`。
export async function cookies(): Promise<CookieJar> {
  return current;
}
