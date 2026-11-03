// @vitest-environment node

import { readFileSync } from "node:fs";
import { join } from "node:path";
import { resetCookieJar } from "@tests/stubs/cookie-jar";
import { EXAMPLES_DIR, validateAgainst } from "@tests/support/analytics-schema";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "@/app/api/reading-signals/route";
import { lookupSessionByToken } from "@/lib/auth/current-user";

vi.mock("next/headers", () => import("@tests/stubs/cookie-jar"));
/// `after` 的回调排到响应之后；测试里记下来，手动执行
const scheduled: (() => unknown)[] = [];
vi.mock("next/server", async (importOriginal) => ({
  ...(await importOriginal<typeof import("next/server")>()),
  after: (task: () => unknown) => {
    scheduled.push(task);
  },
}));
vi.mock("@/lib/auth/current-user", () => ({ lookupSessionByToken: vi.fn() }));

const fetchMock = vi.fn<typeof fetch>();
const DEVICE = "D".repeat(43);
const ORIGIN = "http://localhost:4000";
const runScheduled = () => Promise.all(scheduled.splice(0).map((task) => task()));
const MIXED = JSON.parse(
  readFileSync(join(EXAMPLES_DIR, "valid/browser-batch--mixed.json"), "utf8"),
) as { sent_at: string; events: Record<string, unknown>[] };

let jar: ReturnType<typeof resetCookieJar>;

beforeEach(() => {
  vi.stubEnv("PATRA_GATEWAY_BASE_URL", "http://gw.test");
  vi.stubEnv("PATRA_SESSION_COOKIE_SECURE", "false");
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockResolvedValue(new Response(null, { status: 202 }));
  vi.mocked(lookupSessionByToken).mockResolvedValue({ status: "anonymous" });
  jar = resetCookieJar(
    { patra_device: DEVICE },
    { host: "localhost:4000", "x-forwarded-for": "203.0.113.7", "user-agent": "Mozilla/5.0" },
  );
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
  scheduled.length = 0;
});

function post(body: string, headers: Record<string, string> = { origin: ORIGIN }) {
  return POST(
    new Request(`${ORIGIN}/api/reading-signals`, {
      method: "POST",
      headers: { "content-type": "application/json", host: "localhost:4000", ...headers },
      body,
    }),
  );
}

const forwarded = () =>
  JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body)) as { events: Record<string, unknown>[] };

describe("POST /api/reading-signals", () => {
  it("缺 Origin（跨站）：403，不转发", async () => {
    expect((await post(JSON.stringify(MIXED), {})).status).toBe(403);
    await runScheduled();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("请求体超过 64 KiB：413", async () => {
    expect((await post("x".repeat(70_000))).status).toBe(413);
  });

  it.each([
    ["不是 JSON", "{"],
    ["没有事件", JSON.stringify({ sent_at: MIXED.sent_at, events: [] })],
    ["超过 50 条", JSON.stringify({ sent_at: MIXED.sent_at, events: Array(51).fill({}) })],
    ["sent_at 没有毫秒", JSON.stringify({ ...MIXED, sent_at: "2026-11-01T08:30:12Z" })],
    ["多余的顶层字段", JSON.stringify({ ...MIXED, user_id: "1" })],
  ])("%s：400", async (_, body) => {
    expect((await post(body)).status).toBe(400);
  });

  it("合格批次：立刻回 204、滚动会话 Cookie，响应之后每条转成采集信封发出", async () => {
    const res = await post(JSON.stringify(MIXED));
    expect(res.status).toBe(204);
    expect(jar.written.some((c) => c.startsWith("patra_sid="))).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
    await runScheduled();
    const [url] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("http://gw.test/patra-analytics/events");
    const { events } = forwarded();
    expect(events).toHaveLength(MIXED.events.length);
    for (const envelope of events) {
      expect(validateAgainst("collect-envelope", envelope)).toEqual([]);
      expect(envelope).toMatchObject({
        source: "client",
        sent_at: MIXED.sent_at,
        device_id: DEVICE,
        user_id: null,
        client_ip: "203.0.113.7",
        is_synthetic: false,
      });
    }
  });

  it("登录时 user_id 来自服务端会话", async () => {
    resetCookieJar({ patra_device: DEVICE, patra_session: "tok" }, { host: "localhost:4000" });
    vi.mocked(lookupSessionByToken).mockResolvedValue({
      status: "authenticated",
      user: { userId: "77" } as never,
    });
    await post(JSON.stringify(MIXED));
    await runScheduled();
    expect(lookupSessionByToken).toHaveBeenCalledWith("tok", expect.anything());
    expect(forwarded().events.every((e) => e.user_id === "77")).toBe(true);
  });

  it("浏览器塞进事件的身份字段不会被带过去", async () => {
    const forged = {
      ...MIXED,
      events: [{ ...MIXED.events[0], user_id: "1", device_id: "x", is_synthetic: true }],
    };
    await post(JSON.stringify(forged));
    await runScheduled();
    expect(forwarded().events[0]).toMatchObject({
      user_id: null,
      device_id: DEVICE,
      is_synthetic: false,
    });
  });

  it.each([
    ["采集端点 503", () => fetchMock.mockResolvedValue(new Response(null, { status: 503 }))],
    ["网关不可达", () => fetchMock.mockRejectedValue(new TypeError("fetch failed"))],
  ])("%s：浏览器照样拿到 204，不抛错", async (_, arrange) => {
    arrange();
    vi.spyOn(console, "warn").mockImplementation(() => {});
    expect((await post(JSON.stringify(MIXED))).status).toBe(204);
    await expect(runScheduled()).resolves.toBeDefined();
  });
});
