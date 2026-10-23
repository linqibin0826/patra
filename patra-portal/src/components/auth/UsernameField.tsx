"use client";

import type { ChangeEvent, ComponentProps } from "react";
import type { FieldControl } from "@/components/auth/FormField";

type UsernameFieldProps = FieldControl &
  Omit<ComponentProps<"input">, keyof FieldControl | "type" | "onChange"> & {
    onChange: (event: ChangeEvent<HTMLInputElement>) => void;
  };

/// 用户名输入框，注册页与账号页（PAP-102 改用户名）共用：键入的大写字母直接显示成小写（简报 §3.1），
/// 光标位置不变；不自动大写、不做拼写检查，自动填充认作用户名。其余属性（RHF 的 `register`、只读）照传。
export function UsernameField({ onChange, ...props }: UsernameFieldProps) {
  function lowercase(event: ChangeEvent<HTMLInputElement>) {
    const input = event.target;
    const lower = input.value.toLowerCase();
    if (lower !== input.value) {
      const { selectionStart, selectionEnd } = input;
      input.value = lower;
      input.setSelectionRange(selectionStart, selectionEnd);
    }
    onChange(event);
  }

  return (
    <input
      {...props}
      type="text"
      autoComplete="username"
      autoCapitalize="none"
      autoCorrect="off"
      spellCheck={false}
      onChange={lowercase}
    />
  );
}
