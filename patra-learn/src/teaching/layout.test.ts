import { describe, expect, it } from "vitest";
import {
  arrowHead,
  edgeLine,
  estimateTextWidth,
  gridLayout,
  labelPlacement,
  rowLayout,
} from "./layout";

describe("按内容定列宽", () => {
  it("估算文字宽度：中文按全角，等宽按每字 7.6 px", () => {
    expect(estimateTextWidth("交换机", 14)).toBe(42);
    expect(estimateTextWidth("abcd", 14)).toBe(28);
    expect(estimateTextWidth("abcd", 12.5, true)).toBeCloseTo(30.4);
  });

  it("给了各列宽度时两端对齐，剩下的空当平均分给列间", () => {
    const { boxes } = gridLayout(
      [
        { id: "a", col: 1, width: 100 },
        { id: "b", col: 2, width: 160 },
        { id: "c", col: 3, width: 120 },
      ],
      { width: 600, nodeHeight: 60, rowGap: 60 },
    );
    expect(boxes.a).toEqual({ x: 0, y: 0, w: 100, h: 60 });
    expect(boxes.b?.x).toBe(210);
    expect(boxes.c?.x).toBe(480);
  });

  it("各列加起来比画布宽时按比例缩窄，列间至少留 24 px，方块不重叠", () => {
    const { boxes } = gridLayout(
      ["a", "b", "c", "d"].map((id, i) => ({ id, col: i + 1, width: 172 })),
      { width: 632, nodeHeight: 60, rowGap: 60 },
    );
    const xs = ["a", "b", "c", "d"].map((id) => boxes[id] as { x: number; w: number });
    for (let i = 1; i < xs.length; i++) {
      const prev = xs[i - 1] as { x: number; w: number };
      expect((xs[i] as { x: number }).x - (prev.x + prev.w)).toBeGreaterThanOrEqual(24);
    }
    const last = xs[3] as { x: number; w: number };
    expect(last.x + last.w).toBeLessThanOrEqual(632);
  });

  it("同一列里宽度不同的节点按这一列最宽的居中", () => {
    const { boxes } = gridLayout(
      [
        { id: "a", col: 1, width: 100 },
        { id: "b", col: 1, row: 2, width: 60 },
        { id: "c", col: 2, width: 100 },
      ],
      { width: 400, nodeHeight: 50, rowGap: 30 },
    );
    expect(boxes.b?.x).toBe(20);
  });
});

describe("连线标签的位置", () => {
  it("横线：标签放在线的上方，最宽不超过线长", () => {
    expect(labelPlacement({ x1: 100, y1: 25, x2: 200, y2: 25 })).toEqual({
      x: 150,
      y: 21,
      side: "above",
      maxWidth: 100,
    });
  });

  it("横线且往下错开过（一对反向连线里的那条）：标签放在线的下方", () => {
    expect(labelPlacement({ x1: 200, y1: 33, x2: 100, y2: 33 }, "below").side).toBe("below");
  });

  it("竖线：标签放在线的右侧", () => {
    expect(labelPlacement({ x1: 50, y1: 50, x2: 50, y2: 150 })).toEqual({
      x: 58,
      y: 100,
      side: "right",
      maxWidth: 120,
    });
  });
});

describe("箭头", () => {
  it("箭头尖在终点，两翼沿来向退回", () => {
    expect(arrowHead({ x1: 0, y1: 0, x2: 100, y2: 0 }, 8)).toBe("100,0 92,4 92,-4");
  });
});

describe("网格布局", () => {
  it("按列均分宽度，节点在格子里居中", () => {
    const { boxes, height } = gridLayout(
      [
        { id: "a", col: 1 },
        { id: "b", col: 2 },
      ],
      { width: 600, nodeHeight: 60, rowGap: 60 },
    );
    expect(boxes.a).toEqual({ x: 75, y: 0, w: 150, h: 60 });
    expect(boxes.b).toEqual({ x: 375, y: 0, w: 150, h: 60 });
    expect(height).toBe(60);
  });

  it("行从 1 数，第 2 行往下移一行加行距", () => {
    const { boxes, height } = gridLayout(
      [
        { id: "a", col: 1 },
        { id: "b", col: 1, row: 2 },
      ],
      { width: 300, nodeHeight: 50, rowGap: 30 },
    );
    expect(boxes.b?.y).toBe(80);
    expect(height).toBe(130);
  });

  it("节点宽度不超过上限，留出格子间距", () => {
    const { boxes } = gridLayout(
      [
        { id: "a", col: 1 },
        { id: "b", col: 4 },
      ],
      { width: 640, nodeHeight: 50, rowGap: 30, maxNodeWidth: 120, gap: 24 },
    );
    expect(boxes.a?.w).toBe(120 - 0);
    expect(boxes.a?.x).toBe(20);
  });
});

describe("一行布局", () => {
  it("n 个节点均分宽度", () => {
    const boxes = rowLayout(["a", "b", "c"], { width: 600, nodeHeight: 64, y: 70 });
    expect(boxes.b).toEqual({ x: 240, y: 70, w: 120, h: 64 });
  });
});

describe("连线端点", () => {
  const a = { x: 0, y: 0, w: 100, h: 50 };
  const b = { x: 200, y: 0, w: 100, h: 50 };

  it("同一行的两个方块：从右边框连到左边框", () => {
    expect(edgeLine(a, b)).toEqual({ x1: 100, y1: 25, x2: 200, y2: 25 });
  });

  it("同一列：从下边框连到上边框", () => {
    const c = { x: 0, y: 100, w: 100, h: 50 };
    expect(edgeLine(a, c)).toEqual({ x1: 50, y1: 50, x2: 50, y2: 100 });
  });

  it("有反向连线时，两条线沿法向错开", () => {
    const line = edgeLine(a, b, 8);
    expect(line.y1).toBe(33);
    expect(line.y2).toBe(33);
  });
});
