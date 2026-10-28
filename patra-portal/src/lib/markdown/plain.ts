import type { Nodes } from "mdast";
import { fromMarkdown } from "mdast-util-from-markdown";
import { graphemes } from "@/lib/grapheme";

/// 块级节点：前后各补一个空格，段落、列表项之间不会粘在一起
const BLOCKS = new Set([
  "paragraph",
  "heading",
  "blockquote",
  "list",
  "listItem",
  "code",
  "table",
  "tableRow",
  "tableCell",
  "thematicBreak",
]);

function collect(node: Nodes, out: string[]): void {
  const block = BLOCKS.has(node.type);
  if (block) {
    out.push(" ");
  }
  if (node.type === "html") {
    // 标签不是文字：摘录与页面描述里不出现原始标记
  } else if (node.type === "image") {
    out.push(node.alt ?? "");
  } else if (node.type === "break") {
    out.push(" ");
  } else if ("value" in node) {
    out.push(node.value);
  } else if ("children" in node) {
    for (const child of node.children) {
      collect(child, out);
    }
  }
  if (block) {
    out.push(" ");
  }
}

/// Markdown 原文 → 纯文本（经验列表的两行摘录、页面 `description`）：解析后只取文字，链接留文字、图片留替代文字，
/// 空白折叠成一个空格。按解析结果取，不用正则硬剥（转义、行内代码里的标记都按原意处理）
export function markdownToPlain(source: string): string {
  const out: string[] = [];
  collect(fromMarkdown(source), out);
  return out.join("").replace(/\s+/g, " ").trim();
}

/// 纯文本摘录：超过 `max` 个字素时截断并加 `…`
export function excerpt(source: string, max: number): string {
  const chars = graphemes(markdownToPlain(source));
  return chars.length > max ? `${chars.slice(0, max).join("")}…` : chars.join("");
}
