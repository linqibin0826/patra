// patra-learn/src/teaching/diagram.tsx
// 图示共用件：画布、节点方块、连线层、连线标签（结构图、状态机、时序图、逐步动画共用）
"use client";

import type { ReactNode } from "react";
import { arrowHead, type Box, type LabelPlacement, type Line, labelPlacement } from "./layout";
import { useSlide } from "./motion";
import { cx, Inline } from "./ui";

/// 图示画布宽度：680 的版心减去组件内边距
export const STAGE_WIDTH = 632;

export type Emphasis = "normal" | "on" | "dim" | "bad";

/// 画布：固定宽度、相对定位；窄屏时横向滚动，不撑破页面
export function Stage({ height, children }: { height: number; children: ReactNode }) {
  return (
    <div className="-mx-1 overflow-x-auto px-1 py-1">
      <div className="relative mx-auto" style={{ width: STAGE_WIDTH, height }}>
        {children}
      </div>
    </div>
  );
}

const NODE_CLASS: Record<Emphasis, string> = {
  normal: "border-line bg-surface",
  on: "border-teal bg-teal-tint shadow-[0_0_0_4px_rgba(14,143,163,0.14)]",
  dim: "border-line bg-surface opacity-38",
  bad: "border-wrong bg-wrong-bg",
};

/// 节点方块；给了 onClick 就是按钮
export function NodeBox({
  box,
  emphasis,
  label,
  sub,
  code,
  dashed,
  onClick,
  testId,
  nodeRef,
}: {
  box: Box;
  emphasis: Emphasis;
  label: string;
  sub?: string | undefined;
  code?: boolean | undefined;
  dashed?: boolean | undefined;
  onClick?: () => void;
  testId?: string;
  nodeRef?: (el: HTMLElement | null) => void;
}) {
  const className = cx(
    "absolute box-border flex items-center justify-center rounded-inner border-[1.5px] p-2 text-center font-ui text-ink text-sm leading-[1.45] transition-[border-color,background-color,box-shadow,opacity] duration-280 ease-out",
    NODE_CLASS[emphasis],
    dashed && "border-dashed",
    onClick &&
      "cursor-pointer hover:border-teal-mark focus-visible:outline-2 focus-visible:outline-teal-mark",
  );
  const content = (
    <span className="flex flex-col">
      <b className={cx("font-semibold", code && "font-medium font-mono text-[12.5px]")}>{label}</b>
      {sub ? (
        <span className="text-[12.5px] text-ink-4">
          <Inline text={sub} />
        </span>
      ) : null}
    </span>
  );
  const style = { left: box.x, top: box.y, width: box.w, height: box.h };
  return onClick ? (
    <button
      ref={nodeRef}
      type="button"
      data-testid={testId}
      data-state={emphasis}
      className={className}
      style={style}
      onClick={onClick}
    >
      {content}
    </button>
  ) : (
    <div
      ref={nodeRef}
      data-testid={testId}
      data-state={emphasis}
      className={className}
      style={style}
    >
      {content}
    </div>
  );
}

const EDGE_STROKE: Record<Emphasis, string> = {
  normal: "stroke-ink-5 fill-ink-5",
  on: "stroke-teal fill-teal",
  dim: "stroke-ink-5 fill-ink-5 opacity-30",
  bad: "stroke-wrong fill-wrong",
};

export interface DrawnEdge {
  key: string;
  line: Line;
  emphasis: Emphasis;
  arrow?: boolean;
  dashed?: boolean | undefined;
}

/// 全部连线画在一层 SVG 里，节点盖在上面
export function EdgeLayer({ edges, height }: { edges: DrawnEdge[]; height: number }) {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute top-0 left-0 overflow-visible"
      width={STAGE_WIDTH}
      height={height}
    >
      {edges.map((e) => (
        <g
          key={e.key}
          data-testid={`edge-${e.key}`}
          data-state={e.emphasis}
          className={cx(
            "transition-[stroke,fill,opacity] duration-280 ease-out",
            EDGE_STROKE[e.emphasis],
          )}
        >
          <line
            x1={e.line.x1}
            y1={e.line.y1}
            x2={e.line.x2}
            y2={e.line.y2}
            strokeWidth={2}
            strokeLinecap="round"
            strokeDasharray={e.dashed ? "6 6" : undefined}
          />
          {e.arrow === false ? null : <polygon points={arrowHead(e.line)} stroke="none" />}
        </g>
      ))}
    </svg>
  );
}

const PLACEMENT_CLASS: Record<LabelPlacement["side"], string> = {
  above: "-translate-x-1/2 -translate-y-full",
  below: "-translate-x-1/2",
  right: "-translate-y-1/2 text-left",
};

const LABEL_CLASS: Record<Emphasis, string> = {
  normal: "text-ink-4",
  on: "font-semibold text-teal-deep",
  dim: "text-ink-4 opacity-35",
  bad: "font-semibold text-wrong",
};

/// 连线旁的标签：带底色压住穿过的线，过长折行；给了 onClick 就能点
export function EdgeLabel({
  placement,
  text,
  emphasis,
  onClick,
  testId,
}: {
  placement: LabelPlacement;
  text: string;
  emphasis: Emphasis;
  onClick?: (() => void) | undefined;
  testId?: string;
}) {
  const className = cx(
    "absolute w-max rounded-[4px] bg-surface px-1.5 text-center font-ui text-[12.5px] leading-4 transition-[color,opacity] duration-280 ease-out",
    PLACEMENT_CLASS[placement.side],
    LABEL_CLASS[emphasis],
    onClick && "cursor-pointer underline decoration-dotted underline-offset-2 hover:text-teal-deep",
  );
  const style = { left: placement.x, top: placement.y, maxWidth: placement.maxWidth };
  return onClick ? (
    <button
      type="button"
      data-testid={testId}
      className={className}
      style={style}
      onClick={onClick}
    >
      {text}
    </button>
  ) : (
    <span data-testid={testId} className={className} style={style}>
      {text}
    </span>
  );
}

/// 一条连线的标签位置：一对反向连线里，往下错开的那条（朝右的）把标签放到线下方
export function placeLabel(line: Line, hasReverse: boolean): LabelPlacement {
  return labelPlacement(line, hasReverse && line.x2 > line.x1 ? "below" : "above");
}

/// 移动的消息块：位置变了就从旧位置滑过去（dur-3，power2.inOut），前进后退同一条路
export function Packet({
  label,
  state,
  x,
  y,
  testId,
}: {
  label: string;
  state?: "faded" | "dropped" | undefined;
  x: number;
  y: number;
  testId: string;
}) {
  const ref = useSlide<HTMLDivElement>(x, y);
  return (
    <div
      ref={ref}
      data-testid={testId}
      data-state={state ?? "normal"}
      className={cx(
        "pointer-events-none absolute box-border flex h-[26px] items-center justify-center whitespace-nowrap rounded-chip px-2 font-medium font-mono text-[11.5px] transition-[background-color,color,opacity] duration-280 ease-out",
        state === "dropped"
          ? "bg-wrong-bg text-wrong line-through"
          : "bg-teal text-white shadow-[0_2px_6px_rgba(11,111,126,0.3)]",
        state === "faded" && "opacity-35",
      )}
      style={{ left: x, top: y, minWidth: 72 }}
    >
      {label}
    </div>
  );
}
