import { useEffect, useState } from "react";

/// 刷新相对时间的间隔
const TICK_MS = 60_000;

/// 相对时间要用的「现在」：首帧用服务端渲染时的时间（水合一致），挂载后换成本机时间并每分钟刷新一次
export function useNow(serverNow: number): number {
  const [now, setNow] = useState(serverNow);
  useEffect(() => {
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), TICK_MS);
    return () => clearInterval(timer);
  }, []);
  return now;
}
