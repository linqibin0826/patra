import { makeExperience } from "@tests/fixtures/experience";
import { describe, expect, it } from "vitest";
import {
  EMPTY_FORM,
  type ExperienceFormValues,
  experienceFormSchema,
  fromExperience,
  toRequest,
} from "@/lib/experience/form-schema";

const TODAY = "2026-10-28";

const FILLED: ExperienceFormValues = {
  ...EMPTY_FORM,
  submittedOn: "2026-03-02",
  firstDecisionOn: "2026-04-13",
  firstDecision: "MAJOR_REVISION",
  finalOutcome: "ACCEPTED",
  articleType: "ORIGINAL_RESEARCH",
};

/// 校验后 `{字段: 第一条文案}`
function errorsOf(values: ExperienceFormValues): Record<string, string> {
  const result = experienceFormSchema(TODAY).safeParse(values);
  if (result.success) {
    return {};
  }
  const out: Record<string, string> = {};
  for (const issue of result.error.issues) {
    const field = String(issue.path[0]);
    out[field] ??= issue.message;
  }
  return out;
}

describe("experienceFormSchema：必填（简报 §4.3）", () => {
  it("空表五个必填各自的文案", () => {
    expect(errorsOf(EMPTY_FORM)).toEqual({
      submittedOn: "请填写投稿日期",
      firstDecisionOn: "请填写首轮决定日期",
      firstDecision: "请选择首轮结果",
      finalOutcome: "请选择最终结果",
      articleType: "请选择文章类型",
    });
  });

  it("只填必填可以通过", () => {
    expect(errorsOf(FILLED)).toEqual({});
  });
});

describe("experienceFormSchema：日期", () => {
  it("晚于今天", () => {
    expect(errorsOf({ ...FILLED, firstDecisionOn: "2026-10-29" })).toEqual({
      firstDecisionOn: "日期不能晚于今天",
    });
  });

  it("早于 1990 年", () => {
    expect(errorsOf({ ...FILLED, submittedOn: "1989-12-31" })).toEqual({
      submittedOn: "日期不能早于 1990 年",
    });
  });

  it("首轮决定早于投稿", () => {
    expect(errorsOf({ ...FILLED, firstDecisionOn: "2026-03-01" })).toEqual({
      firstDecisionOn: "首轮决定日期不能早于投稿日期",
    });
  });

  it("最终决定早于首轮决定", () => {
    expect(errorsOf({ ...FILLED, finalDecisionOn: "2026-04-12" })).toEqual({
      finalDecisionOn: "最终决定日期不能早于首轮决定日期",
    });
  });

  it("认不出的写法报格式错误", () => {
    expect(errorsOf({ ...FILLED, submittedOn: "2026/9/31" })).toEqual({
      submittedOn: "日期写成 2026-08-14 这样",
    });
  });

  it("同一天可以", () => {
    expect(
      errorsOf({ ...FILLED, firstDecisionOn: "2026-03-02", finalDecisionOn: "2026-03-02" }),
    ).toEqual({});
  });
});

describe("experienceFormSchema：选填的范围与一致性", () => {
  it.each(["0", "11", "1.5", "abc"])("审稿轮数 %s 不合格", (reviewRounds) => {
    expect(errorsOf({ ...FILLED, reviewRounds })).toEqual({
      reviewRounds: "审稿轮数填 1 到 10 之间的整数",
    });
  });

  it("正文超过 2,000 字", () => {
    expect(errorsOf({ ...FILLED, body: "字".repeat(2001) })).toEqual({ body: "正文最多 2,000 字" });
  });

  it("直接拒稿却选了录用", () => {
    expect(errorsOf({ ...FILLED, firstDecision: "DESK_REJECT", finalOutcome: "ACCEPTED" })).toEqual(
      { finalOutcome: "首轮已是最终结果：直接拒稿、审后拒稿最终只能是拒稿，首轮录用只能是录用" },
    );
  });

  it("进行中还填了最终决定日期", () => {
    expect(
      errorsOf({ ...FILLED, finalOutcome: "IN_PROGRESS", finalDecisionOn: "2026-06-01" }),
    ).toEqual({ finalDecisionOn: "稿件还在进行中，不用填最终决定日期" });
  });

  it("审后拒稿的审稿轮数只能是 1", () => {
    expect(
      errorsOf({
        ...FILLED,
        firstDecision: "REJECT_AFTER_REVIEW",
        finalOutcome: "REJECTED",
        reviewRounds: "2",
      }),
    ).toEqual({ reviewRounds: "首轮即结束，审稿只有 1 轮" });
  });
});

describe("toRequest：选填没填发 null（PAP-122 交接 3.1）", () => {
  it("只填必填：选填严格是 null，不是 false / 0", () => {
    const request = toRequest(FILLED, "create");
    expect(request.finalDecisionOn).toBeNull();
    expect(request.reviewRounds).toBeNull();
    expect(request.reviewQuality).toBeNull();
    expect(request.editorCommunication).toBeNull();
    expect(request.apcPaid).toBeNull();
    expect(request.previouslyRejected).toBeNull();
    expect(request.body).toBeNull();
    expect(request.anonymous).toBe(false);
  });

  it("选填转成数字与布尔，正文去首尾空白", () => {
    const request = toRequest(
      {
        ...FILLED,
        finalDecisionOn: "2026-06-01",
        reviewRounds: "2",
        reviewQuality: "4",
        editorCommunication: "3",
        apcPaid: "yes",
        previouslyRejected: "no",
        body: "  经历  ",
        anonymous: true,
      },
      "create",
    );
    expect(request).toMatchObject({
      finalDecisionOn: "2026-06-01",
      reviewRounds: 2,
      reviewQuality: 4,
      editorCommunication: 3,
      apcPaid: true,
      previouslyRejected: false,
      body: "经历",
      anonymous: true,
    });
  });

  it("只有空白的正文是 null", () => {
    expect(toRequest({ ...FILLED, body: " \n " }, "create").body).toBeNull();
  });

  it("编辑不带 anonymous", () => {
    expect("anonymous" in toRequest({ ...FILLED, anonymous: true }, "edit")).toBe(false);
  });

  it("fromExperience 往返不变", () => {
    const experience = makeExperience({
      finalDecisionOn: "2026-06-01",
      reviewRounds: 2,
      apcPaid: false,
      previouslyRejected: null,
      body: "经历",
    });
    const { anonymous: _, ...request } = toRequest(fromExperience(experience), "create");
    expect(request).toEqual({
      submittedOn: experience.submittedOn,
      firstDecisionOn: experience.firstDecisionOn,
      firstDecision: experience.firstDecision,
      finalOutcome: experience.finalOutcome,
      finalDecisionOn: "2026-06-01",
      articleType: experience.articleType,
      reviewRounds: 2,
      reviewQuality: experience.reviewQuality,
      editorCommunication: experience.editorCommunication,
      apcPaid: false,
      previouslyRejected: null,
      body: "经历",
    });
  });
});
