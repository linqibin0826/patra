import { create } from "zustand";

interface BrowseFilterUiState {
  sheetOpen: boolean;
  open(): void;
  close(): void;
  toggle(): void;
}

/// 浏览页（/journals、/papers）筛选抽屉 UI 状态（移动端 sheet 开关）。
export const useBrowseFilterUiStore = create<BrowseFilterUiState>((set) => ({
  sheetOpen: false,
  open: () => set({ sheetOpen: true }),
  close: () => set({ sheetOpen: false }),
  toggle: () => set((s) => ({ sheetOpen: !s.sheetOpen })),
}));
