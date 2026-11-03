// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readActionFacts, readHandlerFacts, readPageFacts } from "@/lib/analytics/server/context";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

const DEVICE = "D".repeat(43);
const SID = "0193e1a3-1111-7a22-8b33-445566778899";

beforeEach(() => {
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
});

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("readPageFacts（页面用：ID 来自 proxy 写的请求头）", () => {
  const PAGE_HEADERS = {
    "x-patra-device-id": DEVICE,
    "x-patra-session-id": SID,
    host: "portal.test",
    "x-forwarded-for": "203.0.113.7",
    "user-agent": "Mozilla/5.0",
  };

  it("读出两个 ID、令牌与浏览器来源", async () => {
    resetCookieJar({ patra_session: "tok" }, PAGE_HEADERS);
    expect(await readPageFacts()).toEqual({
      deviceId: DEVICE,
      sessionId: SID,
      token: "tok",
      client: { ip: "203.0.113.7", userAgent: "Mozilla/5.0" },
      referrerHost: null,
      isActionRequest: false,
    });
  });

  it("缺任一个 ID 头时返回 null（不发事件）", async () => {
    resetCookieJar({}, { "x-patra-device-id": DEVICE });
    expect(await readPageFacts()).toBeNull();
  });

  it("带 Next-Action 头的是 Server Action 回渲染", async () => {
    resetCookieJar({}, { ...PAGE_HEADERS, "next-action": "abc123" });
    expect((await readPageFacts())?.isActionRequest).toBe(true);
  });

  it.each([
    ["外站来源取主机名", "https://www.google.com/search?q=x", "www.google.com"],
    ["本站来源（站内跳转）为 null", "http://portal.test/papers", null],
    ["解析不了为 null", "not a url", null],
  ])("%s", async (_, referer, host) => {
    resetCookieJar({}, { ...PAGE_HEADERS, referer });
    expect((await readPageFacts())?.referrerHost).toBe(host);
  });

  it("本站主机先看 X-Forwarded-Host", async () => {
    resetCookieJar(
      {},
      {
        ...PAGE_HEADERS,
        "x-forwarded-host": "patra.linqibin.dev",
        referer: "https://patra.linqibin.dev/",
      },
    );
    expect((await readPageFacts())?.referrerHost).toBeNull();
  });
});

describe("readHandlerFacts（Route Handler、Server Action 用：自己读写 Cookie）", () => {
  it("没有 Cookie 时生成两个 ID 并写回", async () => {
    const jar = resetCookieJar({}, { host: "portal.test" });
    const facts = await readHandlerFacts();
    expect(facts.deviceId).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(jar.written).toHaveLength(2);
  });

  it("沿用已有的设备与会话", async () => {
    resetCookieJar(
      { patra_device: DEVICE, patra_sid: `${SID}.${Date.now()}` },
      { host: "portal.test" },
    );
    expect(await readHandlerFacts()).toMatchObject({ deviceId: DEVICE, sessionId: SID });
  });
});

describe("readActionFacts（Server Action：ID 来自 proxy 的请求头，不写 Cookie）", () => {
  it("带 Next-Action 也不算回渲染", async () => {
    const jar = resetCookieJar(
      {},
      { "x-patra-device-id": DEVICE, "x-patra-session-id": SID, "next-action": "7f3a" },
    );
    expect(await readActionFacts()).toMatchObject({
      deviceId: DEVICE,
      sessionId: SID,
      isActionRequest: false,
    });
    expect(jar.written).toEqual([]);
  });

  it("没有 proxy 的请求头时返回 null", async () => {
    resetCookieJar({}, { "next-action": "7f3a" });
    expect(await readActionFacts()).toBeNull();
  });
});
