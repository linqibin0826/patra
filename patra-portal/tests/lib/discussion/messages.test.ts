import { describe, expect, it } from "vitest";
import { commentFailureMessage } from "@/lib/discussion/messages";

describe("commentFailureMessage（简报 §4.2）", () => {
  it.each([
    ["too-frequent", "发得太快了，请隔 10 秒再发。"],
    ["daily-limit", "今天已经发表了 100 条评论，明天再来吧。"],
    ["account-restricted", "账号状态异常，暂时不能发表。"],
    ["email-unverified", "验证邮箱后才能发表评论。"],
    ["too-many-mentions", "一条评论最多 @ 10 人。"],
    ["too-long", "内容太长了，请删减一些。"],
    ["reply-target-gone", "要回复的评论已被删除，内容还在输入框里。"],
    ["subject-gone", "这条投稿经验已被删除，不能再回复。"],
  ] as const)("%s", (reason, message) => {
    expect(commentFailureMessage(reason, "publish")).toBe(message);
  });

  it.each([
    ["publish", "没有发表成功，请稍后再试。"],
    ["edit", "没有保存成功，请稍后再试"],
    ["delete", "没有删除成功，请稍后再试"],
  ] as const)("unavailable：%s", (action, message) => {
    expect(commentFailureMessage("unavailable", action)).toBe(message);
    for (const reason of ["empty", "invalid-character", "forbidden", "comment-gone"] as const) {
      expect(commentFailureMessage(reason, action)).toBe(message);
    }
  });
});
