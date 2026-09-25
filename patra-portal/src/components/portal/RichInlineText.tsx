import { createElement, type ReactNode } from "react";
import { highlightInlineNodes, type InlineNode, parseInlineMarkup } from "@/lib/rich-inline-text";

/** 检索词高亮底色（hi-fi mark：--selection-bg）。 */
const MARK_CLASS = "rounded-[2px] bg-(--selection-bg) px-px text-inherit";

/** InlineNode 树 → React 元素：文本节点由 React 自动转义；白名单元素零属性，仅 mark 带高亮样式。 */
function renderNodes(nodes: InlineNode[]): ReactNode[] {
  // 解析结果静态、不增删不重排，index key 安全
  return nodes.map((node, index) =>
    node.kind === "text"
      ? node.value
      : createElement(
          node.tag,
          node.tag === "mark" ? { key: index, className: MARK_CLASS } : { key: index },
          renderNodes(node.children),
        ),
  );
}

/**
 * 白名单内联富文本：安全渲染 PubMed 标题/摘要中的排版标签（i/b/sub/sup/u）
 * 与 MathML 公式；白名单外内容显示为字面文本。零布局样式，嵌入现有排版即插即用。
 * highlight：检索词（大小写不敏感），命中片段渲染为 mark。
 * 调用方须知：返回 Fragment（非包裹元素），字体/字号/行高等样式由调用方的包裹元素提供；
 * 返回 ReactNode，不可用于只接受字符串的位置（title= / aria-label / metadata），那类场景需另行降级为纯文本。
 */
export function RichInlineText({ text, highlight }: { text: string; highlight?: string }) {
  const nodes = parseInlineMarkup(text);
  return <>{renderNodes(highlight ? highlightInlineNodes(nodes, highlight) : nodes)}</>;
}
