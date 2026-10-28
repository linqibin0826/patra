/// 编辑器工具条的动作（简报 §3.5）
export type ToolbarAction =
  | "bold"
  | "italic"
  | "quote"
  | "bullet"
  | "ordered"
  | "code"
  | "link"
  | "mention";

export interface Selection {
  start: number;
  end: number;
}

const WRAPS: Partial<Record<ToolbarAction, string>> = { bold: "**", italic: "*", code: "`" };

/// 按行加前缀：引用 `> `、无序 `- `、有序 `1. 2. …`
function prefixLines(value: string, selection: Selection, prefix: (index: number) => string) {
  const lineStart = value.lastIndexOf("\n", selection.start - 1) + 1;
  const found = value.indexOf("\n", selection.end);
  const lineEnd = found === -1 ? value.length : found;
  const block = value
    .slice(lineStart, lineEnd)
    .split("\n")
    .map((line, index) => `${prefix(index)}${line}`)
    .join("\n");
  const next = value.slice(0, lineStart) + block + value.slice(lineEnd);
  if (selection.start === selection.end) {
    const caret = selection.start + prefix(0).length;
    return { value: next, selection: { start: caret, end: caret } };
  }
  return { value: next, selection: { start: lineStart, end: lineStart + block.length } };
}

/// 对文本框的值应用一个工具条动作：有选区包住选区，没选区插入成对标记并把光标放中间；列表、引用按行加前缀；
/// 链接包成 `[选区](https://)` 并选中网址；提及插入 `@`
export function applyToolbar(
  value: string,
  selection: Selection,
  action: ToolbarAction,
): { value: string; selection: Selection } {
  const before = value.slice(0, selection.start);
  const selected = value.slice(selection.start, selection.end);
  const after = value.slice(selection.end);
  const mark = WRAPS[action];
  if (mark) {
    const start = selection.start + mark.length;
    return {
      value: `${before}${mark}${selected}${mark}${after}`,
      selection: { start, end: start + selected.length },
    };
  }
  switch (action) {
    case "link": {
      const start = selection.start + selected.length + 3;
      return {
        value: `${before}[${selected}](https://)${after}`,
        selection: { start, end: start + "https://".length },
      };
    }
    case "mention": {
      const caret = selection.start + 1;
      return { value: `${before}@${after}`, selection: { start: caret, end: caret } };
    }
    case "quote":
      return prefixLines(value, selection, () => "> ");
    case "bullet":
      return prefixLines(value, selection, () => "- ");
    default:
      return prefixLines(value, selection, (index) => `${index + 1}. `);
  }
}
