"use client";

import { Eye, EyeOff } from "lucide-react";
import { type ComponentProps, type Ref, useRef, useState } from "react";
import { flushSync } from "react-dom";

/// 把输入框同时交给自己的 ref 和调用方的 ref（RHF 的 `register` 靠它找到输入框）
function assignRef<T>(ref: Ref<T> | undefined, node: T | null) {
  if (typeof ref === "function") {
    ref(node);
  } else if (ref) {
    ref.current = node;
  }
}

/// 密码输入框，右侧是显示 / 隐藏切换按钮，默认隐藏；按钮桌面 32px，移动端 44px。放在 FormField 的外框里，属性原样交给输入框。
/// 切换时焦点留在输入框里，光标位置和选区不变：按下按钮时不抢焦点；有的浏览器改 `type` 时会把光标
/// 移到开头，所以改完再把切换前的选区放回去。
export function PasswordInput({ ref, ...props }: ComponentProps<"input">) {
  const [visible, setVisible] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  function toggle() {
    const input = inputRef.current;
    const start = input?.selectionStart ?? null;
    const end = input?.selectionEnd ?? null;
    flushSync(() => setVisible((value) => !value));
    if (input && start !== null && end !== null && document.activeElement === input) {
      input.setSelectionRange(start, end);
    }
  }

  return (
    <>
      <input
        {...props}
        ref={(node) => {
          inputRef.current = node;
          assignRef(ref, node);
        }}
        type={visible ? "text" : "password"}
      />
      <button
        type="button"
        aria-label={visible ? "隐藏密码" : "显示密码"}
        onMouseDown={(event) => event.preventDefault()}
        onClick={toggle}
        className="inline-flex size-8 shrink-0 max-[880px]:size-11 items-center justify-center rounded-full text-fg-3 transition-colors duration-150 hover:bg-paper-200 hover:text-ink-900"
      >
        {visible ? (
          <EyeOff className="size-3.75" aria-hidden="true" />
        ) : (
          <Eye className="size-3.75" aria-hidden="true" />
        )}
      </button>
    </>
  );
}
