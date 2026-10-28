import type { KeyboardEvent } from "react";

/// 这次按键属于输入法组字：组字中（`isComposing`），或者 Safari 用 Enter 提交组字——它先发 `compositionend`，
/// 再发一次 `isComposing` 为假、`keyCode` 为 229 的 keydown
export function isImeKey(event: KeyboardEvent): boolean {
  return event.nativeEvent.isComposing || event.keyCode === 229;
}
