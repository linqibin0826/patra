import { beforeEach, describe, expect, it, vi } from "vitest";
import { createDwellTracker, isReadingVisible } from "@/lib/analytics/client/dwell";

const VIEWPORT = 1_000;

describe("isReadingVisible：正文占视口一半以上才算在读（埋点方案 §6.3）", () => {
  it.each([
    ["长文滚到中间：可见 700 ≥ 500", { top: 300, bottom: 3_000 }, true],
    ["只露出视口底部 200", { top: 800, bottom: 3_000 }, false],
    ["正好一半", { top: 500, bottom: 3_000 }, true],
    ["正文已滚出视口上方一大半，只剩 300", { top: -2_700, bottom: 300 }, false],
    ["矮于半个视口且整段可见", { top: 100, bottom: 500 }, true],
    ["矮于半个视口但只露出一半", { top: 800, bottom: 1_200 }, false],
  ])("%s → %s", (_, rect, expected) => {
    expect(isReadingVisible(rect, VIEWPORT)).toBe(expected);
  });
});

describe("createDwellTracker：累计到 10 秒发一次", () => {
  let clock: number;
  let pending: { fn: () => void; at: number } | null;
  const onReach = vi.fn();
  const advance = (ms: number) => {
    clock += ms;
    if (pending && pending.at <= clock) {
      const { fn } = pending;
      pending = null;
      fn();
    }
  };
  const make = () =>
    createDwellTracker({
      thresholdMs: 10_000,
      onReach,
      now: () => clock,
      setTimer: (fn, ms) => {
        pending = { fn, at: clock + ms };
        return pending;
      },
      clearTimer: (handle) => {
        if (handle === pending) pending = null;
      },
    });

  beforeEach(() => {
    clock = 0;
    pending = null;
    onReach.mockReset();
  });

  it("读 6 秒 → 离开 1 分钟 → 回来再读 4 秒：此刻发出，visible_seconds = 10", () => {
    const dwell = make();
    dwell.reading(true);
    advance(6_000);
    dwell.reading(false);
    advance(60_000);
    dwell.reading(true);
    advance(3_999);
    expect(onReach).not.toHaveBeenCalled();
    advance(1);
    expect(onReach).toHaveBeenCalledWith(10);
  });

  it("计时器比时钟早一点点触发：秒数仍记 10（契约要求 ≥ 10）", () => {
    let fire: () => void = () => {};
    const dwell = createDwellTracker({
      thresholdMs: 10_000,
      onReach,
      now: () => clock,
      setTimer: (fn) => {
        fire = fn;
        return fn;
      },
      clearTimer: () => {},
    });
    dwell.reading(true);
    clock += 9_999.6;
    fire();
    expect(onReach).toHaveBeenCalledWith(10);
  });

  it("发过一次后不再发", () => {
    const dwell = make();
    dwell.reading(true);
    advance(10_000);
    dwell.reading(false);
    dwell.reading(true);
    advance(20_000);
    expect(onReach).toHaveBeenCalledTimes(1);
  });

  it("重复报告在读不重复计时", () => {
    const dwell = make();
    dwell.reading(true);
    advance(5_000);
    dwell.reading(true);
    advance(5_000);
    expect(onReach).toHaveBeenCalledWith(10);
  });

  it("不足 10 秒就离开页面：不发", () => {
    const dwell = make();
    dwell.reading(true);
    advance(9_900);
    dwell.dispose();
    advance(10_000);
    expect(onReach).not.toHaveBeenCalled();
  });
});
