"use client";

import { NumberField as NumberFieldPrimitive } from "@base-ui/react/number-field";
import { Minus, Plus } from "lucide-react";
import type { Ref } from "react";

// base-ui NumberField 的薄封装（画布「表单控件重设计」）：值靠左、等宽数字，右端 − / + 两个按钮。
// 直接敲的值不夹取（`allowOutOfRange`），超出范围由表单校验报错；按钮与方向键只在范围内加减。

interface NumberFieldProps {
  id: string;
  value: number | null;
  onValueChange: (value: number | null) => void;
  min: number;
  max: number;
  disabled?: boolean;
  invalid?: boolean;
  describedBy?: string;
  inputRef?: Ref<HTMLInputElement>;
  onBlur?: () => void;
  className?: string;
}

const STEP =
  "flex size-8 shrink-0 cursor-pointer items-center justify-center rounded-md text-fg-2 transition-colors duration-150 hover:bg-bg-sunken hover:text-fg-1 disabled:cursor-not-allowed disabled:bg-transparent disabled:text-fg-4 max-[880px]:size-10";

/// 数字步进：外框与表单输入框同一套（44 高）；两个按钮不进 Tab 顺序，键盘用 ↑ / ↓
export function NumberField({
  id,
  value,
  onValueChange,
  min,
  max,
  disabled,
  invalid,
  describedBy,
  inputRef,
  onBlur,
  className,
}: NumberFieldProps) {
  return (
    <NumberFieldPrimitive.Root
      id={id}
      value={value}
      onValueChange={(next) => onValueChange(next)}
      min={min}
      max={max}
      step={1}
      allowOutOfRange
      disabled={disabled}
      inputRef={inputRef}
      className={className}
    >
      <NumberFieldPrimitive.Group className="flex h-11 items-center gap-1 rounded-lg border border-border-strong bg-bg-elevated pr-1.5 pl-4 shadow-inset ring-clay-500/15 transition-[border-color,box-shadow,background-color] duration-200 hover:border-ink-500 has-[input:focus]:border-border-focus has-[input:focus]:ring-4 has-[input[aria-invalid=true]]:border-status-danger has-[input[aria-invalid=true]]:ring-rust-500/15 has-[input:disabled]:border-border-default has-[input:disabled]:bg-bg-sunken has-[input:disabled]:shadow-none">
        <NumberFieldPrimitive.Input
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          onBlur={onBlur}
          className="h-full min-w-0 flex-1 bg-transparent p-0 font-mono text-ink-900 text-sm tabular-nums outline-none disabled:cursor-not-allowed disabled:text-fg-2 max-[880px]:text-md"
        />
        <NumberFieldPrimitive.Decrement aria-label="减一" className={STEP}>
          <Minus className="size-4" aria-hidden="true" />
        </NumberFieldPrimitive.Decrement>
        <span aria-hidden="true" className="h-4.5 w-px bg-border-subtle" />
        <NumberFieldPrimitive.Increment aria-label="加一" className={STEP}>
          <Plus className="size-4" aria-hidden="true" />
        </NumberFieldPrimitive.Increment>
      </NumberFieldPrimitive.Group>
    </NumberFieldPrimitive.Root>
  );
}
