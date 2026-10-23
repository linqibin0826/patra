/// 服务不可用：客户端按「稍后再试」处理
export type Unavailable = { readonly ok: false; readonly reason: "unavailable" };

const UNAVAILABLE: Unavailable = { ok: false, reason: "unavailable" };

/// 调 Server Action / 接口的 promise 被拒绝时（网络断开、请求没发出去）按服务不可用处理，调用方只需按 `reason` 分支
export function orUnavailable<T>(promise: Promise<T>): Promise<T | Unavailable> {
  return promise.catch(() => UNAVAILABLE);
}
