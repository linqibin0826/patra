import { useEffect, useState } from "react";

/// 距 `until`（毫秒时间戳）还剩多少秒，向上取整；`until` 为 null 或已过时是 0。每秒更新一次，到 0 就停表。
export function useCountdown(until: number | null): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (until === null || until <= Date.now()) {
      return;
    }
    setNow(Date.now());
    const timer = setInterval(() => {
      const current = Date.now();
      setNow(current);
      if (current >= until) {
        clearInterval(timer);
      }
    }, 1_000);
    return () => clearInterval(timer);
  }, [until]);
  return until === null ? 0 : Math.max(0, Math.ceil((until - now) / 1_000));
}
