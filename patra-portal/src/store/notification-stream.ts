import { create } from "zustand";

interface NotificationStreamState {
  /// 未读数的实时推送连着：为真时不轮询（推送会给当前值）
  live: boolean;
  setLive(live: boolean): void;
}

/// 推送的连接状态（PAP-149 spec 第 5.1 节）：推送 hook 写、轮询读。是浏览器端的连接状态，不是服务端数据，不进查询缓存
export const useNotificationStreamStore = create<NotificationStreamState>((set) => ({
  live: false,
  setLive: (live) => set({ live }),
}));
