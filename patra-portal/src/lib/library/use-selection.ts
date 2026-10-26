import { useCallback, useMemo, useState } from "react";

/// 文献库选择模式里的选中项（只在当前页内，简报 §3.11）。`ids` 是本页条目；翻页、列表变化后不在本页的选中项自动丢掉
export function useSelection(ids: readonly string[]) {
  const [picked, setPicked] = useState<ReadonlySet<string>>(new Set());
  const selected = useMemo(() => new Set(ids.filter((id) => picked.has(id))), [ids, picked]);
  const allSelected = ids.length > 0 && selected.size === ids.length;
  const toggle = useCallback((id: string) => {
    setPicked((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }, []);
  const togglePage = useCallback(() => {
    setPicked(allSelected ? new Set() : new Set(ids));
  }, [allSelected, ids]);
  const clear = useCallback(() => setPicked(new Set()), []);
  return { selected, allSelected, toggle, togglePage, clear };
}
