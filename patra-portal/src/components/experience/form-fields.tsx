"use client";

import { CircleAlert } from "lucide-react";
import type { ReactNode } from "react";
import { FIELD_BOX, FIELD_INPUT } from "@/components/auth/FormField";
import { cn } from "@/lib/utils";

/// 投稿经验表单的字段（画板 `ExpForm`，简报 §3.15）：标签后的 `*`、字段下方一行提示或错误。门户的 `FormField`
/// 只有「（可选）」的写法，这里的必填 / 选填写法不同，另写一层

const noop = () => {};

/// 标签：必填写 `*`（`fg-3`），选填写 `选填`
function FieldLabel({
  htmlFor,
  id,
  required,
  optional,
  children,
}: {
  htmlFor?: string;
  id?: string;
  required?: boolean;
  optional?: boolean;
  children: ReactNode;
}) {
  const Tag = htmlFor ? "label" : "span";
  return (
    <Tag htmlFor={htmlFor} id={id} className="font-medium text-fg-1 text-sm leading-5">
      {children}
      {required && <span className="ml-0.5 font-normal text-fg-3">*</span>}
      {optional && <span className="ml-1.5 font-normal text-fg-3">选填</span>}
    </Tag>
  );
}

/// 字段下面一行：错误优先，否则是提示（联动说明、含义说明）
export function FieldNote({ id, error, hint }: { id: string; error?: string; hint?: ReactNode }) {
  if (error) {
    return (
      <p
        key="error"
        id={id}
        className="disclosure-body flex items-start gap-1.5 text-sm text-status-danger leading-5"
      >
        <CircleAlert className="mt-0.75 size-3.5 shrink-0" aria-hidden="true" />
        <span>{error}</span>
      </p>
    );
  }
  return hint ? (
    <p key="hint" id={id} className="text-fg-3 text-sm leading-5">
      {hint}
    </p>
  ) : null;
}

interface BoxFieldProps {
  id: string;
  label: string;
  required?: boolean;
  optional?: boolean;
  error?: string;
  hint?: ReactNode;
  wide?: boolean;
  /// 控件自带外框（日期、下拉、数字原语）时不再套输入井
  bare?: boolean;
  children: (control: {
    id: string;
    className: string;
    "aria-invalid": boolean;
    "aria-describedby"?: string;
  }) => ReactNode;
}

/// 带标签与提示行的字段；普通输入框套输入井外框，自带外框的控件传 `bare`
export function BoxField({
  id,
  label,
  required,
  optional,
  error,
  hint,
  wide,
  bare,
  children,
}: BoxFieldProps) {
  const noteId = `${id}-note`;
  const control = children({
    id,
    className: cn(FIELD_INPUT, "disabled:cursor-not-allowed"),
    "aria-invalid": Boolean(error),
    "aria-describedby": error || hint ? noteId : undefined,
  });
  return (
    <div data-field className={cn("flex min-w-0 flex-col gap-2", wide && "col-span-full")}>
      <FieldLabel htmlFor={id} required={required} optional={optional}>
        {label}
      </FieldLabel>
      {bare ? (
        control
      ) : (
        <div className={cn(FIELD_BOX, "h-11 rounded-lg has-[:disabled]:bg-bg-sunken")}>
          {control}
        </div>
      )}
      <FieldNote id={noteId} error={error} hint={hint} />
    </div>
  );
}

export interface ChoiceOption<T extends string> {
  value: T;
  label: string;
  disabled?: boolean;
}

interface ChoiceGroupProps<T extends string> {
  id: string;
  label: string;
  required?: boolean;
  options: readonly ChoiceOption<T>[];
  value: T | "";
  onChange: (value: T | "") => void;
  /// 选填：再点一次已选项取消，另有「清除选择」按钮
  clearable?: boolean;
  /// `chip` 胶囊；`score` 1–5 方块
  variant?: "chip" | "score";
  /// 方块后面的注（`1 很差 · 5 很好`）
  note?: string;
  error?: string;
  hint?: ReactNode;
  wide?: boolean;
  onBlur?: () => void;
}

/// 胶囊单选组与评分方块（画板 `.pt-chips` / `.pt-score`）：真正的 radio（读屏、方向键照常），视觉是胶囊。
/// radio 点已选中项不触发 onChange，改用 onClick 才能「再点取消」（同 `FacetCheckRow`）
export function ChoiceGroup<T extends string>({
  id,
  label,
  required,
  options,
  value,
  onChange,
  clearable,
  variant = "chip",
  note,
  error,
  hint,
  wide,
  onBlur,
}: ChoiceGroupProps<T>) {
  const labelId = `${id}-label`;
  const noteId = `${id}-note`;
  return (
    <div data-field className={cn("flex min-w-0 flex-col gap-2", wide && "col-span-full")}>
      <FieldLabel id={labelId} required={required}>
        {label}
      </FieldLabel>
      <div
        role="radiogroup"
        aria-labelledby={labelId}
        aria-invalid={Boolean(error)}
        aria-describedby={error || hint ? noteId : undefined}
        className="flex flex-wrap items-center gap-2"
      >
        {options.map((option) => {
          const checked = value === option.value;
          return (
            <label
              key={option.value}
              className={cn(
                "relative inline-flex h-9 cursor-pointer select-none items-center rounded-full border border-border-default bg-paper-50 px-3.5 font-medium text-fg-2 text-sm transition-colors duration-150 has-[:focus-visible]:shadow-(--ring-focus) max-[880px]:h-11",
                variant === "score" &&
                  "w-9 justify-center rounded-md px-0 font-mono max-[880px]:w-11",
                checked
                  ? "border-clay-600 bg-clay-50 text-fg-1"
                  : "not-has-disabled:hover:border-border-hover not-has-disabled:hover:text-ink-900",
                error && !checked && "border-rust-500/55",
                "has-disabled:cursor-not-allowed has-disabled:opacity-50",
              )}
            >
              <input
                type="radio"
                name={id}
                value={option.value}
                checked={checked}
                disabled={option.disabled}
                onChange={noop}
                onClick={() => onChange(clearable && checked ? "" : option.value)}
                onBlur={onBlur}
                className="absolute size-px opacity-0"
              />
              {option.label}
            </label>
          );
        })}
        {note && <small className="ml-1.5 text-fg-3 text-xs">{note}</small>}
        {clearable && value !== "" && (
          <button
            type="button"
            onClick={() => onChange("")}
            className="px-1 text-fg-3 text-xs hover:text-fg-1 hover:underline"
          >
            清除选择
          </button>
        )}
      </div>
      <FieldNote id={noteId} error={error} hint={hint} />
    </div>
  );
}
