"use client";

import Link from "next/link";
import { AuthorAvatar } from "@/components/discussion/AuthorIdentity";
import { RichInlineText } from "@/components/portal/RichInlineText";
import type { AuthorView } from "@/lib/discussion/author";
import { formatDateTime, formatRelative } from "@/lib/format-time";
import type { Notification } from "@/lib/interaction/notification-schema";
import {
  actionLabel,
  actorName,
  actorOf,
  contextLabel,
  excerptOf,
  notificationHref,
} from "@/lib/notifications/row";
import { cn } from "@/lib/utils";

/// 触发人名字的颜色（简报 §3.19、§3.20）：匿名作者 `fg-2`、已注销用户 `fg-3`、其余 `fg-1`；已读整体降一级（不低于 `fg-3`）
function nameColor(view: AuthorView, unread: boolean): string {
  if (view.kind === "deleted" || (view.kind === "anonymous" && !unread)) {
    return "text-fg-3";
  }
  return view.kind === "anonymous" || !unread ? "text-fg-2" : "text-fg-1";
}

interface NotificationRowProps {
  notification: Notification;
  /// 相对时间的「现在」
  now: number;
  /// 点开这一行：标为已读（不阻止跳转）
  onOpen: (notification: Notification) => void;
}

/// 提醒列表的一行（简报 §3.19）：未读圆点 · 触发人头像 · 三层（谁做了什么 · 时间 / 摘录 / 在哪）。整行是链接，去评论
/// 永久链接；对象 key 为空时是按钮（没有去处，点了只标已读）。未读行浅底、已读行文字整体降一级。中键（新标签页打开）
/// 也算打开；指针移入、聚焦只会让 Link 预取，不算
export function NotificationRow({ notification, now, onOpen }: NotificationRowProps) {
  const view = actorOf(notification);
  const excerpt = excerptOf(notification);
  const context = contextLabel(notification.subject);
  const href = notificationHref(notification);
  const unread = !notification.read;
  const className = cn(
    "grid min-h-18 grid-cols-[8px_36px_minmax(0,1fr)] items-start gap-x-3 py-4 transition-colors hover:bg-bg-sunken max-[880px]:grid-cols-[8px_32px_minmax(0,1fr)]",
    unread && "bg-paper-50",
  );
  const body = (
    <>
      <span className="flex h-5 items-center" aria-hidden="true">
        {unread && <span className="size-1.5 rounded-full bg-clay-500" />}
      </span>
      <AuthorAvatar view={view} size={36} mobileSize={32} />
      <span className="flex min-w-0 flex-col gap-1">
        <span className="text-sm leading-5">
          {unread && <span className="sr-only">未读，</span>}
          <span className={cn("font-medium", nameColor(view, unread))}>{actorName(view)}</span>
          <span className={cn("ml-1", unread ? "text-fg-2" : "text-fg-3")}>
            {actionLabel(notification.type)}
          </span>
          <span className="text-fg-3"> · </span>
          <time
            dateTime={notification.createdAt}
            title={formatDateTime(notification.createdAt)}
            className="text-fg-3"
          >
            {formatRelative(notification.createdAt, now)}
          </time>
        </span>
        {excerpt?.kind === "deleted" && (
          <span className="text-sm leading-5 text-fg-3 italic">该评论已删除</span>
        )}
        {excerpt?.kind === "text" && (
          <span
            className={cn(
              "line-clamp-2 max-w-[72ch] text-sm leading-5",
              unread ? "text-fg-2" : "text-fg-3",
            )}
          >
            {excerpt.text}
          </span>
        )}
        {/* 一行截断交给 CSS：按渲染出的文字截，不会截在标签中间 */}
        {context && (
          <span className="truncate text-xs leading-4 text-fg-3">
            {context.kind === "paper" ? (
              <>
                《<RichInlineText text={context.title} />
                》的讨论
              </>
            ) : (
              context.text
            )}
          </span>
        )}
      </span>
    </>
  );
  if (!href) {
    return (
      <button
        type="button"
        data-unread={unread}
        onClick={() => onOpen(notification)}
        className={cn(className, "w-full text-left")}
      >
        {body}
      </button>
    );
  }
  return (
    <Link
      href={href}
      data-unread={unread}
      onClick={() => onOpen(notification)}
      onAuxClick={(event) => {
        if (event.button === 1) {
          onOpen(notification);
        }
      }}
      className={className}
    >
      {body}
    </Link>
  );
}
