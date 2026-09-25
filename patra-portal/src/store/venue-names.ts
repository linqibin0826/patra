import { create } from "zustand";

interface VenueNamesState {
  names: Record<string, string>;
  remember(id: string, name: string): void;
}

/// 刚从候选添加的期刊名（URL 里只有 id）：服务端刊名到达前，供 chip 与已选期刊行即时显示刊名。
export const useVenueNamesStore = create<VenueNamesState>((set) => ({
  names: {},
  remember: (id, name) => set((s) => ({ names: { ...s.names, [id]: name } })),
}));
