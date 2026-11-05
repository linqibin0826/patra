/// 正文在视口里的位置（`getBoundingClientRect` 的相关部分）
export interface ReadingRect {
  top: number;
  bottom: number;
}

/// 正文算不算「在读」（埋点方案 §6.3）：在视口里的可见高度 ≥ 视口高度的一半；正文本身矮于半个视口时要整段可见。
/// 不用「正文 50% 可见」：长文超过两屏时它永远达不到
export function isReadingVisible(rect: ReadingRect, viewportHeight: number): boolean {
  const half = viewportHeight / 2;
  if (rect.bottom - rect.top < half) {
    return rect.top >= 0 && rect.bottom <= viewportHeight;
  }
  return Math.min(rect.bottom, viewportHeight) - Math.max(rect.top, 0) >= half;
}

export interface DwellTracker {
  /// 现在是否在读（正文可见且标签页在前台）
  reading(active: boolean): void;
  dispose(): void;
}

/// 全文停留的累计，不碰 DOM：在读时累计、不在读时暂停，累计到 `thresholdMs` 调一次 `onReach(整秒数)`，之后不再调
export function createDwellTracker(opts: {
  thresholdMs: number;
  onReach: (seconds: number) => void;
  now: () => number;
  setTimer: (fn: () => void, ms: number) => unknown;
  clearTimer: (handle: unknown) => void;
}): DwellTracker {
  let accumulated = 0;
  let since: number | null = null;
  let timer: unknown = null;
  let done = false;

  function pause(): void {
    if (since !== null) {
      accumulated += opts.now() - since;
      since = null;
    }
    if (timer !== null) {
      opts.clearTimer(timer);
      timer = null;
    }
  }

  return {
    reading(active) {
      if (done) {
        return;
      }
      if (!active) {
        pause();
        return;
      }
      if (since !== null) {
        return;
      }
      since = opts.now();
      timer = opts.setTimer(() => {
        timer = null;
        pause();
        done = true;
        // 计时器与时钟不是同一个来源，可能早一点点触发：秒数至少是阈值（契约要求 ≥ 10）
        opts.onReach(Math.max(opts.thresholdMs / 1000, Math.floor(accumulated / 1000)));
      }, opts.thresholdMs - accumulated);
    },
    dispose() {
      pause();
      done = true;
    },
  };
}
