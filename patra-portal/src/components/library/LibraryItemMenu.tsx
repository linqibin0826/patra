"use client";

import { MoreHorizontal } from "lucide-react";
import type { RefObject } from "react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

interface LibraryItemMenuProps {
  /// 已失效：只能编辑备注、移出、移除
  unavailable: boolean;
  /// 当前收藏夹的名称（收藏夹视图才有「移出「x」」）
  currentCollectionName: string | null;
  /// 菜单按钮（「放进收藏夹…」的浮层锚在它上面）
  triggerRef: RefObject<HTMLButtonElement | null>;
  onAddToCollections: () => void;
  onEditNote: () => void;
  onCite: () => void;
  onExport: () => void;
  onRemoveFromCollection: () => void;
  onRemove: () => void;
}

/// 文献库条目的 `···` 菜单（简报 §3.10）。键盘：菜单按钮上 `Shift+F10` 也能打开
export function LibraryItemMenu({
  unavailable,
  currentCollectionName,
  triggerRef,
  onAddToCollections,
  onEditNote,
  onCite,
  onExport,
  onRemoveFromCollection,
  onRemove,
}: LibraryItemMenuProps) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        ref={triggerRef}
        aria-label="条目操作"
        onKeyDown={(event) => {
          if (event.shiftKey && event.key === "F10") {
            event.preventDefault();
            event.currentTarget.click();
          }
        }}
        className="inline-flex size-8 items-center justify-center rounded-full text-fg-3 transition-colors hover:bg-paper-200 hover:text-ink-900 data-popup-open:bg-paper-200"
      >
        <MoreHorizontal className="size-4" aria-hidden="true" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-48">
        {!unavailable && (
          <DropdownMenuItem onClick={onAddToCollections}>放进收藏夹…</DropdownMenuItem>
        )}
        <DropdownMenuItem onClick={onEditNote}>编辑备注</DropdownMenuItem>
        {!unavailable && (
          <>
            <DropdownMenuItem onClick={onCite}>复制引用</DropdownMenuItem>
            <DropdownMenuItem onClick={onExport}>导出…</DropdownMenuItem>
          </>
        )}
        <DropdownMenuSeparator />
        {currentCollectionName !== null && (
          <DropdownMenuItem onClick={onRemoveFromCollection}>
            移出「{currentCollectionName}」
          </DropdownMenuItem>
        )}
        <DropdownMenuItem onClick={onRemove} className="text-status-danger">
          从文献库移除
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
