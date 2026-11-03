// @vitest-environment node
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ensureTrackingIds,
  resolveTrackingIds,
  rollSession,
} from "@/lib/analytics/server/identity-cookies";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));

afterEach(() => {
  vi.unstubAllEnvs();
});

const NOW = 1_762_069_330_177;
const SID = "0193e1a3-1111-7a22-8b33-445566778899";
const DEVICE = "A".repeat(43);
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe("rollSession：30 分钟没有活动换新会话", () => {
  it.each([
    ["没有 Cookie", undefined],
    ["格式坏", "garbage"],
    ["时间戳不是数字", `${SID}.abc`],
    ["ID 不是 UUID", `not-a-uuid.${NOW}`],
    ["时间戳在未来超过 1 分钟", `${SID}.${NOW + 60_001}`],
    ["距上次 30 分 1 毫秒", `${SID}.${NOW - 1_800_001}`],
  ])("%s → 新会话", (_, raw) => {
    const { sessionId, value } = rollSession(raw, NOW);
    expect(sessionId).toMatch(UUID);
    expect(sessionId).not.toBe(SID);
    expect(value).toBe(`${sessionId}.${NOW}`);
  });

  it.each([
    ["距上次 29 分 59 秒", NOW - 1_799_999],
    ["正好 30 分钟", NOW - 1_800_000],
    ["时间戳略在未来（1 分钟内的时钟误差）", NOW + 60_000],
  ])("%s → 沿用，最后活动改成现在", (_, last) => {
    expect(rollSession(`${SID}.${last}`, NOW)).toEqual({ sessionId: SID, value: `${SID}.${NOW}` });
  });
});

describe("resolveTrackingIds", () => {
  const read = (cookies: Record<string, string>) => (name: string) => cookies[name];

  it("合法设备值沿用，两条 Cookie 都写回", () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const ids = resolveTrackingIds(read({ patra_device: DEVICE, patra_sid: `${SID}.${NOW}` }), NOW);
    expect(ids.deviceId).toBe(DEVICE);
    expect(ids.sessionId).toBe(SID);
    expect(ids.cookies.map((c) => [c.name, c.value])).toEqual([
      ["patra_device", DEVICE],
      ["patra_sid", `${SID}.${NOW}`],
    ]);
  });

  it("设备值被改坏 → 换新", () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const ids = resolveTrackingIds(read({ patra_device: "short" }), NOW);
    expect(ids.deviceId).toMatch(/^[A-Za-z0-9_-]{43}$/);
  });

  it("Secure 开着时名字带 __Host- 前缀", () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", undefined);
    const ids = resolveTrackingIds(read({ "__Host-patra_device": DEVICE }), NOW);
    expect(ids.deviceId).toBe(DEVICE);
    expect(ids.cookies.map((c) => c.name)).toEqual(["__Host-patra_device", "__Host-patra_sid"]);
  });
});

describe("ensureTrackingIds（Route Handler、Server Action 用）", () => {
  it("没有 Cookie：写出两条，设备 400 天，会话随浏览器关闭失效（没有 Max-Age）", async () => {
    vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
    const jar = resetCookieJar();
    const ids = await ensureTrackingIds();
    expect(jar.written).toHaveLength(2);
    expect(jar.written[0]).toMatch(
      new RegExp(
        `^patra_device=${ids.deviceId}; Path=/; Expires=[^;]+; Max-Age=34560000; HttpOnly; SameSite=lax$`,
      ),
    );
    expect(jar.written[1]).toMatch(
      new RegExp(`^patra_sid=${ids.sessionId}\\.\\d+; Path=/; HttpOnly; SameSite=lax$`),
    );
  });
});
