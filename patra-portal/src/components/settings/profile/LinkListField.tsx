"use client";

import { CircleAlert, Plus, X } from "lucide-react";
import { useFieldArray, useFormContext } from "react-hook-form";
import { FIELD_BOX, FIELD_INPUT } from "@/components/auth/FormField";
import type { ProfileFormValues } from "@/lib/identity/profile-form";

/// 最多 5 条（release spec 2.1.2）
const MAX_LINKS = 5;

interface LinkListFieldProps {
  /// 邮箱已验证；没验证时提示链接暂不展示
  emailVerified: boolean;
  readOnly: boolean;
}

/// 个人资料的链接（简报 §3.6）：0–5 行「标题（可选）」+「网址」+ 删除；满 5 条时「添加链接」换成说明。
/// 字段错误显示在该行下方，读屏经 `aria-describedby` 关联到出错的输入框。
export function LinkListField({ emailVerified, readOnly }: LinkListFieldProps) {
  const {
    control,
    register,
    formState: { errors },
  } = useFormContext<ProfileFormValues>();
  const { fields, append, remove } = useFieldArray({ control, name: "links" });
  const listError = errors.links?.root?.message ?? errors.links?.message;

  return (
    <div className="flex min-w-0 flex-col gap-2">
      <span className="font-medium text-fg-1 text-sm leading-5">
        链接<span className="font-normal text-fg-3">（可选）</span>
      </span>
      {!emailVerified && (
        <p className="text-sm leading-5 text-amber-700">验证邮箱后，链接才会展示在主页上</p>
      )}
      {fields.map((field, index) => {
        const n = index + 1;
        const titleError = errors.links?.[index]?.title?.message;
        const urlError = errors.links?.[index]?.url?.message;
        const error = urlError ?? titleError;
        const errorId = error ? `profile-link-${index}-error` : undefined;
        return (
          <div key={field.id} className="flex flex-col gap-2">
            <div className="grid grid-cols-[12rem_minmax(0,1fr)_auto] items-center gap-2 max-[880px]:grid-cols-[minmax(0,1fr)_auto]">
              <div className={FIELD_BOX}>
                <input
                  {...register(`links.${index}.title`)}
                  type="text"
                  aria-label={`链接 ${n} 的标题`}
                  aria-invalid={Boolean(titleError)}
                  aria-describedby={titleError ? errorId : undefined}
                  placeholder="标题（可选，不填显示域名）"
                  readOnly={readOnly}
                  className={FIELD_INPUT}
                />
              </div>
              <div className={`${FIELD_BOX} max-[880px]:col-start-1`}>
                <input
                  {...register(`links.${index}.url`)}
                  type="url"
                  aria-label={`链接 ${n} 的网址`}
                  aria-invalid={Boolean(urlError)}
                  aria-describedby={urlError ? errorId : undefined}
                  placeholder="https://"
                  spellCheck={false}
                  readOnly={readOnly}
                  className={FIELD_INPUT}
                />
              </div>
              <button
                type="button"
                aria-label={`删除链接 ${n}`}
                disabled={readOnly}
                onClick={() => remove(index)}
                className="inline-flex size-9 items-center justify-center rounded-full text-fg-3 transition-colors hover:bg-paper-200 hover:text-ink-900 disabled:opacity-55 max-[880px]:col-start-2 max-[880px]:row-span-2 max-[880px]:row-start-1"
              >
                <X className="size-4" strokeWidth={1.75} aria-hidden="true" />
              </button>
            </div>
            {error && (
              <p
                id={errorId}
                className="flex items-start gap-1.5 text-sm text-status-danger leading-5"
              >
                <CircleAlert className="mt-0.75 size-3.5 shrink-0" aria-hidden="true" />
                <span>{error}</span>
              </p>
            )}
          </div>
        );
      })}
      {fields.length < MAX_LINKS ? (
        <button
          type="button"
          disabled={readOnly}
          onClick={() => append({ title: "", url: "" }, { shouldFocus: true })}
          className="inline-flex min-h-8 items-center gap-1 self-start text-sm font-medium text-link transition-colors hover:text-link-hover disabled:opacity-55"
        >
          <Plus className="size-3.5" strokeWidth={2} aria-hidden="true" />
          添加链接
        </button>
      ) : (
        <p className="text-sm leading-5 text-fg-3">最多 5 条链接</p>
      )}
      {listError && <p className="text-sm text-status-danger leading-5">{listError}</p>}
    </div>
  );
}
