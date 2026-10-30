import type { HelpfulFailure } from "@/lib/interaction/helpful-actions";
import type { HelpfulTarget } from "@/lib/interaction/helpful-schema";

/// 点有帮助失败时按钮旁行内提示的文案（简报 v0.15 §3.2）。给自己点在界面上到不了，按网络失败处理；
/// 未验证的「重新发送验证邮件」由按钮另放
export function helpfulFailureMessage(
  reason: HelpfulFailure["reason"],
  context: { on: boolean; type: HelpfulTarget["type"] },
): string {
  switch (reason) {
    case "daily-limit":
      return "今天已经点了 300 次有帮助，明天再来吧";
    case "account-restricted":
      return "账号状态异常，暂时不能点有帮助";
    case "email-unverified":
      return "验证邮箱后才能点有帮助";
    case "gone":
      return context.type === "comment" ? "这条评论已被删除" : "这条投稿经验已被删除";
    default:
      return context.on ? "没有标记成功，请稍后再试" : "没有取消成功，请稍后再试";
  }
}
