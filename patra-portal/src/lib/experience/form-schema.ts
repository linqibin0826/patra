import { z } from "zod";
import { parseDateInput } from "@/lib/date-input";
import { limitState, measure } from "@/lib/discussion/limits";
import {
  ARTICLE_TYPES,
  type ArticleType,
  FINAL_OUTCOMES,
  FIRST_DECISIONS,
  type FinalOutcome,
  type FirstDecision,
} from "@/lib/experience/labels";
import {
  BODY_TOO_LONG,
  BODY_TOO_MANY_BYTES,
  CONSTRAINT_HINTS,
  DATE_MESSAGES,
  REQUIRED_MESSAGES,
  ROUNDS_MESSAGE,
  SCORE_MESSAGE,
} from "@/lib/experience/messages";
import { constraintsFor, MIN_DATE } from "@/lib/experience/rules";
import type { Experience, ExperienceRequest } from "@/lib/interaction/experience-schema";

/// 经验正文的上限（后端）：2,000 字素、8,000 字节
export const EXPERIENCE_BODY_LIMITS = { graphemes: 2000, bytes: 8000 } as const;

/// 表单值：全是字符串形态（日期 `yyyy-MM-dd`、单选为枚举值或空串、是否题 `yes` / `no` / 空串），提交时
/// `toRequest()` 转成请求体
export interface ExperienceFormValues {
  submittedOn: string;
  firstDecisionOn: string;
  finalDecisionOn: string;
  firstDecision: FirstDecision | "";
  finalOutcome: FinalOutcome | "";
  articleType: ArticleType | "";
  reviewRounds: string;
  reviewQuality: string;
  editorCommunication: string;
  apcPaid: "" | "yes" | "no";
  previouslyRejected: "" | "yes" | "no";
  body: string;
  anonymous: boolean;
}

export const EMPTY_FORM: ExperienceFormValues = {
  submittedOn: "",
  firstDecisionOn: "",
  finalDecisionOn: "",
  firstDecision: "",
  finalOutcome: "",
  articleType: "",
  reviewRounds: "",
  reviewQuality: "",
  editorCommunication: "",
  apcPaid: "",
  previouslyRejected: "",
  body: "",
  anonymous: false,
};

const yesNo = (value: boolean | null): "" | "yes" | "no" =>
  value === null ? "" : value ? "yes" : "no";
const numberText = (value: number | null): string => (value === null ? "" : String(value));

/// 编辑时的初值
export function fromExperience(experience: Experience): ExperienceFormValues {
  return {
    submittedOn: experience.submittedOn,
    firstDecisionOn: experience.firstDecisionOn,
    finalDecisionOn: experience.finalDecisionOn ?? "",
    firstDecision: experience.firstDecision,
    finalOutcome: experience.finalOutcome,
    articleType: experience.articleType,
    reviewRounds: numberText(experience.reviewRounds),
    reviewQuality: numberText(experience.reviewQuality),
    editorCommunication: numberText(experience.editorCommunication),
    apcPaid: yesNo(experience.apcPaid),
    previouslyRejected: yesNo(experience.previouslyRejected),
    body: experience.body ?? "",
    anonymous: experience.anonymous,
  };
}

const INTEGER = /^\d+$/;
const YES_NO = z.enum(["", "yes", "no"]);

