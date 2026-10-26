"use client";

import { Download, FolderInput, FolderMinus, Quote, Trash2 } from "lucide-react";
import type { RefObject } from "react";
import { cn } from "@/lib/utils";

interface LibrarySelectionBarProps {
  count: number;
  /// 收藏夹视图才有「移出「x」」
  currentCollectionName: string | null;
  /// 「放进收藏夹…」按钮（浮层锚在它上面）
  addRef: RefObject<HTMLButtonElement | null>;
  busy: boolean;
  onAdd: () => void;
  onRemoveFromCollection: () => void;
  onCite: () => void;
  onExport: () => void;
  onRemove: () => void;
}

const BUTTON =
  "inline-flex h-10 items-center gap-1.5 rounded-full px-3.5 text-sm font-medium text-fg-1 transition-colors hover:bg-paper-200 aria-disabled:cursor-not-allowed aria-disabled:opacity-55 max-[880px]:flex-col max-[880px]:gap-0.5 max-[880px]:px-2 max-[880px]:text-2xs";

/// 吸底批量栏（简报 §3.11）：选中 ≥ 1 篇时出现。桌面在内容列宽度内，移动整宽、避开底部安全区，只显示图标 + 短文字
export function LibrarySelectionBar({
  count,
  currentCollectionName,
  addRef,
  busy,
  onAdd,
  onRemoveFromCollection,
  onCite,
  onExport,
  onRemove,
}: LibrarySelectionBarProps) {
  const disabled = busy || undefined;
  return (
    <div
      role="toolbar"
      aria-label="批量操作"
      className="sticky bottom-4 z-20 flex items-center gap-1 rounded-full border border-border-default bg-paper-50 py-1.5 pr-1.5 pl-4 shadow-lg max-[880px]:fixed max-[880px]:inset-x-0 max-[880px]:bottom-0 max-[880px]:justify-around max-[880px]:rounded-none max-[880px]:pb-[max(0.375rem,env(safe-area-inset-bottom))]"
    >
      <span className="mr-2 font-mono text-xs tabular-nums text-fg-3 max-[880px]:hidden">
        已选 {count} 篇
      </span>
      <button
        ref={addRef}
        type="button"
        aria-disabled={disabled}
        className={BUTTON}
        onClick={onAdd}
      >
        <FolderInput className="size-4" aria-hidden="true" />
        放进收藏夹…
      </button>
      {currentCollectionName !== null && (
        <button
          type="button"
          aria-disabled={disabled}
          className={BUTTON}
          onClick={onRemoveFromCollection}
        >
          <FolderMinus className="size-4" aria-hidden="true" />
          移出「{currentCollectionName}」
        </button>
      )}
      <button type="button" aria-disabled={disabled} className={BUTTON} onClick={onCite}>
        <Quote className="size-4" aria-hidden="true" />
        复制引用
      </button>
      <button type="button" aria-disabled={disabled} className={BUTTON} onClick={onExport}>
        <Download className="size-4" aria-hidden="true" />
        导出
      </button>
      <button
        type="button"
        aria-disabled={disabled}
        className={cn(BUTTON, "text-status-danger")}
        onClick={onRemove}
      >
        <Trash2 className="size-4" aria-hidden="true" />
        移除
      </button>
    </div>
  );
}
