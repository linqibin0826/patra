"use client";

import { Bookmark } from "lucide-react";
import { btnBlock, btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";
import { useBookmarkStore } from "@/store/bookmarks";

interface BookmarkButtonProps {
  paperId: string;
  /** 撑满容器宽度（详情页侧栏） */
  block?: boolean;
  /** 仅图标的圆形小按钮（首页文献条目） */
  compact?: boolean;
}

/// 收藏按钮——本版为 mock：无用户系统、无后端，状态存 Zustand（按 paperId 共享，同页多实例同步）。
export function BookmarkButton({ paperId, block = false, compact = false }: BookmarkButtonProps) {
  const bookmarked = useBookmarkStore((s) => s.ids.has(paperId));
  const toggle = useBookmarkStore((s) => s.toggle);

  if (compact) {
    return (
      <button
        type="button"
        aria-pressed={bookmarked}
        aria-label={bookmarked ? "已收藏" : "收藏"}
        title={bookmarked ? "取消收藏" : "收藏"}
        onClick={() => toggle(paperId)}
        className="inline-flex size-8 items-center justify-center rounded-full text-fg-3 transition-colors duration-200 hover:bg-paper-200 hover:text-ink-900 aria-pressed:text-clay-600"
      >
        <Bookmark
          size={15}
          strokeWidth={1.6}
          aria-hidden
          className={cn(
            "motion-safe:transition-transform duration-300 ease-out-expo",
            bookmarked && "fill-current motion-safe:scale-110",
          )}
        />
      </button>
    );
  }

  return (
    <button
      type="button"
      aria-pressed={bookmarked}
      onClick={() => toggle(paperId)}
      className={cn(btnSecondary, block && btnBlock)}
    >
      <Bookmark
        size={14}
        strokeWidth={1.5}
        aria-hidden
        className={cn(
          "motion-safe:transition-transform duration-300 ease-out-expo",
          bookmarked && "fill-clay-500 text-clay-500 motion-safe:scale-110",
        )}
      />
      {bookmarked ? "已收藏" : "收藏"}
    </button>
  );
}
