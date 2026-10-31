// patra-learn/src/teaching/layout.ts
// 图示的几何计算：作者只写网格位置（列 / 行从 1 数），坐标在这里算出来。纯函数，单测在 layout.test.ts

export interface Box {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface GridOptions {
  width: number;
  nodeHeight: number;
  rowGap: number;
  /// 节点最宽多少
  maxNodeWidth?: number;
  /// 相邻两列节点之间至少留多宽
  gap?: number;
}

/// 按列均分宽度、按行叠放，节点在格子里水平居中。
/// 节点带了 width 时改为按内容定列宽：每列取这一列最宽的节点，两端对齐，空当平均分给列间（标签放得下）
export function gridLayout(
  items: { id: string; col: number; row?: number | undefined; width?: number }[],
  { width, nodeHeight, rowGap, maxNodeWidth = 150, gap = 24 }: GridOptions,
): { boxes: Record<string, Box>; height: number } {
  const cols = Math.max(1, ...items.map((i) => i.col));
  const rows = Math.max(1, ...items.map((i) => i.row ?? 1));
  const top = (item: { row?: number | undefined }) => ((item.row ?? 1) - 1) * (nodeHeight + rowGap);
  const boxes: Record<string, Box> = {};
  const height = rows * nodeHeight + (rows - 1) * rowGap;
  if (items.every((i) => i.width !== undefined)) {
    const colWidths = Array.from({ length: cols }, (_, c) =>
      Math.max(0, ...items.filter((i) => i.col === c + 1).map((i) => i.width ?? 0)),
    );
    const spare = width - colWidths.reduce((a, b) => a + b, 0);
    const between = cols > 1 ? spare / (cols - 1) : 0;
    const lead = cols > 1 ? 0 : spare / 2;
    const colX = colWidths.map(
      (_, c) => lead + colWidths.slice(0, c).reduce((a, b) => a + b, 0) + c * between,
    );
    for (const item of items) {
      const w = item.width ?? 0;
      const cw = colWidths[item.col - 1] ?? w;
      boxes[item.id] = {
        x: (colX[item.col - 1] ?? 0) + (cw - w) / 2,
        y: top(item),
        w,
        h: nodeHeight,
      };
    }
    return { boxes, height };
  }
  const colWidth = width / cols;
  const w = Math.min(maxNodeWidth, colWidth - gap);
  for (const item of items) {
    boxes[item.id] = {
      x: (item.col - 1) * colWidth + (colWidth - w) / 2,
      y: top(item),
      w,
      h: nodeHeight,
    };
  }
  return { boxes, height };
}

/// 粗估一行文字的宽度（px）：中日韩字符按全角，其余按半角；等宽字体每字 0.608 em
export function estimateTextWidth(text: string, fontSize: number, mono = false): number {
  let units = 0;
  for (const ch of text)
    units += mono ? 0.608 : /[\u2e80-\u9fff\uff00-\uffef\u3000-\u303f]/.test(ch) ? 1 : 0.5;
  return Math.round(units * fontSize * 100) / 100;
}

/// 节点方块的宽度：取标题与小字里较宽的那行，加内边距，限制在 [min, max]
export function nodeWidth(
  node: { label: string; sub?: string | undefined; code?: boolean | undefined },
  { min = 96, max = 172 }: { min?: number; max?: number } = {},
): number {
  const label = estimateTextWidth(node.label, node.code ? 12.5 : 14, node.code);
  const sub = node.sub ? estimateTextWidth(node.sub, 12.5) : 0;
  return Math.min(max, Math.max(min, Math.ceil(Math.max(label, sub)) + 28));
}

/// 一行均分：逐步动画、时序图的参与者用
export function rowLayout(
  ids: string[],
  {
    width,
    nodeHeight,
    y = 0,
    nodeWidth = 120,
  }: { width: number; nodeHeight: number; y?: number; nodeWidth?: number },
): Record<string, Box> {
  const colWidth = width / Math.max(1, ids.length);
  const w = Math.min(nodeWidth, colWidth - 12);
  const boxes: Record<string, Box> = {};
  ids.forEach((id, i) => {
    boxes[id] = { x: i * colWidth + (colWidth - w) / 2, y, w, h: nodeHeight };
  });
  return boxes;
}

export interface Line {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

export interface LabelPlacement {
  x: number;
  y: number;
  /// 标签在线的哪一侧：above 底边贴着线上方、below 顶边贴着线下方、right 左边贴着线右侧
  side: "above" | "below" | "right";
  /// 标签最宽多少，超出就折行，避免盖住两端的方块
  maxWidth: number;
}

/// 连线标签放哪：偏横的线放上方（或下方，用于一对反向连线里往下错开的那条），偏竖的线放右侧
export function labelPlacement(line: Line, prefer: "above" | "below" = "above"): LabelPlacement {
  const dx = line.x2 - line.x1;
  const dy = line.y2 - line.y1;
  const x = (line.x1 + line.x2) / 2;
  const y = (line.y1 + line.y2) / 2;
  if (Math.abs(dx) >= Math.abs(dy)) {
    return {
      x,
      y: prefer === "above" ? y - 4 : y + 4,
      side: prefer,
      maxWidth: Math.max(48, Math.round(Math.abs(dx))),
    };
  }
  return { x: x + 8, y, side: "right", maxWidth: 120 };
}

/// 连线终点的三角箭头（SVG polygon 的 points）
export function arrowHead({ x1, y1, x2, y2 }: Line, size = 7): string {
  const len = Math.hypot(x2 - x1, y2 - y1) || 1;
  const dx = (x2 - x1) / len;
  const dy = (y2 - y1) / len;
  const bx = x2 - dx * size;
  const by = y2 - dy * size;
  const half = size / 2;
  // + 0 把 -0 变成 0
  const fmt = (n: number) => String(Math.round(n * 100) / 100 + 0);
  return [
    `${fmt(x2)},${fmt(y2)}`,
    `${fmt(bx - dy * half)},${fmt(by + dx * half)}`,
    `${fmt(bx + dy * half)},${fmt(by - dx * half)}`,
  ].join(" ");
}

/// 从中心出发的射线与方块边框的交点距中心多远
function exitDistance(box: Box, dx: number, dy: number): number {
  const tx = dx === 0 ? Number.POSITIVE_INFINITY : box.w / 2 / Math.abs(dx);
  const ty = dy === 0 ? Number.POSITIVE_INFINITY : box.h / 2 / Math.abs(dy);
  return Math.min(tx, ty);
}

/// 两个方块之间的连线：沿中心连线，从 a 的边框到 b 的边框；offset 沿法向平移（错开一对反向连线）
export function edgeLine(a: Box, b: Box, offset = 0): Line {
  const ax = a.x + a.w / 2;
  const ay = a.y + a.h / 2;
  const bx = b.x + b.w / 2;
  const by = b.y + b.h / 2;
  const len = Math.hypot(bx - ax, by - ay) || 1;
  const dx = (bx - ax) / len;
  const dy = (by - ay) / len;
  const ox = -dy * offset;
  const oy = dx * offset;
  const ta = exitDistance(a, dx, dy);
  const tb = exitDistance(b, dx, dy);
  const round = (n: number) => Math.round(n * 100) / 100;
  return {
    x1: round(ax + dx * ta + ox),
    y1: round(ay + dy * ta + oy),
    x2: round(bx - dx * tb + ox),
    y2: round(by - dy * tb + oy),
  };
}
