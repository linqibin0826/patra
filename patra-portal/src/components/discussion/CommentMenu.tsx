"use client";

import { MoreHorizontal } from "lucide-react";
import { useRef } from "react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

interface CommentMenuProps {
  /// 本人的评论多出「编辑」「删除」
  mine: boolean;
  /// 复制失败时要选中文字的元素（`useCopy` 的第二步），传菜单按钮
  onCopyLink: (target: HTMLElement | null) => void;
  onEdit: () => void;
  onDelete: () => void;
}

/// 评论的 `···` 菜单（简报 §3.2）：所有人「复制链接」；本人另有「编辑」、分隔、「删除」。本版没有举报。
/// 菜单按钮上 `Shift+F10` 也能打开
export function CommentMenu({ mine, onCopyLink, onEdit, onDelete }: CommentMenuProps) {
  const trigger = useRef<HTMLButtonElement | null>(null);
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        ref={trigger}
        aria-label="评论操作"
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
      <DropdownMenuContent align="end" className="w-36">
        <DropdownMenuItem onClick={() => onCopyLink(trigger.current)}>复制链接</DropdownMenuItem>
        {mine && (
          <>
            <DropdownMenuItem onClick={onEdit}>编辑</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={onDelete} className="text-status-danger">
              删除
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
