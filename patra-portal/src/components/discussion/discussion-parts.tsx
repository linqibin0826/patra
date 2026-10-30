"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { SortToggle } from "@/components/portal/SortToggle";
import type { DiscussionCopy } from "@/lib/discussion/copy";
import type { useDiscussionLocation } from "@/lib/discussion/use-discussion-location";
import type { ViewerSeed } from "@/lib/discussion/viewer";
import type { DiscussionSort, Subject } from "@/lib/interaction/discussion-schema";

/// 新发表的评论高亮多久（简报 §3.4）
const FRESH_MS = 2000;

/// 根评论的三种排序（简报 v0.15 §3.5）
const SORT_OPTIONS = [
  { value: "asc", label: "正序" },
  { value: "latest", label: "最新" },
  { value: "helpful", label: "最有帮助" },
] as const satisfies readonly { value: DiscussionSort; label: string }[];

export type DiscussionLocation = ReturnType<typeof useDiscussionLocation>;

export interface DiscussionViewProps {
  subject: Subject;
  copy: DiscussionCopy;
  viewer: ViewerSeed;
  /// 服务端渲染时的时间：首屏的相对时间与服务端一致
  now: number;
  /// 对象页的路径（`/papers/1`），拼站内永久链接
  basePath: string;
  /// 对象页的完整公开地址，「复制链接」用
  shareBase: string;
  /// 区块标题的 ID（`section` 的 `aria-labelledby`）
  titleId?: string;
}

/// 标题行：左边区块标题，右边排序（只在列表视图、且有评论时出）
export function DiscussionHeader({
  title,
  titleId,
  sort,
  onSort,
}: {
  title: string;
  titleId: string;
  sort?: DiscussionSort;
  onSort?: (sort: DiscussionSort) => void;
}) {
  return (
    <div className="flex min-h-9 flex-wrap items-center justify-between gap-x-4 gap-y-2">
      <SectionEyebrow as="h2" id={titleId} className="mb-0">
        {title}
      </SectionEyebrow>
      {sort && onSort && <SortToggle value={sort} options={SORT_OPTIONS} onChange={onSort} />}
    </div>
  );
}

/// 刚发表的评论：高亮 2 秒、焦点落到它的时间链接上
export function useFresh(): [string | null, (commentId: string) => void] {
  const [fresh, setFresh] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (!fresh) {
      return;
    }
    document
      .getElementById(`comment-${fresh}`)
      ?.querySelector<HTMLAnchorElement>(`a[href$="comment=${fresh}"]`)
      ?.focus();
  }, [fresh]);
  useEffect(
    () => () => {
      if (timer.current) {
        clearTimeout(timer.current);
      }
    },
    [],
  );
  const mark = useCallback((commentId: string) => {
    if (timer.current) {
      clearTimeout(timer.current);
    }
    setFresh(commentId);
    timer.current = setTimeout(() => setFresh(null), FRESH_MS);
  }, []);
  return [fresh, mark];
}

/// 读屏播报（`已发表`、`已回复`）
export function Announcer({ text }: { text: string }) {
  return (
    <div role="status" className="sr-only">
      {text}
    </div>
  );
}

/// 换回复框前的确认（简报 §3.3）：另一个回复框里有内容
export function DiscardReplyConfirm({
  onDiscard,
  onKeep,
}: {
  onDiscard: () => void;
  onKeep: () => void;
}) {
  return (
    <p className="flex flex-wrap items-center gap-2.5 text-sm text-fg-1">
      放弃正在写的回复？
      <button
        type="button"
        onClick={onDiscard}
        className="font-medium text-link hover:text-link-hover"
      >
        放弃
      </button>
      <button
        type="button"
        onClick={onKeep}
        className="font-medium text-link hover:text-link-hover"
      >
        继续写
      </button>
    </p>
  );
}
