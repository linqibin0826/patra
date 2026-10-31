"use client";

import { FileText, PenLine } from "lucide-react";
import Link from "next/link";
import { AuthorAvatar } from "@/components/discussion/AuthorIdentity";
import { RichInlineText } from "@/components/portal/RichInlineText";
import type { AuthorView } from "@/lib/discussion/author";
import { formatDateTime, formatGroupedTime } from "@/lib/format-time";
import type { Notification } from "@/lib/notification/inbox-schema";
import {
  actionLabel,
  actorName,
  actorOf,
  bodyOf,
  contextLabel,
  helpfulMore,
  notificationHref,
} from "@/lib/notifications/row";
import { cn } from "@/lib/utils";

/// 触发人名字的颜色（简报 §3.20）：匿名作者 `fg-2`、已注销用户 `fg-3`、其余 `fg-1`；已读整体降一级（不低于 `fg-3`）
function nameColor(view: AuthorView, unread: boolean): string {
  if (view.kind === "deleted" || (view.kind === "anonymous" && !unread)) {
    return "text-fg-3";
  }
  return view.kind === "anonymous" || !unread ? "text-fg-2" : "text-fg-1";
}

interface NotificationRowProps {
  notification: Notification;
  /// 相对时间与分组的「现在」
  now: number;
  /// 点开这一行：标为已读（不阻止跳转）
  onOpen: (notification: Notification) => void;
  /// 推送来的新条目：浅陶土底 2 秒淡出（减弱动效时没有，v0.16 简报 §3.4）
  fresh?: boolean;
}

/// 提醒列表的一行（spec 2026-10-28-notification-inbox-redesign 4.3，画布 `DirA` / `DirA-M`）：头像 · 三层（谁做了什么 ·
/// 时间靠右 / 对方的话当主角 / 带图标的「在哪」）。有帮助的合并条目（v0.16 简报 §3.2）同一结构：头像是最近一位点的人，
/// 2 人起右下角压 `+N−1`；正文是你自己那条内容，颜色不随未读加深。未读在行左侧页边距里一个圆点、名字加粗，不铺浅底；已读整行降一级。
/// 整行是链接，去评论永久链接；对象 key 为空时是按钮（没有去处，点了只标已读）。中键（新标签页打开）也算打开；
/// 指针移入、聚焦只会让 Link 预取，不算
export function NotificationRow({
  notification,
  now,
  onOpen,
  fresh = false,
}: NotificationRowProps) {
  const view = actorOf(notification);
  const body = bodyOf(notification);
  const more = helpfulMore(notification);
  const context = contextLabel(notification.subject);
  const href = notificationHref(notification);
  const unread = !notification.read;
  const className = cn(
    "relative grid grid-cols-[32px_minmax(0,1fr)] items-start gap-x-3 border-border-subtle border-b py-4 transition-colors hover:bg-bg-sunken max-[880px]:gap-x-2.5 max-[880px]:py-3.5",
    fresh && "anim-fresh",
  );
  const content = (
    <>
      {unread && (
        <span
          aria-hidden="true"
          className="absolute top-7 -left-4 size-1.5 rounded-full bg-clay-500 max-[880px]:top-6.5 max-[880px]:-left-3"
        />
      )}
      <span className="relative size-8">
        <AuthorAvatar view={view} size={32} mobileSize={32} />
        {more && (
          <span
            aria-hidden="true"
            className="absolute -right-1.5 -bottom-1 h-4 min-w-4.5 rounded-full bg-paper-50 px-1 text-center font-mono text-3xs leading-4 font-medium text-fg-2 tabular-nums ring-1 ring-border-default"
          >
            {more}
          </span>
        )}
      </span>
      <span className="flex min-w-0 flex-col gap-1.5">
        <span className="flex items-baseline gap-1.5 text-sm leading-5">
          {unread && <span className="sr-only">未读，</span>}
          <span
            className={cn(
              "shrink-0",
              unread ? "font-semibold" : "font-medium",
              nameColor(view, unread),
            )}
          >
            {actorName(view)}
          </span>
          <span className="min-w-0 truncate text-fg-3">{actionLabel(notification)}</span>
          <time
            dateTime={notification.occurredAt}
            title={formatDateTime(notification.occurredAt)}
            className="ml-auto shrink-0 text-fg-3 tabular-nums"
          >
            {formatGroupedTime(notification.occurredAt, now)}
          </time>
        </span>
        {body && (
          <span
            className={cn(
              "line-clamp-2 max-w-160 text-base leading-6",
              unread && !body.muted ? "text-fg-1" : "text-fg-2",
            )}
          >
            {body.text}
          </span>
        )}
        {context && (
          <span className="flex min-w-0 items-center gap-1.5 text-fg-3 text-xs leading-4">
            {context.kind === "text" && context.post ? (
              <PenLine aria-hidden="true" className="size-3.25 shrink-0" strokeWidth={1.75} />
            ) : (
              <FileText aria-hidden="true" className="size-3.25 shrink-0" strokeWidth={1.75} />
            )}
            {/* 一行截断交给 CSS：按渲染出的文字截，不会截在标签中间 */}
            <span className="min-w-0 truncate">
              {context.kind === "paper" ? <RichInlineText text={context.title} /> : context.text}
            </span>
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
        {content}
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
      {content}
    </Link>
  );
}
