import type { CommentFailure } from "@/lib/interaction/discussion-actions";

/// 写评论失败的提示条文案（简报 §4.2）。前端正常操作到不了的（空正文、非法字符、改别人的、评论已不在）按「稍后再试」
const FAILURE_MESSAGES: Partial<Record<CommentFailure["reason"], string>> = {
  "too-frequent": "发得太快了，请隔 10 秒再发。",
  "daily-limit": "今天已经发表了 100 条评论，明天再来吧。",
  "account-restricted": "账号状态异常，暂时不能发表。",
  "email-unverified": "验证邮箱后才能发表评论。",
  "too-many-mentions": "一条评论最多 @ 10 人。",
  "too-long": "内容太长了，请删减一些。",
  "reply-target-gone": "要回复的评论已被删除，内容还在输入框里。",
  "subject-gone": "这条投稿经验已被删除，不能再回复。",
};

const RETRY_LATER = {
  publish: "没有发表成功，请稍后再试。",
  edit: "没有保存成功，请稍后再试",
  delete: "没有删除成功，请稍后再试",
} as const;

/// 一次写操作失败时提示条里写什么
export function commentFailureMessage(
  reason: CommentFailure["reason"],
  action: keyof typeof RETRY_LATER,
): string {
  return FAILURE_MESSAGES[reason] ?? RETRY_LATER[action];
}

/// 编辑器的其余固定文案（简报 §3.5、§3.6、§3.12、§4.2）
export const COMPOSER_COPY = {
  previewEmpty: "没有可以预览的内容",
  discard: "放弃正在写的内容？",
  discardReply: "放弃正在写的回复？",
  sessionExpired: "登录已过期，去重新登录？已写的内容会丢失。",
  markdownHint: "支持 Markdown：粗体、斜体、引用、列表、行内代码、链接",
  markdownHintShort: "支持 Markdown",
  experienceHint: "支持 Markdown；这里不能 @ 人",
  tooManyMentions: "一条评论最多 @ 10 人",
  tooLong: "内容太长了，请删减一些",
  mentionParticipants: "本讨论的参与者",
  mentionOthers: "其他用户",
  mentionHint: "输入用户名或昵称查找同行",
  mentionLoading: "正在查找…",
  mentionError: "暂时查不到用户，输入完整的用户名也能 @ 到对方。",
} as const;

/// 联想没有结果
export function mentionNotFound(query: string): string {
  return `没有找到「${query}」。输入完整的用户名也能 @ 到对方。`;
}

/// 计数超过字素上限时的说明：`评论最多 5,000 字` / `正文最多 2,000 字`
export function overLimitMessage(label: "评论" | "正文", limit: number): string {
  return `${label}最多 ${limit.toLocaleString("en-US")} 字`;
}
