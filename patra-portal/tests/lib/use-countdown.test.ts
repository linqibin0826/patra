import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useCountdown } from "@/lib/use-countdown";

const T0 = Date.parse("2026-10-23T08:00:00Z");

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval", "Date"] });
  vi.setSystemTime(T0);
});

afterEach(() => vi.useRealTimers());

describe("useCountdown", () => {
  it("没有截止时间是 0", () => {
    const { result } = renderHook(() => useCountdown(null));
    expect(result.current).toBe(0);
  });

  it("每秒减一，到 0 停", () => {
    const { result } = renderHook(() => useCountdown(T0 + 3_000));
    expect(result.current).toBe(3);
    act(() => vi.advanceTimersByTime(1_000));
    expect(result.current).toBe(2);
    act(() => vi.advanceTimersByTime(5_000));
    expect(result.current).toBe(0);
  });

  it("隔了很久才设新的截止时间：第一次渲染就是新的剩余秒数，不会先多出一截", () => {
    const rendered: number[] = [];
    const { rerender } = renderHook(
      ({ until }) => {
        const left = useCountdown(until);
        rendered.push(left);
        return left;
      },
      { initialProps: { until: null as number | null } },
    );
    vi.setSystemTime(T0 + 100_000);
    const from = rendered.length;
    rerender({ until: T0 + 130_000 });
    // 每一次渲染（包括 effect 跑之前那一次）都是 30
    expect(rendered.slice(from)).not.toHaveLength(0);
    expect(new Set(rendered.slice(from))).toEqual(new Set([30]));
  });
});
