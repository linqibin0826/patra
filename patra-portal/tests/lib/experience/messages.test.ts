import { describe, expect, it } from "vitest";
import {
  CONSTRAINT_HINTS,
  experienceFailureMessage,
  fieldErrorMessage,
} from "@/lib/experience/messages";

describe("fieldErrorMessage：服务端字段错误码 → 文案（spec 6.4）", () => {
  it.each([
    ["submittedOn", "REQUIRED", "请填写投稿日期"],
    ["firstDecisionOn", "REQUIRED", "请填写首轮决定日期"],
    ["firstDecision", "REQUIRED", "请选择首轮结果"],
    ["finalOutcome", "REQUIRED", "请选择最终结果"],
    ["articleType", "REQUIRED", "请选择文章类型"],
    ["firstDecisionOn", "FUTURE", "日期不能晚于今天"],
    ["submittedOn", "TOO_EARLY", "日期不能早于 1990 年"],
    ["firstDecisionOn", "BEFORE_SUBMISSION", "首轮决定日期不能早于投稿日期"],
    ["finalDecisionOn", "BEFORE_FIRST_DECISION", "最终决定日期不能早于首轮决定日期"],
    ["reviewRounds", "OUT_OF_RANGE", "审稿轮数填 1 到 10 之间的整数"],
    ["reviewQuality", "OUT_OF_RANGE", "评分是 1 到 5"],
    ["editorCommunication", "OUT_OF_RANGE", "评分是 1 到 5"],
    ["body", "TOO_LONG", "正文最多 2,000 字"],
    ["body", "INVALID_CHARACTER", "这一项的内容不对，请重新填写"],
    ["articleType", "INVALID", "这一项的内容不对，请重新填写"],
  ])("%s / %s", (field, code, text) => {
    expect(fieldErrorMessage(field, code)).toBe(text);
  });

  it("一致性错误用联动说明", () => {
    expect(fieldErrorMessage("finalOutcome", "INCONSISTENT")).toBe(CONSTRAINT_HINTS.finalOutcome);
    expect(fieldErrorMessage("reviewRounds", "INCONSISTENT")).toBe(CONSTRAINT_HINTS.oneRound);
    expect(fieldErrorMessage("reviewRounds", "NOT_APPLICABLE")).toBe(CONSTRAINT_HINTS.notReviewed);
    expect(fieldErrorMessage("reviewQuality", "NOT_APPLICABLE")).toBe(CONSTRAINT_HINTS.notReviewed);
    expect(fieldErrorMessage("finalDecisionOn", "NOT_APPLICABLE")).toBe(
      CONSTRAINT_HINTS.finalDateNotApplicable,
    );
  });

  it("重复投稿日期不在字段下重复（走底部提示条）", () => {
    expect(fieldErrorMessage("submittedOn", "DUPLICATE")).toBeNull();
  });

  it("认不出的码返回 null", () => {
    expect(fieldErrorMessage("submittedOn", "WHATEVER")).toBeNull();
    expect(fieldErrorMessage("unknown", "REQUIRED")).toBeNull();
    expect(fieldErrorMessage("body", null)).toBeNull();
  });
});

describe("experienceFailureMessage：写操作失败的提示条（spec 4.3）", () => {
  it("重复投稿日期带格式化的日期", () => {
    expect(experienceFailureMessage("duplicate", "create", "2026-03-02")).toBe(
      "你已经写过 2026 年 3 月 2 日投这本期刊的经验，同一天的投稿只能写一条。",
    );
  });

  it.each([
    ["daily-limit", "create", "今天已经发布了 10 条投稿经验，明天再来吧。"],
    ["account-restricted", "create", "账号状态异常，暂时不能发布。"],
    ["account-restricted", "edit", "账号状态异常，暂时不能保存。"],
    ["email-unverified", "create", "验证邮箱后才能写投稿经验。"],
    ["journal-gone", "create", "这本期刊已不在 Patra 的索引里，不能再写投稿经验。"],
    ["post-gone", "edit", "这条投稿经验已被删除。"],
    ["unavailable", "create", "没有发布成功，请稍后再试。"],
    ["unavailable", "edit", "没有保存成功，请稍后再试。"],
    ["unavailable", "delete", "没有删除成功，请稍后再试"],
    ["forbidden", "edit", "没有保存成功，请稍后再试。"],
    ["invalid-fields", "create", "没有发布成功，请稍后再试。"],
  ] as const)("%s / %s", (reason, mode, text) => {
    expect(experienceFailureMessage(reason, mode, "2026-03-02")).toBe(text);
  });
});
