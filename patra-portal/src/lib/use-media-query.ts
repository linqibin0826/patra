import { useSyncExternalStore } from "react";

/// 当前环境有没有 `matchMedia`（jsdom 没有）
const supported = () => typeof window.matchMedia === "function";

/// 媒体查询是否命中（浏览器端，随窗口变化更新）；服务端渲染、首帧与没有 `matchMedia` 的环境按不命中处理
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      if (!supported()) {
        return () => {};
      }
      const list = window.matchMedia(query);
      list.addEventListener("change", onChange);
      return () => list.removeEventListener("change", onChange);
    },
    () => supported() && window.matchMedia(query).matches,
    () => false,
  );
}
