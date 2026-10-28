import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useNow } from "@/lib/discussion/use-now";

afterEach(() => {
  vi.useRealTimers();
});

describe("useNow", () => {
  it("首帧用服务端的时间，挂载后改成本机时间并每分钟刷新", () => {
    vi.useFakeTimers();
    vi.setSystemTime(Date.parse("2026-10-27T06:20:00Z"));
    const server = Date.parse("2026-10-27T06:00:00Z");
    const { result } = renderHook(() => useNow(server));
    expect(result.current).toBe(Date.parse("2026-10-27T06:20:00Z"));
    act(() => {
      vi.advanceTimersByTime(60_000);
    });
    expect(result.current).toBe(Date.parse("2026-10-27T06:21:00Z"));
  });
});
