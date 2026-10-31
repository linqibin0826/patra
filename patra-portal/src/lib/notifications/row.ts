import { type AuthorView, authorView } from "@/lib/discussion/author";
import { formatOutcomeLine } from "@/lib/experience/format";
import type {
  Notification,
  NotificationSubject,
  NotificationTab,
  NotificationType,
} from "@/lib/notification/inbox-schema";

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

/// 「在哪」由下行说明，动作只写最短的（spec 2026-10-28-notification-inbox-redesign 4.3）；有帮助只说对象是评论还是投稿经验
const ACTIONS: Record<NotificationType, string> = {
  COMMENT_REPLY: "回复了你",
  POST_COMMENT: "评论了你的投稿经验",
  MENTION: "@ 了你",
  COMMENT_HELPFUL: "觉得你的评论有帮助",
  POST_HELPFUL: "觉得你的投稿经验有帮助",
};

/// 有帮助的两类
const isHelpful = (type: NotificationType) => type === "COMMENT_HELPFUL" || type === "POST_HELPFUL";

/// 上行名字之后的部分：有帮助 2 人起前面加「等 N 人」（名字是最近一位点的人，简报 §3.2）
export function actionLabel(notification: Notification): string {
  const action = ACTIONS[notification.type];
  return isHelpful(notification.type) && notification.actorCount > 1
    ? `等 ${notification.actorCount} 人${action}`
    : action;
}

/// 有帮助 2 人起头像右下角的人数角标 `+{N−1}`；其余为 null
export function helpfulMore(notification: Notification): string | null {
  return isHelpful(notification.type) && notification.actorCount > 1
    ? `+${notification.actorCount - 1}`
    : null;
}

/// 这类提醒在哪个标签：@ 归「@ 我的」，有帮助归「有帮助」，回复与评论经验归「回复我的」
export function tabOf(type: NotificationType): NotificationTab {
  if (isHelpful(type)) {
    return "helpful";
  }
  return type === "MENTION" ? "mentions" : "replies";
}

/// 正文：`muted` 为真时颜色不随未读加深（有帮助的正文是你自己那条内容，简报 §3.2）
export interface RowBody {
  text: string;
  muted: boolean;
}

/// 正文：后端给的纯文本摘录，被截断时末尾加「…」。有帮助点了没有正文的投稿经验时写结果行，同经验列表
/// （`ExperienceRow`）；对象已不可用（文献下线）、或什么都没有时不渲染
export function bodyOf(notification: Notification): RowBody | null {
  const muted = isHelpful(notification.type);
  if (notification.excerpt !== null) {
    const text = notification.excerptTruncated ? `${notification.excerpt}…` : notification.excerpt;
    return { text, muted };
  }
  const experience = notification.subject?.experience;
  if (notification.type === "POST_HELPFUL" && experience) {
    return { text: formatOutcomeLine(experience).replace("  →  ", " → "), muted };
  }
  return null;
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

/// 点开去哪：有评论时去评论永久链接（单楼视图），没有评论（有帮助点了经验）时去对象本身；`key` 为空时不是链接
export function notificationHref(notification: Notification): string | null {
  const subject = notification.subject;
  if (!subject?.key) {
    return null;
  }
  const base = subject.type === "publication" ? `/papers/${subject.key}` : `/posts/${subject.key}`;
  return notification.commentId ? `${base}?comment=${notification.commentId}` : base;
}
