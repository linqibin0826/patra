import Link from "next/link";
import type { MouseEvent } from "react";
import { AuthorName } from "@/components/discussion/AuthorIdentity";
import { type AuthorView, isSelfReply, replyToLabel } from "@/lib/discussion/author";
import { formatDateTime, formatRelative } from "@/lib/format-time";
import type { Comment } from "@/lib/interaction/discussion-schema";

interface CommentBylineProps {
  comment: Comment;
  view: AuthorView;
  permalink: string;
  now: number;
  onOpenPermalink: () => void;
  onShowHistory: () => void;
}

/// 点链接时按住了修饰键（新标签页、新窗口打开）：交给浏览器
function isModified(event: MouseEvent): boolean {
  return event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0;
}

/// 署名行（简报 §3.2）：名字 + 可选「作者」+ 可选「回复 @X」+ 时间（永久链接）+ 可选「已编辑」
export function CommentByline({
  comment,
  view,
  permalink,
  now,
  onOpenPermalink,
  onShowHistory,
}: CommentBylineProps) {
  const replyTo = isSelfReply(comment) ? null : replyToLabel(comment.replyTo);
  return (
    <div className="flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-0.5 pr-9 text-xs leading-4.5 text-fg-3">
      <AuthorName view={view} />
      {comment.isOwner && (
        <span className="inline-flex h-4.5 items-center self-center rounded-sm bg-clay-50 px-1.5 font-mono text-3xs font-medium tracking-mono text-clay-700">
          作者
        </span>
      )}
      <span aria-hidden="true">·</span>
      {replyTo && (
        <span>
          {replyTo.href ? (
            <>
              回复{" "}
              <Link href={replyTo.href} className="text-link hover:text-link-hover">
                {replyTo.text.slice(3)}
              </Link>
            </>
          ) : (
            replyTo.text
          )}
          <span aria-hidden="true"> ·</span>
        </span>
      )}
      <a
        href={permalink}
        title={formatDateTime(comment.createdAt)}
        onClick={(event) => {
          if (!isModified(event)) {
            event.preventDefault();
            onOpenPermalink();
          }
        }}
        className="text-fg-3 underline-offset-3 hover:text-fg-1 hover:underline"
      >
        {formatRelative(comment.createdAt, now)}
      </a>
      {comment.edited && (
        <>
          <span aria-hidden="true">·</span>
          <button
            type="button"
            onClick={onShowHistory}
            className="text-fg-3 underline-offset-3 hover:text-fg-1 hover:underline"
          >
            已编辑
          </button>
        </>
      )}
    </div>
  );
}
