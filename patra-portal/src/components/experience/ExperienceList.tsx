"use client";

import type { ReactNode } from "react";
import { btnBlock, btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

interface ExperienceListProps {
  children: ReactNode;
  /// 还有下一页
  hasMore: boolean;
  loadingMore: boolean;
  /// 上一次「加载更多」失败
  moreFailed: boolean;
  onLoadMore: () => void;
  /// 按钮文案：期刊页 `加载更多投稿经验`，主页 `加载更多`
  moreLabel: string;
}

/// 经验条目列表与「加载更多」（简报 §3.14、§3.17）：细线分隔的条目，不是卡片网格。
/// 翻页失败时保留已显示的内容，按钮下面就地提示
export function ExperienceList({
  children,
  hasMore,
  loadingMore,
  moreFailed,
  onLoadMore,
  moreLabel,
}: ExperienceListProps) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col">{children}</div>
      {hasMore && (
        <button
          type="button"
          aria-disabled={loadingMore || undefined}
          onClick={() => !loadingMore && onLoadMore()}
          className={cn(btnSecondary, btnBlock)}
        >
          {loadingMore ? "加载中…" : moreLabel}
        </button>
      )}
      {moreFailed && (
        <p role="alert" className="m-0 text-center text-sm text-status-danger">
          没有加载成功，请稍后再试
        </p>
      )}
    </div>
  );
}
