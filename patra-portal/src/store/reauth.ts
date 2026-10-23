import { create } from "zustand";

interface ReauthState {
  /// 再认证对话框是否打开
  open: boolean;
  /// 打开对话框，等用户验证完：验证成功为 true，取消为 false。已在等时返回同一个 promise
  request(): Promise<boolean>;
  /// 对话框给出结果并关闭；没有待决请求时什么也不做
  settle(ok: boolean): void;
}

/// 再认证对话框（spec §6.2）：同一时间只有一个待决请求，两个按钮几乎同时触发也只弹一次。
/// promise 与它的 resolve 放在闭包里，不进状态（不触发渲染）。
export const useReauthStore = create<ReauthState>((set) => {
  let pending: Promise<boolean> | null = null;
  let resolvePending: ((ok: boolean) => void) | null = null;
  return {
    open: false,
    request: () => {
      if (pending) {
        return pending;
      }
      pending = new Promise<boolean>((resolve) => {
        resolvePending = resolve;
      });
      set({ open: true });
      return pending;
    },
    settle: (ok) => {
      const resolve = resolvePending;
      pending = null;
      resolvePending = null;
      set({ open: false });
      resolve?.(ok);
    },
  };
});
