import { type AuthorView, authorView } from "@/lib/discussion/author";
import type {
  Notification,
  NotificationSubject,
  NotificationTab,
  NotificationType,
} from "@/lib/interaction/notification-schema";

/// 提醒行的触发人：匿名作者是中性头像（接口里没有他的用户 ID）；其余与评论署名同一套规则（已注销、主页不可见、可见）
export function actorOf(notification: Notification): AuthorView {
  if (notification.actorAnonymous) {
    return { kind: "anonymous", self: false };
  }
  return authorView({ author: notification.actor, anonymous: false, mine: false });
}

/// 触发人的名字（整行是链接，名字不另链主页）
export function actorName(view: AuthorView): string {
  switch (view.kind) {
    case "anonymous":
      return "匿名作者";
    case "deleted":
      return "已注销用户";
    default:
      return view.card.displayName ?? "";
  }
}

/// 「在哪」由下行说明，动作只写最短的（spec 2026-10-28-notification-inbox-redesign 4.3）
const ACTIONS: Record<NotificationType, string> = {
  COMMENT_REPLY: "回复了你",
  POST_COMMENT: "评论了你的投稿经验",
  MENTION: "@ 了你",
};

/// 上行的动作
export function actionLabel(type: NotificationType): string {
  return ACTIONS[type];
}

/// 这类提醒在哪个标签：@ 归「@ 我的」，回复与评论经验归「回复我的」
export function tabOf(type: NotificationType): NotificationTab {
  return type === "MENTION" ? "mentions" : "replies";
}

/// 正文：后端给的纯文本摘录，被截断时末尾加「…」；对象已不可用（文献下线）时没有摘录、不渲染
export function excerptOf(notification: Notification): string | null {
  if (notification.excerpt === null) {
    return null;
  }
  return notification.excerptTruncated ? `${notification.excerpt}…` : notification.excerpt;
}

/// `2026-03` → `2026 年 3 月投稿`
function submittedLabel(month: string): string {
  const [year, mm] = month.split("-");
  return `${year} 年 ${Number(mm)} 月投稿`;
}

/// 下行的内容：文献交出原样的标题（PubMed 标题带内联标记，由组件按白名单渲染），其余是纯文本；`post` 决定图标
export type RowContext =
  | { kind: "paper"; title: string }
  | { kind: "text"; text: string; post: boolean };

/// 下行：在哪里（spec 2026-10-28-notification-inbox-redesign 4.3）。没有对象时不渲染
export function contextLabel(subject: NotificationSubject | null): RowContext | null {
  if (!subject) {
    return null;
  }
  if (subject.type === "publication") {
    return subject.title
      ? { kind: "paper", title: subject.title }
      : { kind: "text", text: "一篇文献的讨论", post: false };
  }
  const journal = subject.abbreviation ?? subject.journal ?? "";
  if (!subject.mine) {
    return { kind: "text", text: `${journal} 的一条投稿经验`, post: true };
  }
  return {
    kind: "text",
    text: [
      "你的投稿经验",
      journal,
      subject.submittedMonth && submittedLabel(subject.submittedMonth),
    ]
      .filter(Boolean)
      .join(" · "),
    post: true,
  };
}

/// 点开去哪：评论永久链接（单楼视图）；`key` 为空时不是链接
export function notificationHref(notification: Notification): string | null {
  const subject = notification.subject;
  if (!subject?.key) {
    return null;
  }
  const base = subject.type === "publication" ? `/papers/${subject.key}` : `/posts/${subject.key}`;
  return `${base}?comment=${notification.commentId}`;
}
