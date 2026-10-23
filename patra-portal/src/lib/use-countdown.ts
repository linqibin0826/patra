import { useEffect, useReducer } from "react";

/// 距 `until`（毫秒时间戳）还剩多少秒，向上取整；`until` 为 null 或已过时是 0。每秒重新渲染一次，到 0 就停表。
/// 剩余秒数在渲染时按当前时间算，不存上一次的时间：换了新的截止时间时第一次渲染就是对的（存下来的旧时间会让
/// 它先多出一截）。截止时间只来自用户操作之后的响应，服务端渲染时总是 null，不会水合不一致。
export function useCountdown(until: number | null): number {
  const [, tick] = useReducer((count: number) => count + 1, 0);
  useEffect(() => {
    if (until === null || until <= Date.now()) {
      return;
    }
    const timer = setInterval(() => {
      tick();
      if (Date.now() >= until) {
        clearInterval(timer);
      }
    }, 1_000);
    return () => clearInterval(timer);
  }, [until]);
  return until === null ? 0 : Math.max(0, Math.ceil((until - Date.now()) / 1_000));
}
