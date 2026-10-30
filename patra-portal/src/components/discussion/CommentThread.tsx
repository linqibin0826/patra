"use client";

import { ChevronDown } from "lucide-react";
import type { ReactNode } from "react";
import { type CommentActions, CommentItem } from "@/components/discussion/CommentItem";
import { ThreadStats } from "@/components/discussion/ThreadStats";
import { formatCount } from "@/lib/format-count";
import type { Comment, Thread } from "@/lib/interaction/discussion-schema";

interface CommentThreadProps {
  thread: Thread;
  now: number;
  /// 一条评论的站内永久链接
  permalinkOf: (comment: Comment) => string;
  actions: CommentActions;
  /// 刚发表的那条（高亮 2 秒）
  freshId?: string | null;
  /// 单楼视图的目标（持续高亮）
  currentId?: string | null;
  /// 正在原地编辑的那条换成编辑器
  editingOf?: (comment: Comment) => ReactNode | undefined;
  /// 「展开其余 N 条回复」
  onExpand?: () => void;
  expanding?: boolean;
  /// 开在这一楼最后的回复框
  replyBox?: ReactNode;
}

/// 一楼（简报 §3.3、v0.15 §3.4）：根评论（操作行右端是楼内统计）+ 平铺的回复（左侧一条结构竖线）+
/// 「展开其余 N 条回复」+ 回复框
export function CommentThread({
  thread,
  now,
  permalinkOf,
  actions,
  freshId,
  currentId,
  editingOf,
  onExpand,
  expanding = false,
  replyBox,
}: CommentThreadProps) {
  const item = (comment: Comment, level: "root" | "reply", trailing?: ReactNode) => (
    <CommentItem
      key={comment.commentId}
      comment={comment}
      level={level}
      permalink={permalinkOf(comment)}
      now={now}
      actions={actions}
      fresh={comment.commentId === freshId}
      current={comment.commentId === currentId}
      editing={editingOf?.(comment)}
      trailing={trailing}
    />
  );
  // 剩下没给出的回复里未删除的条数；剩下的全是占位时为 0
  const remaining = thread.replyCount - thread.replies.filter((reply) => !reply.deleted).length;
  const hasBranch = thread.replies.length > 0 || thread.hasMoreReplies || replyBox;
  return (
    <div className="flex flex-col gap-3.5 border-t border-border-subtle py-5 first:border-t-0 first:pt-1">
      {item(
        thread.root,
        "root",
        // 没有回复时不传：空槽位在窄屏会占满一行
        thread.replyCount > 0 && thread.lastReplyAt !== null ? (
          <ThreadStats replyCount={thread.replyCount} lastReplyAt={thread.lastReplyAt} now={now} />
        ) : undefined,
      )}
      {hasBranch && (
        <div className="ml-11 flex flex-col gap-4 border-l border-border-default pl-4 max-[880px]:ml-3.5 max-[880px]:pl-3">
          {thread.replies.map((reply) => item(reply, "reply"))}
          {thread.hasMoreReplies && onExpand && (
            <button
              type="button"
              aria-expanded={false}
              disabled={expanding}
              onClick={onExpand}
              className="inline-flex items-center gap-1 self-start text-xs font-medium text-link hover:text-link-hover disabled:text-fg-3"
            >
              {expanding ? (
                "加载中…"
              ) : (
                <>
                  {remaining > 0 ? `展开其余 ${formatCount(remaining)} 条回复` : "展开全部回复"}
                  <ChevronDown className="size-3.5" aria-hidden="true" />
                </>
              )}
            </button>
          )}
          {replyBox}
        </div>
      )}
    </div>
  );
}
