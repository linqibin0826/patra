"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { Controller, type FieldPath, FormProvider, useForm } from "react-hook-form";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { LeaveGuard } from "@/components/settings/LeaveGuard";
import { CharCounter } from "@/components/settings/profile/CharCounter";
import { FieldError } from "@/components/settings/profile/FieldError";
import { LinkListField } from "@/components/settings/profile/LinkListField";
import { MeshInterestPicker } from "@/components/settings/profile/MeshInterestPicker";
import { OrganizationPicker } from "@/components/settings/profile/OrganizationPicker";
import { SettingsCard } from "@/components/settings/SettingsCard";
import { StickySaveBar } from "@/components/settings/StickySaveBar";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectSeparator,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { CurrentUser } from "@/lib/auth/types";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { OCCUPATIONS, occupationLabel } from "@/lib/identity/occupation";
import { type SaveProfileResult, saveProfile } from "@/lib/identity/profile-actions";
import {
  formPathOf,
  lengthMessage,
  type ProfileFormValues,
  profileFieldMessage,
  profileFormSchema,
  TEXT_LIMITS,
  toFormValues,
} from "@/lib/identity/profile-form";
import type { MyProfile, PrivacySettings } from "@/lib/identity/profile-schema";
import { queryKeys } from "@/lib/query-keys";

/// 这一页的地址：保存失败跳登录页、取消注销页时作 `next`
const PAGE = "/settings/profile";
const OCCUPATION_ITEMS = OCCUPATIONS.map((value) => ({ value, label: occupationLabel(value) }));

/// 非字段错误的提示条文案（简报 §4.5）
const ALERTS = {
  "catalog-unavailable": "机构或主题词暂时无法校验，请稍后再保存。你填的内容都还在。",
  conflict: "资料没有保存成功，请再保存一次",
  unavailable: "没有保存成功，请稍后再试",
} as const;

const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

interface ProfileFormProps {
  initial: MyProfile;
  user: CurrentUser;
  privacy: PrivacySettings;
}

