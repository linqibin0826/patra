// patra-learn/src/lib/annotations/dom-text.ts
// 正文区域的纯文本与 DOM 选区互换：批注按文本位置定位（anchor.ts），画高亮、放徽章时再换回 Range

import type { TextSpan } from "./anchor";

/// 正文全部文字，按文档顺序拼起来（与 Range.toString 的口径一致）
export function textOf(root: Node): string {
  return root.textContent ?? "";
}

/// 从正文开头到 (node, offset) 有多少个字符
function offsetOf(root: Node, node: Node, offset: number) {
  const range = document.createRange();
  range.selectNodeContents(root);
  range.setEnd(node, offset);
  return range.toString().length;
}

/// 选区 → 正文文本里的位置；选区有一头不在正文里时返回 null
export function spanOf(root: Node, range: Range): TextSpan | null {
  if (!root.contains(range.startContainer) || !root.contains(range.endContainer)) return null;
  return {
    start: offsetOf(root, range.startContainer, range.startOffset),
    end: offsetOf(root, range.endContainer, range.endOffset),
  };
}

/// 正文文本里的位置 → 选区；超出文本长度时返回 null
export function rangeAt(root: Node, start: number, end: number): Range | null {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const range = document.createRange();
  let pos = 0;
  let started = false;
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    const length = node.textContent?.length ?? 0;
    if (!started && start < pos + length) {
      range.setStart(node, start - pos);
      started = true;
    }
    if (started && end <= pos + length) {
      range.setEnd(node, end - pos);
      return range;
    }
    pos += length;
  }
  return null;
}
