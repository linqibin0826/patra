"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Calculator, Lock, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useId, useRef, useState } from "react";
import { Controller, type FieldPath, type PathValue, useForm } from "react-hook-form";
import { FormAlert } from "@/components/auth/FormAlert";
import { MarkdownEditor, useEditorMetrics } from "@/components/discussion/MarkdownEditor";
import {
  BoxField,
  ChoiceGroup,
  type ChoiceOption,
  FieldNote,
} from "@/components/experience/form-fields";
import { ResendVerificationButton } from "@/components/portal/ResendVerificationButton";
import { DatePicker } from "@/components/ui/date-picker";
import { Dialog } from "@/components/ui/dialog";
import { NumberField } from "@/components/ui/number-field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { authPageHref } from "@/lib/auth/next-path";
import { parseDateInput } from "@/lib/date-input";
import { COMPOSER_COPY } from "@/lib/discussion/messages";
import {
  EMPTY_FORM,
  EXPERIENCE_BODY_LIMITS,
  type ExperienceFormValues,
  experienceFormSchema,
  fromExperience,
  toRequest,
} from "@/lib/experience/form-schema";
import { formatDays } from "@/lib/experience/format";
import {
  ARTICLE_TYPE_LABELS,
  ARTICLE_TYPES,
  FINAL_OUTCOME_LABELS,
  FINAL_OUTCOMES,
  FIRST_DECISION_LABELS,
  FIRST_DECISIONS,
  type FinalOutcome,
  type FirstDecision,
} from "@/lib/experience/labels";
import {
  CONSTRAINT_HINTS,
  experienceFailureMessage,
  fieldErrorMessage,
} from "@/lib/experience/messages";
import { constraintsFor, daysBetween, MIN_DATE, todayInShanghai } from "@/lib/experience/rules";
import {
  type ExperienceFailure,
  editExperience,
  publishExperience,
} from "@/lib/interaction/experience-actions";
import type { Experience, PostDetail } from "@/lib/interaction/experience-schema";
import { orUnavailable } from "@/lib/or-unavailable";
import {
  btnDangerOutline,
  btnPrimary,
  btnSecondary,
  btnText,
  CHECK_INPUT,
  CHECK_MARK,
} from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

export type ExperienceFormProps =
  | {
      mode: "create";
      journalId: string;
      journalTitle: string;
      onDone: (detail: PostDetail) => void;
      onCancel: () => void;
    }
  | {
      mode: "edit";
      postId: string;
      journalTitle: string;
      initial: Experience;
      anonymous: boolean;
      onDone: (detail: PostDetail) => void;
      onCancel: () => void;
    };

const SCORES = ["1", "2", "3", "4", "5"] as const;
const scoreOptions = (disabled: boolean): ChoiceOption<(typeof SCORES)[number]>[] =>
  SCORES.map((value) => ({ value, label: value, disabled }));
const APC_OPTIONS = [
  { value: "yes", label: "付了" },
  { value: "no", label: "没付" },
] as const;
const TRANSFER_OPTIONS = [
  { value: "yes", label: "是" },
  { value: "no", label: "否" },
] as const;
const FORM_FIELDS = new Set<string>(Object.keys(EMPTY_FORM));
const ARTICLE_TYPE_ITEMS = ARTICLE_TYPES.map((value) => ({
  value,
  label: ARTICLE_TYPE_LABELS[value],
}));

/// 写不进去时表单底部的提示条
type Alert = { reason: ExperienceFailure["reason"]; existingPostId?: string | null };

const LEGEND = "float-left mb-1 w-full p-0 font-semibold text-fg-1 text-md";
const GRID = "grid grid-cols-2 gap-x-6 gap-y-4.5 max-[880px]:grid-cols-1";

