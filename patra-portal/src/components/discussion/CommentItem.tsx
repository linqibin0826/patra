"use client";

import { type ReactNode, useEffect, useRef, useState } from "react";
import { AuthorAvatar } from "@/components/discussion/AuthorIdentity";
import { CommentByline } from "@/components/discussion/CommentByline";
import { CommentMenu } from "@/components/discussion/CommentMenu";
import { MarkdownBody } from "@/components/discussion/MarkdownBody";
import { HelpfulButton } from "@/components/helpful/HelpfulButton";
import { authorView, isSelfReply } from "@/lib/discussion/author";
import type { Comment } from "@/lib/interaction/discussion-schema";
import { cn } from "@/lib/utils";

/// 一条评论上的全部操作，由讨论区统一处理
export interface CommentActions {
  onReply: (comment: Comment) => void;
  onShowConversation: (comment: Comment) => void;
  onShowHistory: (comment: Comment) => void;
  onEdit: (comment: Comment) => void;
  onDelete: (comment: Comment) => void;
  onCopyLink: (comment: Comment, target: HTMLElement | null) => void;
  onOpenPermalink: (comment: Comment) => void;
  /// 点有帮助时这条已被删除：提示结束后重取它所在的楼
  onHelpfulGone: (comment: Comment) => void;
}

interface CommentItemProps {
  comment: Comment;
  level: "root" | "reply";
  /// 站内永久链接 `/papers/<id>?comment=<评论 id>`
  permalink: string;
  now: number;
  actions: CommentActions;
  /// 单楼视图的目标：持续高亮、`aria-current`
  current?: boolean;
  /// 刚发表的：高亮 2 秒淡出
  fresh?: boolean;
  /// 原地编辑时代替正文的编辑器
  editing?: ReactNode;
  /// 只读（「查看对话」对话框里）：没有操作行与菜单
  readOnly?: boolean;
}

/// 超过 12 行折叠到 8 行（简报 §3.2）
const FOLD_AFTER_LINES = 12;

/// 评论条目（简报 §3.2）：头像、署名行、正文、操作行、`···` 菜单；占位只占位置、只有一行字（§3.9）
export function CommentItem({
  comment,
  level,
  permalink,
  now,
  actions,
  current = false,
  fresh = false,
  editing,
  readOnly = false,
}: CommentItemProps) {
  const reply = level === "reply";
  const grid = reply
    ? "grid-cols-[24px_minmax(0,1fr)] gap-2.5 max-[880px]:grid-cols-[22px_minmax(0,1fr)] max-[880px]:gap-2"
    : "grid-cols-[32px_minmax(0,1fr)] gap-3 max-[880px]:grid-cols-[28px_minmax(0,1fr)] max-[880px]:gap-2.5";
  const highlight = current
    ? "-m-3 bg-clay-50 p-3 ring-1 ring-clay-200"
    : fresh
      ? "bg-clay-50"
      : "";

  if (comment.deleted) {
    return (
      <div
        id={`comment-${comment.commentId}`}
        aria-current={current ? "true" : undefined}
        className={cn(
          "grid scroll-mt-[126px] items-center rounded-md",
          grid,
          reply ? "min-h-6" : "min-h-8",
          highlight,
        )}
      >
        <span className="aspect-square w-full rounded-full border border-dashed border-ink-300" />
        <span className="text-sm text-fg-3 italic">该评论已删除</span>
      </div>
    );
  }

  const view = authorView(comment);
  return (
    <article
      id={`comment-${comment.commentId}`}
      aria-current={current ? "true" : undefined}
      className={cn(
        "relative grid scroll-mt-[126px] rounded-md transition-colors duration-(--dur-slow) motion-reduce:transition-none",
        grid,
        highlight,
      )}
    >
      <AuthorAvatar view={view} size={reply ? 24 : 32} mobileSize={reply ? 22 : 28} />
      <div className="flex min-w-0 flex-col gap-1.5">
        <CommentByline
          comment={comment}
          view={view}
          permalink={permalink}
          now={now}
          onOpenPermalink={() => actions.onOpenPermalink(comment)}
          onShowHistory={() => actions.onShowHistory(comment)}
        />
        {editing ?? (
          <FoldedBody>
            <MarkdownBody source={comment.body ?? ""} mentions={comment.mentions} />
          </FoldedBody>
        )}
        {!editing && !readOnly && (
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs leading-4.5">
            <HelpfulButton
              target={{ type: "comment", id: comment.commentId }}
              helpful={comment.helpful}
              count={comment.helpfulCount}
              own={comment.mine}
              variant="text"
              onGone={() => actions.onHelpfulGone(comment)}
            />
            <TextAction onClick={() => actions.onReply(comment)}>回复</TextAction>
            {comment.replyTo && !isSelfReply(comment) && (
              <TextAction onClick={() => actions.onShowConversation(comment)}>查看对话</TextAction>
            )}
          </div>
        )}
      </div>
      {!editing && !readOnly && (
        <div className="absolute -top-1.5 -right-1.5">
          <CommentMenu
            mine={comment.mine}
            onCopyLink={(target) => actions.onCopyLink(comment, target)}
            onEdit={() => actions.onEdit(comment)}
            onDelete={() => actions.onDelete(comment)}
          />
        </div>
      )}
    </article>
  );
}

function TextAction({ onClick, children }: { onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex items-center font-medium text-fg-3 hover:text-fg-1 max-[880px]:min-h-8"
    >
      {children}
    </button>
  );
}

/// 长评论折叠：渲染后量高度，超过 12 行折到 8 行、末尾渐隐 + 「展开全文」
function FoldedBody({ children }: { children: ReactNode }) {
  const body = useRef<HTMLDivElement | null>(null);
  const [long, setLong] = useState(false);
  const [expanded, setExpanded] = useState(false);
  useEffect(() => {
    const element = body.current;
    if (!element || typeof ResizeObserver === "undefined") {
      return;
    }
    const observer = new ResizeObserver(() => {
      const lineHeight = Number.parseFloat(window.getComputedStyle(element).lineHeight) || 24;
      setLong(element.scrollHeight > lineHeight * FOLD_AFTER_LINES);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  const folded = long && !expanded;
  return (
    <div className="flex flex-col items-start gap-1">
      <div
        ref={body}
        className={cn(
          "relative w-full max-w-[72ch]",
          folded &&
            "max-h-[calc(8*1.6em)] overflow-hidden after:absolute after:inset-x-0 after:bottom-0 after:h-12 after:bg-linear-to-b after:from-transparent after:to-bg-canvas",
        )}
      >
        {children}
      </div>
      {long && (
        <button
          type="button"
          onClick={() => setExpanded((value) => !value)}
          className="text-xs font-medium text-link hover:text-link-hover"
        >
          {expanded ? "收起" : "展开全文"}
        </button>
      )}
    </div>
  );
}
