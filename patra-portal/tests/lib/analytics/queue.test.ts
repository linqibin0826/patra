import { validateAgainst } from "@tests/support/analytics-schema";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createQueue, splitBatches } from "@/lib/analytics/client/queue";
import type { ClientEvent } from "@/lib/analytics/types";

/// 手动推进的计时器
function manualTimers() {
  let pending: { fn: () => void; at: number } | null = null;
  let clock = Date.parse("2026-11-02T08:30:00.000Z");
  return {
    now: () => clock,
    setTimer: (fn: () => void, ms: number) => {
      pending = { fn, at: clock + ms };
      return pending;
    },
    clearTimer: (handle: unknown) => {
      if (handle === pending) pending = null;
    },
    advance(ms: number) {
      clock += ms;
      if (pending && pending.at <= clock) {
        const { fn } = pending;
        pending = null;
        fn();
      }
    },
  };
}

const fetchMock = vi.fn<typeof fetch>();
let timers: ReturnType<typeof manualTimers>;
let path: string;
let beacon: ReturnType<typeof vi.fn<(url: string, data: Blob) => boolean>> | null;

function makeQueue() {
  return createQueue({
    now: timers.now,
    setTimer: timers.setTimer,
    clearTimer: timers.clearTimer,
    fetch: fetchMock,
    sendBeacon: beacon,
    location: () => path,
  });
}

const sentBodies = () =>
  fetchMock.mock.calls.map(
    ([, init]) => JSON.parse(String(init?.body)) as { sent_at: string; events: ClientEvent[] },
  );

beforeEach(() => {
  timers = manualTimers();
  path = "/papers/42";
  beacon = vi.fn<(url: string, data: Blob) => boolean>(() => true);
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
});

afterEach(() => {
  fetchMock.mockReset();
  vi.restoreAllMocks();
});

describe("攒批上报", () => {
  it("第一条入队后满 5 秒发一批，经第一方路径", () => {
    const queue = makeQueue();
    queue.track("fulltext_read", { article_id: "42", visible_seconds: 10 });
    timers.advance(4_999);
    expect(fetchMock).not.toHaveBeenCalled();
    timers.advance(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe("/api/reading-signals");
    expect(init).toMatchObject({ method: "POST", keepalive: true });
  });

  it("批次与每条事件都符合契约", () => {
    const queue = makeQueue();
    queue.track("page_viewed", {});
    queue.track("article_impressed", {
      article_id: "42",
      list_type: "search",
      list_id: "0193e1a3-2222-7a22-8b33-445566778899",
      position: 21,
    });
    timers.advance(5_000);
    const [body] = sentBodies();
    expect(validateAgainst("browser-batch", body)).toEqual([]);
    for (const event of body?.events ?? []) {
      expect(validateAgainst("browser-event", event)).toEqual([]);
    }
    expect(body?.events[0]).toMatchObject({
      event_name: "page_viewed",
      schema_version: "1-0-0",
      page_path: "/papers/42",
      page_type: "article",
      created_at: "2026-11-02T08:30:00.000Z",
    });
  });

  it("攒满 20 条立刻发", () => {
    const queue = makeQueue();
    for (let i = 0; i < 20; i++) queue.track("page_viewed", {});
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(sentBodies()[0]?.events).toHaveLength(20);
  });

  it("页面与路径是入队时的，不是发出时的", () => {
    const queue = makeQueue();
    queue.track("page_viewed", {});
    path = "/";
    timers.advance(5_000);
    expect(sentBodies()[0]?.events[0]).toMatchObject({
      page_path: "/papers/42",
      page_type: "article",
    });
  });

  it("路径超过 512 个字符截断", () => {
    path = `/${"a".repeat(600)}`;
    const queue = makeQueue();
    queue.track("page_viewed", {});
    timers.advance(5_000);
    expect(sentBodies()[0]?.events[0]?.page_path).toHaveLength(512);
  });
});

describe("页面隐藏或离开时补发", () => {
  it("用 sendBeacon 把剩下的发出，计时器作废", async () => {
    const queue = makeQueue();
    queue.track("page_viewed", {});
    queue.flushWithBeacon();
    expect(beacon).toHaveBeenCalledTimes(1);
    const [url, blob] = beacon?.mock.calls[0] ?? [];
    expect(url).toBe("/api/reading-signals");
    expect(blob?.type).toBe("application/json");
    expect(JSON.parse((await blob?.text()) ?? "").events).toHaveLength(1);
    timers.advance(5_000);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("没有 sendBeacon 时退回 fetch keepalive", () => {
    beacon = null;
    const queue = makeQueue();
    queue.track("page_viewed", {});
    queue.flushWithBeacon();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("缓冲为空时什么也不发", () => {
    makeQueue().flushWithBeacon();
    expect(beacon).not.toHaveBeenCalled();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("失败不影响页面", () => {
  it.each([
    ["网络错误", () => fetchMock.mockRejectedValue(new TypeError("Failed to fetch"))],
    ["门户回 500", () => fetchMock.mockResolvedValue(new Response(null, { status: 500 }))],
  ])("%s：不抛错、不打错误日志", async (_, arrange) => {
    arrange();
    const error = vi.spyOn(console, "error").mockImplementation(() => {});
    const queue = makeQueue();
    queue.track("page_viewed", {});
    expect(() => timers.advance(5_000)).not.toThrow();
    await Promise.resolve();
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(error).not.toHaveBeenCalled();
  });

  it("sendBeacon 返回 false：丢弃，不抛错", () => {
    beacon = vi.fn(() => false);
    const queue = makeQueue();
    queue.track("page_viewed", {});
    expect(() => queue.flushWithBeacon()).not.toThrow();
  });
});

describe("splitBatches：每批最多 50 条、48 KiB", () => {
  const event = (size: number): ClientEvent => ({
    event_id: "0193e1a2-7c4b-7d2e-9f10-000000000006",
    event_name: "page_viewed",
    schema_version: "1-0-0",
    created_at: "2026-11-02T08:30:00.000Z",
    page_path: `/${"a".repeat(size)}`,
    page_type: "other",
    properties: {},
  });

  it("51 条 → 50 + 1", () => {
    expect(splitBatches(Array(51).fill(event(1))).map((b) => b.length)).toEqual([50, 1]);
  });

  it("按字节切：整批序列化后不超过 48 KiB", () => {
    const batches = splitBatches(Array(40).fill(event(2_000)));
    expect(batches.length).toBeGreaterThan(1);
    for (const batch of batches) {
      const bytes = new TextEncoder().encode(
        JSON.stringify({ sent_at: "2026-11-02T08:30:00.000Z", events: batch }),
      ).length;
      expect(bytes).toBeLessThanOrEqual(49_152);
    }
    expect(batches.flat()).toHaveLength(40);
  });

  it("单条就超过 48 KiB 的丢掉", () => {
    expect(splitBatches([event(60_000), event(1)]).flat()).toHaveLength(1);
  });
});
