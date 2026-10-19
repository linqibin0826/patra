import { createElement, type ReactNode } from "react";
import { type MathNode, parseMathml } from "@/lib/mathml";

function renderNodes(nodes: MathNode[]): ReactNode[] {
  // 解析结果静态、不增删不重排，index key 安全
  return nodes.map((node, index) => {
    if (node.kind === "text") {
      return node.value;
    }
    // MathML 属性名都是小写，React 原样写成元素属性（rowspan 也不会被当成 HTML 的 rowSpan）
    return createElement(node.tag, { key: index, ...node.attrs }, ...renderNodes(node.children));
  });
}

/**
 * 公式：后端给的 MathML 字符串经白名单解析后，由浏览器原生排版（release spec 决策 C）。
 * 只渲染 `<math>` 本身，行内 / 行间的容器样式由调用方决定。
 */
export function MathMl({ mathml }: { mathml: string }) {
  return <>{renderNodes(parseMathml(mathml))}</>;
}
