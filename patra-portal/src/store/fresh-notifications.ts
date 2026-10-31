import { create } from "zustand";

/// 新条目的浅底显示多久（v0.16 简报 §3.4）
export const FRESH_MS = 2_000;

interface FreshNotificationsState {
  /// 推送后刷新列表时新出现（或挪上来变回未读）的提醒 ID
  ids: ReadonlySet<string>;
  /// 记下一批，`FRESH_MS` 后清掉这一批
  mark(ids: readonly string[]): void;
}

/// 提醒页里「刚推送来的」条目（PAP-149 spec 第 5.4 节）：行据此加淡出的浅陶土底。只是界面上的一下提示，不进查询缓存
export const useFreshNotifications = create<FreshNotificationsState>((set) => ({
  ids: new Set(),
  mark: (ids) => {
    if (ids.length === 0) {
      return;
    }
    set((state) => ({ ids: new Set([...state.ids, ...ids]) }));
    setTimeout(() => {
      set((state) => ({ ids: new Set([...state.ids].filter((id) => !ids.includes(id))) }));
    }, FRESH_MS);
  },
}));