/// 个人资料表单（简报 §3.6、§4.5）：公开资料、专业背景、关注领域三张卡片，一个吸底保存条整份保存
/// （`PUT /me/profile`）。前端先按同样的规则校验；后端的字段错误按路径落到对应字段，焦点移到第一个；
/// 非字段错误用保存条上方的提示条，输入全部保留。
export function ProfileForm({ initial, user, privacy }: ProfileFormProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const leave = useCommonFailureRedirect(PAGE);
  const [saving, setSaving] = useState(false);
  const [alert, setAlert] = useState<string | null>(null);
  // 后端报的某个关注领域的错误（`researchInterests[i]`），按主题词 UI 记：调整顺序后仍标在那个词上，
  // 移除了就跟着消失。不进 RHF：RHF 只认下标，选择器一改动就会按前端规则重新校验、把它清掉
  const [interestErrors, setInterestErrors] = useState<Record<string, string>>({});
  const form = useForm<ProfileFormValues>({
    resolver: zodResolver(profileFormSchema),
    defaultValues: toFormValues(initial),
    mode: "onSubmit",
    reValidateMode: "onChange",
  });
  const {
    register,
    handleSubmit,
    reset,
    setError,
    setFocus,
    watch,
    formState: { errors, isDirty },
  } = form;
  const [displayName, bio, interests] = watch(["displayName", "bio", "researchInterests"]);
  // 计数超出上限（字数或字节）时立即显示字段错误，不等提交（简报 §4.5）
  const displayNameError =
    errors.displayName?.message ??
    lengthMessage("displayName", displayName, TEXT_LIMITS.displayName);
  const bioError = errors.bio?.message ?? lengthMessage("bio", bio, TEXT_LIMITS.bio);

  /// 后端的字段错误挂到表单字段上（某个关注领域的错误按提交时那一项的 UI 记下），返回第一个挂上的字段
  function showFieldErrors(
    fieldErrors: { field: string; code: string | null }[],
    values: ProfileFormValues,
  ) {
    let first: FieldPath<ProfileFormValues> | null = null;
    const byInterest: Record<string, string> = {};
    for (const { field, code } of fieldErrors) {
      const index = /^researchInterests\[(\d+)\]$/.exec(field)?.[1];
      const interest = index === undefined ? undefined : values.researchInterests[Number(index)];
      if (interest) {
        byInterest[interest.ui] = profileFieldMessage(field, code);
        first ??= "researchInterests";
        continue;
      }
      const path = formPathOf(field) as FieldPath<ProfileFormValues> | null;
      if (path) {
        setError(path, { type: "server", message: profileFieldMessage(field, code) });
        first ??= path;
      }
    }
    setInterestErrors(byInterest);
    return first;
  }

  /// 放弃改动或保存成功后回到某份值，后端报过的错误一并作废
  function resetTo(values?: ProfileFormValues) {
    reset(values);
    setInterestErrors({});
  }

  /// 保存结果 → 界面
  function settle(result: SaveProfileResult, values: ProfileFormValues) {
    if (result.ok) {
      const saved = toFormValues(result.profile);
      // 刚从 ROR 选中的机构，保存后仍显示完整副行（GET 不带英文名与城市）
      const { organization } = values;
      if (
        saved.organization?.kind === "ror" &&
        organization?.kind === "ror" &&
        organization.rorId === saved.organization.rorId
      ) {
        saved.organization = { ...saved.organization, detail: organization.detail };
      }
      resetTo(saved);
      toast.success("资料已保存");
      void queryClient.invalidateQueries({ queryKey: queryKeys.currentUser });
      router.refresh();
      return;
    }
    switch (result.reason) {
      case "invalid-fields": {
        const first = showFieldErrors(result.fieldErrors, values);
        if (first) {
          setFocus(first, { shouldSelect: false });
          return;
        }
        setAlert(ALERTS.unavailable);
        return;
      }
      case "pending-deletion":
      case "signed-out":
        leave(result.reason);
        return;
      default:
        setAlert(ALERTS[result.reason]);
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) {
      return;
    }
    setAlert(null);
    setInterestErrors({});
    void handleSubmit(async (values) => {
      setSaving(true);
      const result = await saveProfile(values).catch(() => UNAVAILABLE);
      setSaving(false);
      settle(result, values);
    })(event);
  }

  return (
    <FormProvider {...form}>
      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-8 max-[880px]:gap-6">
        <SettingsCard title="公开资料">
          <FormField
            id="profile-display-name"
            label="昵称"
            aside={<CharCounter value={displayName} max={TEXT_LIMITS.displayName} />}
            hint="2–30 个字。不填就显示用户名。昵称可以和别人重复，主页上会同时显示 @用户名"
            error={displayNameError}
          >
            {(control) => (
              <input
                {...control}
                {...register("displayName")}
                type="text"
                placeholder={user.username}
                readOnly={saving}
              />
            )}
          </FormField>
          <FormField
            id="profile-bio"
            label="简介"
            optional
            aside={<CharCounter value={bio} max={TEXT_LIMITS.bio} />}
            hint="最多 3 行"
            error={bioError}
          >
            {(control) => <textarea {...control} {...register("bio")} rows={3} readOnly={saving} />}
          </FormField>
          <LinkListField emailVerified={user.emailVerified} readOnly={saving} />
        </SettingsCard>

        <SettingsCard
          title="专业背景"
          description={
            <>
              <p>按你填写的原样展示，不代表经过认证。</p>
              <p className="mt-0.5">
                当前：{privacy.professionalVisibility === "PUBLIC" ? "所有人可见" : "仅自己可见"}
                <Link
                  href="/settings/privacy"
                  className="ml-3 text-link transition-colors hover:text-link-hover"
                >
                  更改
                </Link>
              </p>
            </>
          }
        >
          <FormField id="profile-occupation" label="身份" error={errors.occupation?.message} bare>
            {(control) => (
              <Controller
                control={form.control}
                name="occupation"
                render={({ field }) => (
                  <Select
                    items={OCCUPATION_ITEMS}
                    // 「不填写」是空值：表单里是空串，下拉里是 null（显示成灰色占位）
                    value={field.value === "" ? null : field.value}
                    onValueChange={(next) => field.onChange(next ?? "")}
                    disabled={saving}
                  >
                    <SelectTrigger
                      id={control.id}
                      ref={field.ref}
                      size="lg"
                      aria-invalid={control["aria-invalid"] || undefined}
                      aria-describedby={control["aria-describedby"]}
                    >
                      <SelectValue placeholder="不填写" />
                    </SelectTrigger>
                    <SelectContent sheetTitle="身份">
                      <SelectItem value={null}>不填写</SelectItem>
                      <SelectSeparator />
                      {OCCUPATION_ITEMS.map((item) => (
                        <SelectItem key={item.value} value={item.value}>
                          {item.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            )}
          </FormField>
          <FormField
            id="profile-job-title"
            label="职称 / 职位"
            optional
            error={errors.jobTitle?.message}
          >
            {(control) => (
              <input
                {...control}
                {...register("jobTitle")}
                type="text"
                placeholder="如：主治医师、副教授、博士后"
                readOnly={saving}
              />
            )}
          </FormField>
          <div className="flex min-w-0 flex-col gap-2">
            <label
              htmlFor="profile-organization"
              className="font-medium text-fg-1 text-sm leading-5"
            >
              机构<span className="font-normal text-fg-3">（可选）</span>
            </label>
            <Controller
              control={form.control}
              name="organization"
              render={({ field }) => (
                <OrganizationPicker
                  id="profile-organization"
                  value={field.value}
                  onChange={field.onChange}
                  focusRef={field.ref}
                  error={errors.organization?.message}
                  readOnly={saving}
                />
              )}
            />
            {errors.organization?.message && (
              <FieldError id="profile-organization-error">{errors.organization.message}</FieldError>
            )}
          </div>
          <FormField
            id="profile-department"
            label="科室 / 院系"
            optional
            error={errors.department?.message}
          >
            {(control) => (
              <input
                {...control}
                {...register("department")}
                type="text"
                placeholder="如：心内科、公共卫生学院"
                readOnly={saving}
              />
            )}
          </FormField>
        </SettingsCard>

        <SettingsCard
          title="关注领域"
          aside={
            <span className="font-mono text-2xs text-fg-3 tabular-nums">
              {interests.length} / 10
            </span>
          }
          description="选择最多 10 个 MeSH 主题词，展示在主页上，点击可检索相关文献。主题词只有英文。"
        >
          <Controller
            control={form.control}
            name="researchInterests"
            render={({ field }) => (
              <MeshInterestPicker
                id="profile-research-interests"
                value={field.value}
                onChange={(next) => {
                  field.onChange(next);
                  // 移除的词带走它的错误，再加回来时不再描红
                  setInterestErrors((current) =>
                    Object.fromEntries(
                      next.flatMap(({ ui }) => (current[ui] ? [[ui, current[ui]]] : [])),
                    ),
                  );
                }}
                focusRef={field.ref}
                errors={interestErrors}
                readOnly={saving}
              />
            )}
          />
          {errors.researchInterests?.message && (
            <FieldError>{errors.researchInterests.message}</FieldError>
          )}
        </SettingsCard>

        {alert && <FormAlert>{alert}</FormAlert>}
        {isDirty && <StickySaveBar saving={saving} onDiscard={() => resetTo()} />}
        <LeaveGuard when={isDirty && !saving} />
      </form>
    </FormProvider>
  );
}
