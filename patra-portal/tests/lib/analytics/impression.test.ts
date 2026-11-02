import { beforeEach, describe, expect, it, vi } from "vitest";
import { createImpressionTimer } from "@/lib/analytics/client/impression";

/// 手动推进的计时器
function manualTimers() {
  const pending = new Set<{ fn: () => void; at: number }>();
  let clock = 0;
  return {
    setTimer: (fn: () => void, ms: number) => {
      const handle = { fn, at: clock + ms };
      pending.add(handle);
      return handle;
    },
    clearTimer: (handle: unknown) => {
      pending.delete(handle as { fn: () => void; at: number });
    },
    advance(ms: number) {
      clock += ms;
      for (const handle of [...pending]) {
        if (handle.at <= clock) {
          pending.delete(handle);
          handle.fn();
        }
      }
    },
  };
}

let timers: ReturnType<typeof manualTimers>;
const onImpress = vi.fn();
const make = () => createImpressionTimer({ onImpress, ...timers });

beforeEach(() => {
  timers = manualTimers();
  onImpress.mockReset();
});

// 埋点方案 §6.4：≥ 50% 可见、连续 1 秒、标签页在前台
describe("曝光判定", () => {
  it("60% 可见 0.8 秒后滚走：不算", () => {
    const timer = make();
    timer.ratio(0.6);
    timers.advance(800);
    timer.ratio(0);
    timers.advance(2_000);
    expect(onImpress).not.toHaveBeenCalled();
  });

  it("滚回来连续 1.2 秒：算一次；之后再滚出滚入不再算", () => {
    const timer = make();
    timer.ratio(0.6);
    timers.advance(800);
    timer.ratio(0);
    timer.ratio(0.6);
    timers.advance(999);
    expect(onImpress).not.toHaveBeenCalled();
    timers.advance(201);
    expect(onImpress).toHaveBeenCalledTimes(1);
    timer.ratio(0);
    timer.ratio(1);
    timers.advance(5_000);
    expect(onImpress).toHaveBeenCalledTimes(1);
  });

  it("不足 50% 可见：不算", () => {
    const timer = make();
    timer.ratio(0.49);
    timers.advance(5_000);
    expect(onImpress).not.toHaveBeenCalled();
  });

  it("正好 50% 可见：算", () => {
    const timer = make();
    timer.ratio(0.5);
    timers.advance(1_000);
    expect(onImpress).toHaveBeenCalledTimes(1);
  });

  it("可见但标签页在后台：不算；回到前台重新计 1 秒", () => {
    const timer = make();
    timer.visibility(false);
    timer.ratio(0.8);
    timers.advance(5_000);
    expect(onImpress).not.toHaveBeenCalled();
    timer.visibility(true);
    timers.advance(999);
    expect(onImpress).not.toHaveBeenCalled();
    timers.advance(1);
    expect(onImpress).toHaveBeenCalledTimes(1);
  });

  it("计时中切到后台：清零", () => {
    const timer = make();
    timer.ratio(0.8);
    timers.advance(900);
    timer.visibility(false);
    timer.visibility(true);
    timers.advance(900);
    expect(onImpress).not.toHaveBeenCalled();
  });

  it("dispose 之后不再发", () => {
    const timer = make();
    timer.ratio(0.8);
    timer.dispose();
    timers.advance(2_000);
    expect(onImpress).not.toHaveBeenCalled();
  });
});
