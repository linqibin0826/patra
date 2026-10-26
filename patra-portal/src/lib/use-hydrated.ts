import { useSyncExternalStore } from "react";

const subscribe = () => () => {};

/// 是否已经过了水合：服务端渲染与水合那一次是 false（与服务端输出一致），之后是 true。
/// 客户端里直接挂载（不是水合）的组件第一次渲染就是 true
export function useHydrated(): boolean {
  return useSyncExternalStore(
    subscribe,
    () => true,
    () => false,
  );
}
