import { SCHEMA_VERSIONS } from "@/lib/analytics/generated/events";
import { newId, truncatePath } from "@/lib/analytics/ids";
import { pageTypeOf } from "@/lib/analytics/page-type";
import type { ClientEvent, ClientEventName, ClientEventProperties } from "@/lib/analytics/types";

/// 门户自己的第一方路径（埋点 spec §6.2）。fetch 处直接写字面量：仓库的网关边界守护只放过以 / 开头的字面量
const ENDPOINT = "/api/reading-signals";
/// 第一条入队后多久发
const FLUSH_DELAY_MS = 5_000;
/// 攒满多少条立刻发
const FLUSH_COUNT = 20;
/// 每批最多 50 条、序列化后不超过 48 KiB（sendBeacon 在途上限约 64 KiB，留余量；埋点方案 §4.1）
const MAX_BATCH_COUNT = 50;
const MAX_BATCH_BYTES = 48 * 1024;
/// 批次外壳 `{"sent_at":"…","events":[]}` 的字节数
const SHELL_BYTES = JSON.stringify({ sent_at: "2026-11-02T08:30:00.000Z", events: [] }).length;

const encoder = new TextEncoder();

/// 按条数与字节切批；单条就超过上限的丢掉（正常用法碰不到）
export function splitBatches(
  events: readonly ClientEvent[],
  maxCount = MAX_BATCH_COUNT,
  maxBytes = MAX_BATCH_BYTES,
): ClientEvent[][] {
  const batches: ClientEvent[][] = [];
  let current: ClientEvent[] = [];
  let bytes = SHELL_BYTES;
  for (const event of events) {
    const size = encoder.encode(JSON.stringify(event)).length;
    if (SHELL_BYTES + size > maxBytes) {
      continue;
    }
    // 第二条起每条前面多一个逗号
    const added = current.length === 0 ? size : size + 1;
    if (current.length === maxCount || bytes + added > maxBytes) {
      batches.push(current);
      current = [];
      bytes = SHELL_BYTES;
    }
    bytes += current.length === 0 ? size : size + 1;
    current.push(event);
  }
  if (current.length > 0) {
    batches.push(current);
  }
  return batches;
}

/// 队列依赖的浏览器能力，测试里替换
export interface QueueDeps {
  now: () => number;
  fetch: typeof fetch;
  /// 浏览器不支持时为 null
  sendBeacon: ((url: string, data: Blob) => boolean) | null;
  /// 当前页面路径（不含查询串）
  location: () => string;
  setTimer: (fn: () => void, ms: number) => unknown;
  clearTimer: (handle: unknown) => void;
}

export interface Queue {
  track<K extends ClientEventName>(name: K, properties: ClientEventProperties[K]): void;
  /// 页面隐藏或离开时把剩下的用 sendBeacon 发出
  flushWithBeacon(): void;
}

/// 浏览器事件的队列（埋点 spec §6.1）：入队时记下页面，攒 5 秒或 20 条用 fetch keepalive 发一批；页面隐藏时
/// 用 sendBeacon 补发。失败一律丢弃，不重试、不打错误日志，不影响页面
export function createQueue(deps: QueueDeps): Queue {
  let buffer: ClientEvent[] = [];
  let timer: unknown = null;

  function take(): ClientEvent[][] {
    if (timer !== null) {
      deps.clearTimer(timer);
      timer = null;
    }
    const events = buffer;
    buffer = [];
    return splitBatches(events);
  }

  const bodyOf = (events: ClientEvent[]) =>
    JSON.stringify({ sent_at: new Date(deps.now()).toISOString(), events });

  function post(body: string): void {
    try {
      deps
        .fetch("/api/reading-signals", {
          method: "POST",
          keepalive: true,
          headers: { "Content-Type": "application/json" },
          body,
        })
        .catch(() => {});
    } catch {
      // fetch 同步抛错（如 keepalive 超出在途上限）同样丢弃
    }
  }

  function flush(): void {
    for (const batch of take()) {
      post(bodyOf(batch));
    }
  }

  return {
    track(name, properties) {
      const path = deps.location();
      buffer.push({
        event_id: newId(),
        event_name: name,
        schema_version: SCHEMA_VERSIONS[name],
        created_at: new Date(deps.now()).toISOString(),
        page_path: truncatePath(path),
        page_type: pageTypeOf(path),
        properties,
      } as ClientEvent);
      if (buffer.length >= FLUSH_COUNT) {
        flush();
      } else if (timer === null) {
        timer = deps.setTimer(flush, FLUSH_DELAY_MS);
      }
    },
    flushWithBeacon() {
      for (const batch of take()) {
        const body = bodyOf(batch);
        if (deps.sendBeacon) {
          try {
            deps.sendBeacon(ENDPOINT, new Blob([body], { type: "application/json" }));
          } catch {
            // 丢弃
          }
        } else {
          post(body);
        }
      }
    },
  };
}

let browserQueue: Queue | null = null;

/// 整个页面一个队列；服务端渲染时没有
function queue(): Queue | null {
  if (typeof window === "undefined") {
    return null;
  }
  browserQueue ??= createQueue({
    now: () => Date.now(),
    fetch: window.fetch.bind(window),
    sendBeacon:
      typeof navigator.sendBeacon === "function"
        ? (url, data) => navigator.sendBeacon(url, data)
        : null,
    location: () => window.location.pathname,
    setTimer: (fn, ms) => window.setTimeout(fn, ms),
    clearTimer: (handle) => window.clearTimeout(handle as number),
  });
  return browserQueue;
}

/// 记一个浏览器事件；服务端渲染时什么也不做
export function track<K extends ClientEventName>(
  name: K,
  properties: ClientEventProperties[K],
): void {
  queue()?.track(name, properties);
}

/// 页面隐藏或离开时调用
export function flushWithBeacon(): void {
  queue()?.flushWithBeacon();
}