/// 表单校验：错误消息就是界面文案（简报 §4.3）。单字段规则先查，跨字段规则只在相关字段本身合格时再查，
/// 问题挂在后一个字段上；一致性规则与表单联动共用 `constraintsFor`
export function experienceFormSchema(today: string) {
  const dateField = (field: string) =>
    z.string().superRefine((value, ctx) => {
      if (value === "") {
        if (REQUIRED_MESSAGES[field]) {
          ctx.addIssue({ code: "custom", message: REQUIRED_MESSAGES[field] });
        }
        return;
      }
      if (parseDateInput(value) !== value) {
        ctx.addIssue({ code: "custom", message: DATE_MESSAGES.format });
      } else if (value > today) {
        ctx.addIssue({ code: "custom", message: DATE_MESSAGES.future });
      } else if (value < MIN_DATE) {
        ctx.addIssue({ code: "custom", message: DATE_MESSAGES.tooEarly });
      }
    });
  /// 必选的单选：值是枚举或空串（还没选），空串报必填
  const choice = <const T extends readonly [string, ...string[]]>(field: string, values: T) =>
    z
      .union([z.enum(values), z.literal("")])
      .refine((value) => value !== "", { message: REQUIRED_MESSAGES[field] });
  const score = z
    .string()
    .refine(
      (value) => value === "" || (INTEGER.test(value) && Number(value) >= 1 && Number(value) <= 5),
      { message: SCORE_MESSAGE },
    );
  return z
    .object({
      submittedOn: dateField("submittedOn"),
      firstDecisionOn: dateField("firstDecisionOn"),
      finalDecisionOn: dateField("finalDecisionOn"),
      firstDecision: choice("firstDecision", FIRST_DECISIONS),
      finalOutcome: choice("finalOutcome", FINAL_OUTCOMES),
      articleType: choice("articleType", ARTICLE_TYPES),
      reviewRounds: z
        .string()
        .refine(
          (value) =>
            value === "" || (INTEGER.test(value) && Number(value) >= 1 && Number(value) <= 10),
          { message: ROUNDS_MESSAGE },
        ),
      reviewQuality: score,
      editorCommunication: score,
      apcPaid: YES_NO,
      previouslyRejected: YES_NO,
      body: z.string().superRefine((value, ctx) => {
        const state = limitState(measure(value), EXPERIENCE_BODY_LIMITS);
        if (state === "over-graphemes") {
          ctx.addIssue({ code: "custom", message: BODY_TOO_LONG });
        } else if (state === "over-bytes") {
          ctx.addIssue({ code: "custom", message: BODY_TOO_MANY_BYTES });
        }
      }),
      anonymous: z.boolean(),
    })
    .superRefine((values, ctx) => {
      const valid = (date: string) => date !== "" && date <= today && date >= MIN_DATE;
      if (
        valid(values.submittedOn) &&
        valid(values.firstDecisionOn) &&
        values.firstDecisionOn < values.submittedOn
      ) {
        ctx.addIssue({
          code: "custom",
          path: ["firstDecisionOn"],
          message: DATE_MESSAGES.beforeSubmission,
        });
      }
      if (
        valid(values.firstDecisionOn) &&
        valid(values.finalDecisionOn) &&
        values.finalDecisionOn < values.firstDecisionOn
      ) {
        ctx.addIssue({
          code: "custom",
          path: ["finalDecisionOn"],
          message: DATE_MESSAGES.beforeFirstDecision,
        });
      }
      const constraints = constraintsFor(values.firstDecision, values.finalOutcome);
      if (
        constraints.lockedFinalOutcome &&
        values.finalOutcome !== "" &&
        values.finalOutcome !== constraints.lockedFinalOutcome
      ) {
        ctx.addIssue({
          code: "custom",
          path: ["finalOutcome"],
          message: CONSTRAINT_HINTS.finalOutcome,
        });
      }
      if (values.finalDecisionOn !== "" && constraints.finalDecisionOn !== "enabled") {
        ctx.addIssue({
          code: "custom",
          path: ["finalDecisionOn"],
          message:
            constraints.finalDecisionOn === "terminal"
              ? CONSTRAINT_HINTS.finalDateTerminal
              : CONSTRAINT_HINTS.finalDateInProgress,
        });
      }
      if (values.reviewRounds !== "" && INTEGER.test(values.reviewRounds)) {
        if (constraints.reviewRounds === "none") {
          ctx.addIssue({
            code: "custom",
            path: ["reviewRounds"],
            message: CONSTRAINT_HINTS.notReviewed,
          });
        } else if (constraints.reviewRounds === "one" && values.reviewRounds !== "1") {
          ctx.addIssue({
            code: "custom",
            path: ["reviewRounds"],
            message: CONSTRAINT_HINTS.oneRound,
          });
        }
      }
      if (values.reviewQuality !== "" && constraints.reviewQuality === "none") {
        ctx.addIssue({
          code: "custom",
          path: ["reviewQuality"],
          message: CONSTRAINT_HINTS.notReviewed,
        });
      }
    });
}

const orNull = (value: string): string | null => (value === "" ? null : value);
const toNumber = (value: string): number | null => (value === "" ? null : Number(value));
const toBoolean = (value: "" | "yes" | "no"): boolean | null =>
  value === "" ? null : value === "yes";

/// 表单值 → 请求体：选填没填一律 `null`（不发 `false` / `0`，PAP-122 交接 3.1）；`anonymous` 只在新建时带。
/// 只在校验通过后调用，必填项此时都不是空串
export function toRequest(
  values: ExperienceFormValues,
  mode: "create" | "edit",
): ExperienceRequest {
  const body = values.body.trim();
  const request: ExperienceRequest = {
    submittedOn: values.submittedOn,
    firstDecisionOn: values.firstDecisionOn,
    firstDecision: values.firstDecision as FirstDecision,
    finalOutcome: values.finalOutcome as FinalOutcome,
    finalDecisionOn: orNull(values.finalDecisionOn),
    articleType: values.articleType as ArticleType,
    reviewRounds: toNumber(values.reviewRounds),
    reviewQuality: toNumber(values.reviewQuality),
    editorCommunication: toNumber(values.editorCommunication),
    apcPaid: toBoolean(values.apcPaid),
    previouslyRejected: toBoolean(values.previouslyRejected),
    body: body === "" ? null : body,
  };
  return mode === "create" ? { ...request, anonymous: values.anonymous } : request;
}
