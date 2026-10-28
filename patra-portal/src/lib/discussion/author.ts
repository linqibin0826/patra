import type { Card, Comment } from "@/lib/interaction/discussion-schema";

/// 署名怎么画（spec 6.4 署名表、后端 5.7 / 5.8）：
/// - `anonymous`：对象的匿名所有者；`self` 是本人在看（显示「匿名作者（你）」）
/// - `deleted`：已注销，或 identity 省略了这个人
/// - `hidden`：主页对访客不可见，只显示昵称与默认头像，不链主页、没有 `@用户名`
/// - `visible`：昵称链主页 + `@用户名`
export type AuthorView =
  | { kind: "anonymous"; self: boolean }
  | { kind: "deleted" }
  | { kind: "hidden"; card: Card }
  | { kind: "visible"; card: Card };

export function authorView(input: {
  author: Card | null;
  anonymous: boolean;
  mine: boolean;
}): AuthorView {
  if (input.anonymous) {
    return { kind: "anonymous", self: input.mine };
  }
  const card = input.author;
  if (!card || card.state === "DELETED" || !card.displayName) {
    return { kind: "deleted" };
  }
  return card.username ? { kind: "visible", card } : { kind: "hidden", card };
}

/// 自己回复自己（被回复的那条与这条是同一个作者，匿名作者也算）：当作补充，不显示「回复 @X」与「查看对话」
/// （2026-10-28 用户定）
export function isSelfReply(comment: Comment): boolean {
  const replyTo = comment.replyTo;
  if (!replyTo || replyTo.deleted) {
    return false;
  }
  if (replyTo.anonymous) {
    return comment.anonymous;
  }
  return (
    replyTo.author !== null &&
    comment.author !== null &&
    !comment.anonymous &&
    replyTo.author.userId === comment.author.userId
  );
}

/// 「回复 @X」写什么（简报 §3.3、§3.20）：被回复的已删除 / 匿名 / 已注销各有写法；主页不可见写 `@昵称`、不链接
export function replyToLabel(
  replyTo: Comment["replyTo"],
): { text: string; href: string | null } | null {
  if (!replyTo) {
    return null;
  }
  if (replyTo.deleted) {
    return { text: "回复已删除的评论", href: null };
  }
  if (replyTo.anonymous) {
    return { text: "回复 匿名作者", href: null };
  }
  const view = authorView({ author: replyTo.author, anonymous: false, mine: false });
  switch (view.kind) {
    case "visible":
      return { text: `回复 @${view.card.username}`, href: `/u/${view.card.username}` };
    case "hidden":
      return { text: `回复 @${view.card.displayName}`, href: null };
    default:
      return { text: "回复 已注销用户", href: null };
  }
}
