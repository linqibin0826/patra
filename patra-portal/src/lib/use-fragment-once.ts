import { useEffect, useRef, useState } from "react";

/// 邮件落地页读地址片段（`#token=…`）的共同做法（spec §8.3，简报 §3.18；v0.16 的退订确认页也用）：挂载后解析一次
/// 片段，并立即用 `replaceState` 从地址栏抹掉，令牌不留在历史记录与截图里（刷新后就读不到了）。首次渲染（服务端与
/// 浏览器一致）返回 `undefined`，挂载后返回 `parse` 的结果。
///
/// 解析结果记在 ref 里：严格模式下 effect 会跑两次，第二次时片段已经被抹掉了。`parse` 应是模块级的稳定函数。
export function useFragmentOnce<T>(parse: (hash: string) => T | null): T | null | undefined {
  const [value, setValue] = useState<T | null | undefined>(undefined);
  const read = useRef<{ value: T | null } | null>(null);

  useEffect(() => {
    if (read.current === null) {
      read.current = { value: parse(window.location.hash) };
    }
    if (window.location.hash) {
      window.history.replaceState(
        window.history.state,
        "",
        `${window.location.pathname}${window.location.search}`,
      );
    }
    setValue(read.current.value);
  }, [parse]);

  return value;
}
