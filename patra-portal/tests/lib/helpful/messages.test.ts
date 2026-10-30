import { describe, expect, it } from "vitest";
import { helpfulFailureMessage } from "@/lib/helpful/messages";

const COMMENT_ON = { on: true, type: "comment" } as const;

describe("helpfulFailureMessage", () => {
  it("网络失败分点与取消", () => {
    expect(helpfulFailureMessage("unavailable", COMMENT_ON)).toBe("没有标记成功，请稍后再试");
    expect(helpfulFailureMessage("unavailable", { on: false, type: "comment" })).toBe(
      "没有取消成功，请稍后再试",
    );
  });

  it("给自己点按网络失败处理", () => {
    expect(helpfulFailureMessage("self", COMMENT_ON)).toBe("没有标记成功，请稍后再试");
  });

  it("每日上限、账号异常、未验证", () => {
    expect(helpfulFailureMessage("daily-limit", COMMENT_ON)).toBe(
      "今天已经点了 300 次有帮助，明天再来吧",
    );
    expect(helpfulFailureMessage("account-restricted", COMMENT_ON)).toBe(
      "账号状态异常，暂时不能点有帮助",
    );
    expect(helpfulFailureMessage("email-unverified", COMMENT_ON)).toBe("验证邮箱后才能点有帮助");
  });

  it("目标已删除分评论与经验", () => {
    expect(helpfulFailureMessage("gone", COMMENT_ON)).toBe("这条评论已被删除");
    expect(helpfulFailureMessage("gone", { on: true, type: "post" })).toBe("这条投稿经验已被删除");
  });
});
