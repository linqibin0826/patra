import { useCallback, useEffect, useRef, useState } from "react";

/// 复制的结果：`copied` 已进剪贴板；`selected` 都失败了，文字保持选中，等用户按快捷键
export type CopyState = "idle" | "copied" | "selected";

/// 「已复制」保持 1.6 秒（简报 §3.4）
const COPIED_MS = 1_600;

/// 选中元素里的全部文字（输入框用 `select()`）
function selectContents(target: HTMLElement): void {
  if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) {
    target.select();
    return;
  }
  const range = document.createRange();
  range.selectNodeContents(target);
  const selection = window.getSelection();
  selection?.removeAllRanges();
  selection?.addRange(range);
}

/// 第三步的提示：Mac 写 ⌘C，其他写 Ctrl+C，触屏写长按
function selectedHint(): string {
  if (typeof window.matchMedia === "function" && window.matchMedia("(pointer: coarse)").matches) {
    return "已选中，长按文字复制";
  }
  return /Mac|iPhone|iPad/.test(navigator.platform)
    ? "已选中，按 ⌘C 复制"
    : "已选中，按 Ctrl+C 复制";
}

/// 全站共用的复制（简报 §3.8），依次尝试：
/// 1. `navigator.clipboard.writeText`（只在 HTTPS / localhost 可用）；
/// 2. 选中目标元素的文字后 `document.execCommand("copy")`（mini 是 HTTP，实际落在这一步）；
/// 3. 都失败：文字保持选中，提示用户按快捷键，提示保留到选区变化。
export function useCopy(): {
  state: CopyState;
  hint: string;
  copy: (text: string, target: HTMLElement | null) => Promise<CopyState>;
} {
  const [state, setState] = useState<CopyState>("idle");
  const [hint, setHint] = useState("");
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (timer.current) {
        clearTimeout(timer.current);
      }
    },
    [],
  );

  // 第三步的提示保留到选区变化
  useEffect(() => {
    if (state !== "selected") {
      return;
    }
    let armed = false;
    const arm = setTimeout(() => {
      armed = true;
    }, 0);
    const onChange = () => {
      if (armed) {
        setState("idle");
      }
    };
    document.addEventListener("selectionchange", onChange);
    return () => {
      clearTimeout(arm);
      document.removeEventListener("selectionchange", onChange);
    };
  }, [state]);

  const copy = useCallback(async (text: string, target: HTMLElement | null) => {
    const copied = () => {
      setState("copied");
      if (timer.current) {
        clearTimeout(timer.current);
      }
      timer.current = setTimeout(() => setState("idle"), COPIED_MS);
      return "copied" as const;
    };
    try {
      await navigator.clipboard.writeText(text);
      return copied();
    } catch {
      // 非安全上下文没有剪贴板接口，或被拒绝：走第二步
    }
    // 没有可选中的元素（菜单里的「复制公开链接」）：临时放一个看不见的文本框来选中
    const temporary = target ? null : document.createElement("textarea");
    if (temporary) {
      temporary.value = text;
      temporary.setAttribute("readonly", "");
      temporary.className = "fixed -left-[9999px] opacity-0";
      document.body.append(temporary);
    }
    const element = target ?? temporary;
    if (element) {
      selectContents(element);
      try {
        if (document.execCommand("copy")) {
          temporary?.remove();
          return copied();
        }
      } catch {
        // 走第三步
      }
    }
    temporary?.remove();
    setHint(selectedHint());
    setState("selected");
    return "selected" as const;
  }, []);

  return { state, hint, copy };
}
