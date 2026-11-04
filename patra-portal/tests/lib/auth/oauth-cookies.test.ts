// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

import {
  clearOAuthFlowCookies,
  clearTicketCookie,
  readOAuthFlow,
  readTicket,
  writeOAuthFlowCookies,
  writeSignedInFlag,
  writeTicketCookie,
} from "@/lib/auth/session-cookie";

beforeEach(() => vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false"));
afterEach(() => vi.unstubAllEnvs());

describe("发起流程的 Cookie", () => {
  it("state 与 return：HttpOnly、Lax、只给 OAuth 路径、10 分钟", async () => {
    const jar = resetCookieJar();
    await writeOAuthFlowCookies("s1", { from: "login", next: "/papers/1" });
    expect(jar.written).toHaveLength(2);
    for (const line of jar.written) {
      expect(line).toMatch(/Path=\/api\/auth\/oauth/);
      expect(line).toMatch(/Max-Age=600/);
      expect(line).toMatch(/HttpOnly/);
      expect(line).toMatch(/SameSite=lax/i);
      expect(line).not.toMatch(/Secure/);
    }
    expect(jar.written[0]).toMatch(/^patra_oauth_state=s1;/);
    expect(jar.written[1]).toMatch(/^patra_oauth_return=/);
  });

  it("Secure 开关打开时带 Secure，名字不带 __Host- 前缀", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "true");
    const jar = resetCookieJar();
    await writeOAuthFlowCookies("s1", { from: "login", next: "/" });
    expect(jar.written[0]).toMatch(/^patra_oauth_state=s1;/);
    expect(jar.written[0]).toMatch(/Secure/);
  });

  it("读出 state、发起页和 next", async () => {
    resetCookieJar({
      patra_oauth_state: "s1",
      patra_oauth_return: "from=register&next=%2Fpapers%2F1",
    });
    await expect(readOAuthFlow()).resolves.toEqual({
      state: "s1",
      ret: { from: "register", next: "/papers/1" },
    });
  });

  it("return 坏了：发起页回到 login，next 回到 /", async () => {
    resetCookieJar({ patra_oauth_return: "from=evil&next=https%3A%2F%2Fevil.com" });
    await expect(readOAuthFlow()).resolves.toEqual({
      state: null,
      ret: { from: "login", next: "/" },
    });
  });

  it("没有 Cookie：state 为 null，发起页回到 login", async () => {
    resetCookieJar();
    await expect(readOAuthFlow()).resolves.toEqual({
      state: null,
      ret: { from: "login", next: "/" },
    });
  });

  it("清除：空值、Max-Age=0，属性与写入时相同", async () => {
    const jar = resetCookieJar();
    await clearOAuthFlowCookies();
    expect(jar.written).toHaveLength(2);
    for (const line of jar.written) {
      expect(line).toMatch(/Max-Age=0/);
      expect(line).toMatch(/Path=\/api\/auth\/oauth/);
    }
  });
});

describe("待补填凭据", () => {
  it("写入：HttpOnly、Lax、Path=/、30 分钟", async () => {
    const jar = resetCookieJar();
    await writeTicketCookie("ticket-1");
    expect(jar.written[0]).toMatch(/^patra_oauth_ticket=ticket-1;/);
    expect(jar.written[0]).toMatch(/Path=\//);
    expect(jar.written[0]).toMatch(/Max-Age=1800/);
    expect(jar.written[0]).toMatch(/HttpOnly/);
  });

  it("读取：没有或为空时是 null", async () => {
    resetCookieJar({ patra_oauth_ticket: "ticket-1" });
    await expect(readTicket()).resolves.toBe("ticket-1");
    resetCookieJar({ patra_oauth_ticket: "" });
    await expect(readTicket()).resolves.toBeNull();
  });

  it("清除：Max-Age=0", async () => {
    const jar = resetCookieJar();
    await clearTicketCookie();
    expect(jar.written[0]).toMatch(/^patra_oauth_ticket=;/);
    expect(jar.written[0]).toMatch(/Max-Age=0/);
  });
});

describe("已登录提示标记", () => {
  it("脚本要能读，不带 HttpOnly；60 秒", async () => {
    const jar = resetCookieJar();
    await writeSignedInFlag();
    expect(jar.written[0]).toMatch(/^patra_oauth_signed_in=1;/);
    expect(jar.written[0]).toMatch(/Max-Age=60/);
    expect(jar.written[0]).not.toMatch(/HttpOnly/);
  });
});