/// 填写与编辑投稿经验（简报 §3.15、spec 6.3）。期刊页上原地展开新建、经验页上原地切换编辑，同一个表单。
/// 首轮结果与最终结果的一致性规则做成联动：首轮终局时自动选定最终结果、禁用并清空不适用的字段；
/// 后端仍按规则校验，回来的字段错误落在对应字段下
export function ExperienceForm(props: ExperienceFormProps) {
  const { mode, journalTitle, onDone, onCancel } = props;
  const editing = mode === "edit";
  const ids = useId();
  const router = useRouter();
  const pathname = usePathname();
  const [today] = useState(() => todayInShanghai(Date.now()));
  const [submitting, setSubmitting] = useState(false);
  const [alert, setAlert] = useState<Alert | null>(null);
  const [confirm, setConfirm] = useState<"discard" | "signed-out" | null>(null);
  const form = useForm<ExperienceFormValues>({
    defaultValues: editing ? fromExperience(props.initial) : EMPTY_FORM,
    resolver: zodResolver(experienceFormSchema(today)),
    mode: "onBlur",
    reValidateMode: "onChange",
  });
  const { register, control, watch, setValue, setError, setFocus, formState, trigger } = form;
  const values = watch();
  const errors = formState.errors;
  // formState 是按读取订阅的代理：在渲染时读出来，回调里拿到的才是最新值
  const { isDirty, isSubmitted, touchedFields } = formState;
  const constraints = constraintsFor(values.firstDecision, values.finalOutcome);
  // 正文超出字素或字节上限时不能提交（编辑器底栏已经标红）
  const bodyBlocked = useEditorMetrics(values.body, EXPERIENCE_BODY_LIMITS, false).blocked;
  const bodyBox = useRef<HTMLTextAreaElement | null>(null);
  const id = (name: string) => `${ids}-${name}`;
  const loginHref = authPageHref("/login", pathname);

  /// 选择题改了值：已经校验过（失焦或提交过）时立即重新校验，免得旧错误挂着
  const choose = <K extends FieldPath<ExperienceFormValues>>(
    name: K,
    value: PathValue<ExperienceFormValues, K>,
  ) => {
    setValue(name, value, {
      shouldDirty: true,
      shouldValidate: isSubmitted || Boolean(touchedFields[name]),
    });
  };

  /// 首轮结果变了：按新规则设定依赖它的字段。从终局改回修改类时，清掉上一次自动填的值
  const chooseFirstDecision = (next: FirstDecision | "") => {
    const before = constraintsFor(values.firstDecision, values.finalOutcome);
    const after = constraintsFor(next, before.lockedFinalOutcome ? "" : values.finalOutcome);
    choose("firstDecision", next);
    if (after.lockedFinalOutcome) {
      choose("finalOutcome", after.lockedFinalOutcome);
    } else if (before.lockedFinalOutcome) {
      choose("finalOutcome", "");
    }
    if (after.finalDecisionOn !== "enabled") {
      choose("finalDecisionOn", "");
    }
    if (after.reviewRounds === "one") {
      choose("reviewRounds", "1");
    } else if (after.reviewRounds === "none" || before.reviewRounds !== "enabled") {
      choose("reviewRounds", "");
    }
    if (after.reviewQuality === "none") {
      choose("reviewQuality", "");
    }
  };

  const chooseFinalOutcome = (next: FinalOutcome | "") => {
    choose("finalOutcome", next);
    if (next === "IN_PROGRESS") {
      choose("finalDecisionOn", "");
    }
  };

  const fail = (failure: ExperienceFailure) => {
    if (failure.reason === "signed-out") {
      if (isDirty) {
        setConfirm("signed-out");
      } else {
        router.push(loginHref);
      }
      return;
    }
    if (failure.reason === "invalid-fields") {
      const placed = failure.fieldErrors.flatMap((fieldError) => {
        const message = fieldErrorMessage(fieldError.field, fieldError.code);
        return message && FORM_FIELDS.has(fieldError.field) ? [{ ...fieldError, message }] : [];
      });
      placed.forEach((fieldError, index) => {
        setError(
          fieldError.field as FieldPath<ExperienceFormValues>,
          { type: "server", message: fieldError.message },
          { shouldFocus: index === 0 && fieldError.field !== "body" },
        );
      });
      // 正文的编辑器没有注册给 RHF 的 ref，焦点手动给
      if (placed[0]?.field === "body") {
        bodyBox.current?.focus();
      }
      if (placed.length > 0) {
        return;
      }
    }
    setAlert({
      reason: failure.reason,
      existingPostId: failure.reason === "duplicate" ? failure.existingPostId : undefined,
    });
    if (failure.reason === "duplicate") {
      setFocus("submittedOn");
    }
  };

  const submit = form.handleSubmit(async (valid) => {
    setSubmitting(true);
    setAlert(null);
    setConfirm(null);
    const result = await orUnavailable(
      editing
        ? editExperience(props.postId, toRequest(valid, "edit"))
        : publishExperience(props.journalId, toRequest(valid, "create")),
    );
    setSubmitting(false);
    if (result.ok) {
      onDone(result.detail);
      return;
    }
    fail(result);
  });

  const cancel = () => {
    if (isDirty) {
      setConfirm("discard");
    } else {
      onCancel();
    }
  };

  // 日期框会把认不出的原文交给表单，先确认两边都是真实存在的日期再算用时
  const datesValid = (from: string, to: string) =>
    parseDateInput(from) === from &&
    parseDateInput(to) === to &&
    from >= MIN_DATE &&
    to <= today &&
    from <= to;
  const firstRound = datesValid(values.submittedOn, values.firstDecisionOn)
    ? daysBetween(values.submittedOn, values.firstDecisionOn)
    : null;
  const total =
    firstRound !== null && datesValid(values.firstDecisionOn, values.finalDecisionOn)
      ? daysBetween(values.submittedOn, values.finalDecisionOn)
      : null;

  const finalDateHint =
    constraints.finalDecisionOn === "terminal"
      ? CONSTRAINT_HINTS.finalDateTerminal
      : constraints.finalDecisionOn === "in-progress"
        ? CONSTRAINT_HINTS.finalDateInProgress
        : undefined;
  const roundsHint =
    constraints.reviewRounds === "none"
      ? CONSTRAINT_HINTS.notReviewed
      : constraints.reviewRounds === "one"
        ? CONSTRAINT_HINTS.oneRound
        : "1–10";
  const mode2 = editing ? "edit" : "create";

  return (
    <form
      noValidate
      onSubmit={(event) => void submit(event)}
      aria-labelledby={id("title")}
      className="flex flex-col gap-6 rounded-xl border border-border-strong bg-bg-elevated p-7 shadow-composer max-[880px]:-mx-1 max-[880px]:px-4 max-[880px]:py-5"
    >
      <div>
        <h3 id={id("title")} className="m-0 font-medium font-serif text-2xl text-fg-1 leading-7.5">
          {editing ? "编辑投稿经验" : "写投稿经验"}
        </h3>
        <p className="mt-1.5 text-fg-3 text-sm leading-5">
          {journalTitle} · 一篇稿件写一条；只填日期，用时由系统算出。带 * 的必填
        </p>
      </div>

      <fieldset disabled={submitting} className="m-0 flex min-w-0 flex-col gap-6 border-0 p-0">
        <fieldset className="m-0 flex min-w-0 flex-col gap-4.5 border-0 border-border-subtle border-t px-0 pt-5 pb-0">
          <legend className={LEGEND}>投稿与审稿</legend>
          <div className={GRID}>
            <BoxField
              id={id("submitted")}
              label="投稿日期"
              required
              error={errors.submittedOn?.message}
              bare
            >
              {(field) => (
                <Controller
                  control={control}
                  name="submittedOn"
                  render={({ field: date }) => (
                    <DatePicker
                      id={field.id}
                      value={date.value}
                      onChange={date.onChange}
                      onBlur={date.onBlur}
                      inputRef={date.ref}
                      today={today}
                      min={MIN_DATE}
                      invalid={field["aria-invalid"]}
                      describedBy={field["aria-describedby"]}
                      // 表单是点「写投稿经验」后原地展开的，焦点进第一个字段（简报 §3.15）
                      autoFocus
                      sheetTitle="投稿日期"
                    />
                  )}
                />
              )}
            </BoxField>
            <BoxField
              id={id("first-date")}
              label="首轮决定日期"
              required
              error={errors.firstDecisionOn?.message}
              bare
            >
              {(field) => (
                <Controller
                  control={control}
                  name="firstDecisionOn"
                  render={({ field: date }) => (
                    <DatePicker
                      id={field.id}
                      value={date.value}
                      onChange={date.onChange}
                      onBlur={date.onBlur}
                      inputRef={date.ref}
                      today={today}
                      min={MIN_DATE}
                      reference={{ date: values.submittedOn, label: "投稿" }}
                      invalid={field["aria-invalid"]}
                      describedBy={field["aria-describedby"]}
                      sheetTitle="首轮决定日期"
                    />
                  )}
                />
              )}
            </BoxField>
            <ChoiceGroup
              id={id("first")}
              label="首轮结果"
              required
              wide
              options={FIRST_DECISIONS.map((value) => ({
                value,
                label: FIRST_DECISION_LABELS[value],
              }))}
              value={values.firstDecision}
              onChange={chooseFirstDecision}
              onBlur={() => void trigger("firstDecision")}
              error={errors.firstDecision?.message}
            />
            <ChoiceGroup
              id={id("final")}
              label="最终结果"
              required
              wide
              options={FINAL_OUTCOMES.map((value) => ({
                value,
                label: FINAL_OUTCOME_LABELS[value],
                disabled:
                  constraints.lockedFinalOutcome !== null &&
                  value !== constraints.lockedFinalOutcome,
              }))}
              value={values.finalOutcome}
              onChange={chooseFinalOutcome}
              error={errors.finalOutcome?.message}
            />
            <BoxField
              id={id("final-date")}
              label="最终决定日期"
              optional
              error={errors.finalDecisionOn?.message}
              hint={finalDateHint}
              bare
            >
              {(field) => (
                <Controller
                  control={control}
                  name="finalDecisionOn"
                  render={({ field: date }) => (
                    <DatePicker
                      id={field.id}
                      value={date.value}
                      onChange={date.onChange}
                      onBlur={date.onBlur}
                      inputRef={date.ref}
                      today={today}
                      min={MIN_DATE}
                      reference={{ date: values.firstDecisionOn, label: "首轮决定" }}
                      disabled={constraints.finalDecisionOn !== "enabled"}
                      invalid={field["aria-invalid"]}
                      describedBy={field["aria-describedby"]}
                      sheetTitle="最终决定日期"
                    />
                  )}
                />
              )}
            </BoxField>
            <BoxField
              id={id("type")}
              label="文章类型"
              required
              error={errors.articleType?.message}
              bare
            >
              {(field) => (
                <Controller
                  control={control}
                  name="articleType"
                  render={({ field: type }) => (
                    <Select
                      items={ARTICLE_TYPE_ITEMS}
                      value={type.value === "" ? null : type.value}
                      onValueChange={(next) => type.onChange(next ?? "")}
                      // 下拉展开时焦点在弹层里，按失焦校验会过早报错；改在收起时校验
                      onOpenChange={(open) => {
                        if (!open) {
                          type.onBlur();
                        }
                      }}
                    >
                      <SelectTrigger
                        id={field.id}
                        ref={type.ref}
                        aria-invalid={field["aria-invalid"] || undefined}
                        aria-describedby={field["aria-describedby"]}
                      >
                        <SelectValue placeholder="请选择" />
                      </SelectTrigger>
                      <SelectContent sheetTitle="文章类型">
                        {ARTICLE_TYPE_ITEMS.map((item) => (
                          <SelectItem key={item.value} value={item.value}>
                            {item.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              )}
            </BoxField>
          </div>
          {firstRound !== null && (
            <p className="m-0 flex items-center gap-2 text-fg-3 text-sm">
              <Calculator className="size-3.5" aria-hidden="true" />
              <span>
                首轮用时{" "}
                <b className="font-medium text-fg-1 tabular-nums">{formatDays(firstRound)}</b>
                {total !== null && (
                  <>
                    {" · 投稿到最终决定 "}
                    <b className="font-medium text-fg-1 tabular-nums">{formatDays(total)}</b>
                  </>
                )}
                （由日期算出）
              </span>
            </p>
          )}
        </fieldset>

        <fieldset className="m-0 flex min-w-0 flex-col gap-4.5 border-0 border-border-subtle border-t px-0 pt-5 pb-0">
          <legend className={LEGEND}>
            更多细节<span className="ml-2 font-normal text-fg-3 text-sm">选填</span>
          </legend>
          <div className={GRID}>
            <BoxField
              id={id("rounds")}
              label="审稿轮数"
              error={errors.reviewRounds?.message}
              hint={roundsHint}
              bare
            >
              {(field) => (
                <Controller
                  control={control}
                  name="reviewRounds"
                  render={({ field: rounds }) => (
                    <NumberField
                      id={field.id}
                      value={rounds.value === "" ? null : Number(rounds.value)}
                      onValueChange={(next) => rounds.onChange(next === null ? "" : String(next))}
                      onBlur={rounds.onBlur}
                      inputRef={rounds.ref}
                      min={1}
                      max={10}
                      disabled={constraints.reviewRounds !== "enabled"}
                      invalid={field["aria-invalid"]}
                      describedBy={field["aria-describedby"]}
                    />
                  )}
                />
              )}
            </BoxField>
            <ChoiceGroup
              id={id("apc")}
              label="是否付了 APC"
              clearable
              options={APC_OPTIONS}
              value={values.apcPaid}
              onChange={(value) => choose("apcPaid", value)}
              error={errors.apcPaid?.message}
            />
            <ChoiceGroup
              id={id("quality")}
              label="审稿质量"
              clearable
              variant="score"
              note="1 很差 · 5 很好"
              options={scoreOptions(constraints.reviewQuality === "none")}
              value={values.reviewQuality as (typeof SCORES)[number] | ""}
              onChange={(value) => choose("reviewQuality", value)}
              error={errors.reviewQuality?.message}
              hint={constraints.reviewQuality === "none" ? CONSTRAINT_HINTS.notReviewed : undefined}
            />
            <ChoiceGroup
              id={id("communication")}
              label="编辑沟通"
              clearable
              variant="score"
              note="1 很差 · 5 很好"
              options={scoreOptions(false)}
              value={values.editorCommunication as (typeof SCORES)[number] | ""}
              onChange={(value) => choose("editorCommunication", value)}
              error={errors.editorCommunication?.message}
            />
            <ChoiceGroup
              id={id("transfer")}
              label="是否转投过"
              clearable
              wide
              options={TRANSFER_OPTIONS}
              value={values.previouslyRejected}
              onChange={(value) => choose("previouslyRejected", value)}
              error={errors.previouslyRejected?.message}
              hint="投本刊之前投过别的期刊，被拒后改投过来"
            />
            <div data-field className="col-span-full flex min-w-0 flex-col gap-2">
              <span className="font-medium text-fg-1 text-sm leading-5">经历</span>
              <Controller
                control={control}
                name="body"
                render={({ field }) => (
                  <MarkdownEditor
                    value={field.value}
                    onChange={field.onChange}
                    label="经历"
                    placeholder="审稿人关注什么、编辑回复快不快、修回花了多久……"
                    limits={EXPERIENCE_BODY_LIMITS}
                    limitLabel="正文"
                    mentions={false}
                    textareaRef={bodyBox}
                    readOnly={submitting}
                  />
                )}
              />
              <FieldNote id={id("body-note")} error={errors.body?.message} />
            </div>
          </div>
        </fieldset>

        <fieldset className="m-0 flex min-w-0 flex-col gap-4.5 border-0 border-border-subtle border-t px-0 pt-5 pb-0">
          <legend className={LEGEND}>发布方式</legend>
          {editing ? (
            <p className="m-0 flex items-center gap-2 text-fg-2 text-sm leading-5">
              <Lock className="size-3.5" aria-hidden="true" />
              {props.anonymous
                ? "这条经验以匿名发布，不能更改。"
                : "这条经验以你的名字发布，不能改成匿名。"}
            </p>
          ) : (
            <label className="relative grid cursor-pointer grid-cols-[16px_minmax(0,1fr)] items-start gap-3">
              <input
                type="checkbox"
                {...register("anonymous")}
                className={cn(CHECK_INPUT, "mt-0.5")}
              />
              <svg
                aria-hidden
                viewBox="0 0 16 16"
                className={cn(CHECK_MARK, "top-0.5 left-0 translate-y-0")}
              >
                <path d="M3.6 8.4l2.9 2.9 5.9-6.6" pathLength={1} />
              </svg>
              <span>
                <span className="font-medium text-base text-fg-1 leading-5">匿名发布</span>
                <span className="mt-1 block text-fg-3 text-sm leading-5">
                  别人看到的作者是「匿名作者」，不会链接到你的主页；这条经验也不会出现在你主页公开的「投稿经验」里（你自己看得到）。
                </span>
                <span className="mt-1.5 flex items-center gap-1.5 text-amber-700 text-sm leading-5">
                  <TriangleAlert className="size-3.5" aria-hidden="true" />
                  发布后不能更改匿名设置。
                </span>
              </span>
            </label>
          )}
        </fieldset>

        {alert && (
          <FormAlert
            action={
              alert.reason === "duplicate" && alert.existingPostId ? (
                <Link href={`/posts/${alert.existingPostId}`} className={btnText}>
                  查看那条经验
                </Link>
              ) : alert.reason === "email-unverified" ? (
                <ResendVerificationButton label="重新发送验证邮件" />
              ) : undefined
            }
          >
            {experienceFailureMessage(alert.reason, mode2, values.submittedOn)}
          </FormAlert>
        )}

        <div className="flex flex-wrap items-center justify-end gap-3 border-border-subtle border-t pt-5 max-[880px]:flex-col-reverse max-[880px]:items-stretch">
          {confirm === "signed-out" && (
            <span className="mr-auto inline-flex flex-wrap items-center gap-2.5 text-fg-1 text-sm">
              {COMPOSER_COPY.sessionExpired}
              <Link href={loginHref} className={btnText}>
                去登录
              </Link>
              <button type="button" onClick={() => setConfirm(null)} className={btnText}>
                留在这里
              </button>
            </span>
          )}
          <button
            type="button"
            onClick={cancel}
            className={cn(btnSecondary, "border-transparent bg-transparent")}
          >
            取消
          </button>
          <button type="submit" disabled={bodyBlocked} className={btnPrimary}>
            {submitting ? (editing ? "保存中…" : "发布中…") : editing ? "保存修改" : "发布投稿经验"}
          </button>
        </div>
      </fieldset>

      <Dialog
        open={confirm === "discard"}
        onOpenChange={(open) => !open && setConfirm(null)}
        title="放弃这条投稿经验？"
        description="已经填写的内容不会保存。"
        size="sm"
      >
        <div className="flex justify-end gap-3">
          <button type="button" onClick={() => setConfirm(null)} className={btnSecondary}>
            继续填写
          </button>
          <button
            type="button"
            onClick={() => {
              setConfirm(null);
              onCancel();
            }}
            className={btnDangerOutline}
          >
            放弃
          </button>
        </div>
      </Dialog>
    </form>
  );
}
