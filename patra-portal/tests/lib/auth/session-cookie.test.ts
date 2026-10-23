// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  clearSessionCookie,
  DEVICE_ID_PATTERN,
  ensureDeviceId,
  readRequestSessionToken,
  readSessionToken,
  refreshSessionCookie,
  writeSessionCookie,
} from "@/lib/auth/session-cookie";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

afterEach(() => {
  vi.unstubAllEnvs();
});

const TOKEN = "patra_user_abc";
/// 写入或刷新时的 Set-Cookie。Expires 由 Next 按 Max-Age 算出，这里只要求它存在
const WRITTEN_PLAIN =
  /^patra_session=patra_user_abc; Path=\/; Expires=[^;]+; Max-Age=2592000; HttpOnly; SameSite=lax$/;
const WRITTEN_SECURE =
  /^__Host-patra_session=patra_user_abc; Path=\/; Expires=[^;]+; Max-Age=2592000; Secure; HttpOnly; SameSite=lax$/;

describe("Cookie 的名称和属性", () => {
  it("开关不设时开启 Secure，名字带 __Host- 前缀", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", undefined);
    const jar = resetCookieJar();
    await writeSessionCookie(TOKEN);
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN_SECURE)]);
  });

  it("开关为 true 时同样开启 Secure", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "true");
    const jar = resetCookieJar();
    await writeSessionCookie(TOKEN);
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN_SECURE)]);
  });

  it("开关为 false 时不带 Secure，名字不带前缀", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const jar = resetCookieJar();
    await writeSessionCookie(TOKEN);
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN_PLAIN)]);
  });

  it.each(["", "TRUE", "yes", "0"])("开关为 %j 时报错", async (value) => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", value);
    resetCookieJar();
    await expect(writeSessionCookie(TOKEN)).rejects.toThrow(
      "PATRA_SESSION_COOKIE_SECURE 只能是 true 或 false",
    );
  });
});

describe("readSessionToken", () => {
  it("Secure 关时读 patra_session", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    resetCookieJar({ patra_session: TOKEN, "__Host-patra_session": "other" });
    await expect(readSessionToken()).resolves.toBe(TOKEN);
  });

  it("Secure 开时读 __Host-patra_session", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", undefined);
    resetCookieJar({ patra_session: "other", "__Host-patra_session": TOKEN });
    await expect(readSessionToken()).resolves.toBe(TOKEN);
  });

  it("Secure 开时不读不带前缀的旧 Cookie：切到 HTTPS 之后要重新登录", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", undefined);
    resetCookieJar({ patra_session: TOKEN });
    await expect(readSessionToken()).resolves.toBeNull();
  });

  it("没有 Cookie 或值为空时返回 null", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    resetCookieJar();
    await expect(readSessionToken()).resolves.toBeNull();
    resetCookieJar({ patra_session: "" });
    await expect(readSessionToken()).resolves.toBeNull();
  });
});

describe("readRequestSessionToken", () => {
  /// proxy 收到的请求，`cookie` 是请求头 Cookie 的原文
  const request = (cookie: string) =>
    new NextRequest("http://portal.test/login", { headers: { cookie } });

  it("Secure 关时读 patra_session", () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const req = request(`patra_session=${TOKEN}; __Host-patra_session=other`);
    expect(readRequestSessionToken(req)).toBe(TOKEN);
  });

  it("Secure 开时读 __Host-patra_session", () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", undefined);
    const req = request(`patra_session=other; __Host-patra_session=${TOKEN}`);
    expect(readRequestSessionToken(req)).toBe(TOKEN);
  });

  it("没有 Cookie 或值为空时返回 null", () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    expect(readRequestSessionToken(request(""))).toBeNull();
    expect(readRequestSessionToken(request("patra_session="))).toBeNull();
  });

  it("开关写错时报错", () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "yes");
    expect(() => readRequestSessionToken(request(`patra_session=${TOKEN}`))).toThrow(
      "PATRA_SESSION_COOKIE_SECURE 只能是 true 或 false",
    );
  });
});

describe("refreshSessionCookie", () => {
  it("用原值重写一次，有效期回到 30 天", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const jar = resetCookieJar({ patra_session: TOKEN });
    await refreshSessionCookie();
    expect(jar.written).toEqual([expect.stringMatching(WRITTEN_PLAIN)]);
  });

  it("没有 Cookie 时什么也不做", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const jar = resetCookieJar();
    await refreshSessionCookie();
    expect(jar.written).toEqual([]);
  });
});

describe("clearSessionCookie", () => {
  it("写入空值，属性和写入时相同，Max-Age=0", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const jar = resetCookieJar({ patra_session: TOKEN });
    await clearSessionCookie();
    expect(jar.written).toEqual(["patra_session=; Path=/; Max-Age=0; HttpOnly; SameSite=lax"]);
  });

  it("Secure 开时清除也带 Secure，浏览器才会接受", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", undefined);
    const jar = resetCookieJar({ "__Host-patra_session": TOKEN });
    await clearSessionCookie();
    expect(jar.written).toEqual([
      "__Host-patra_session=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=lax",
    ]);
  });
});

describe("设备标识 Cookie", () => {
  const VALID = "A".repeat(42) + "_";
  const PLAIN =
    /^patra_device=([A-Za-z0-9_-]{43}); Path=\/; Expires=[^;]+; Max-Age=34560000; HttpOnly; SameSite=lax$/;

  it("issues_a_device_id_when_missing", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const jar = resetCookieJar();
    const id = await ensureDeviceId();
    expect(id).toMatch(DEVICE_ID_PATTERN);
    expect(jar.written).toEqual([expect.stringMatching(PLAIN)]);
    expect(jar.written[0]).toContain(`patra_device=${id};`);
  });

  it("uses_host_prefix_when_secure", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", undefined);
    const jar = resetCookieJar();
    await ensureDeviceId();
    expect(jar.written[0]).toMatch(
      /^__Host-patra_device=[A-Za-z0-9_-]{43}; .*Secure; HttpOnly; SameSite=lax$/,
    );
  });

  it("keeps_a_valid_device_id_and_extends_it", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const jar = resetCookieJar({ patra_device: VALID });
    await expect(ensureDeviceId()).resolves.toBe(VALID);
    expect(jar.written).toEqual([expect.stringMatching(PLAIN)]);
    expect(jar.written[0]).toContain(`patra_device=${VALID};`);
  });

  it.each(["abc", `${"A".repeat(42)}.`, "A".repeat(44)])(
    "replaces_a_malformed_device_cookie：%s",
    async (bad) => {
      vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
      resetCookieJar({ patra_device: bad });
      const id = await ensureDeviceId();
      expect(id).toMatch(DEVICE_ID_PATTERN);
      expect(id).not.toBe(bad);
    },
  );

  it("每次新生成的值都不同", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    resetCookieJar();
    const first = await ensureDeviceId();
    resetCookieJar();
    expect(await ensureDeviceId()).not.toBe(first);
  });

  it("clearSessionCookie_does_not_touch_device_cookie", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const jar = resetCookieJar({ patra_session: TOKEN, patra_device: VALID });
    await clearSessionCookie();
    expect(jar.written.every((line) => line.startsWith("patra_session="))).toBe(true);
  });
});
