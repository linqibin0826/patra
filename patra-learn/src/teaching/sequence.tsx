// patra-learn/src/teaching/sequence.tsx
// 时序图（画板 C-Sequence）：一条一条往下走，没走到的变淡，卡住的那条标红；点任意一条跳过去
"use client";

import { useState } from "react";
import { type DrawnEdge, EdgeLayer, type Emphasis, NodeBox, STAGE_WIDTH, Stage } from "./diagram";
import { rowLayout } from "./layout";
import type { PropsOf } from "./registry";
import { Button, Caption, cx, Inline, Meta, Widget } from "./ui";

const HEAD = 52;
const FIRST_ROW = HEAD + 40;
const ROW = 44;

const LABEL_CLASS: Record<Emphasis, string> = {
  normal: "text-ink-4",
  on: "font-semibold text-teal-deep",
  dim: "text-ink-4 opacity-35",
  bad: "font-semibold text-wrong",
};

export function Sequence({ title, actors, messages, summary }: PropsOf<"Sequence">) {
  const [cur, setCur] = useState(0);
  const total = messages.length;
  const done = cur >= total;
  const boxes = rowLayout(
    actors.map((a) => a.id),
    { width: STAGE_WIDTH, nodeHeight: HEAD, nodeWidth: 128 },
  );
  const centerX = (id: string) => {
    const b = boxes[id];
    return b ? b.x + b.w / 2 : 0;
  };
  const height = FIRST_ROW + total * ROW;

  const stateOf = (i: number): Emphasis => {
    const m = messages[i];
    if (!done && i > cur) return "dim";
    if (m?.bad) return "bad";
    return !done && i === cur ? "on" : "normal";
  };

  const lifelines: DrawnEdge[] = actors.map((a) => ({
    key: `life-${a.id}`,
    line: { x1: centerX(a.id), y1: HEAD, x2: centerX(a.id), y2: height },
    emphasis: "normal",
    arrow: false,
    dashed: true,
  }));
  const arrows: DrawnEdge[] = messages.map((m, i) => {
    const x1 = centerX(m.from);
    const x2 = centerX(m.to);
    const dir = Math.sign(x2 - x1);
    const y = FIRST_ROW + i * ROW + 14;
    return {
      key: `message-line-${i}`,
      line: { x1: x1 + dir * 4, y1: y, x2: x2 - dir * 4, y2: y },
      emphasis: stateOf(i),
      dashed: m.dashed,
    };
  });

  const current = done ? undefined : messages[cur];
  const lead = done ? summary?.time : current?.time;
  const text = done ? (summary?.caption ?? "") : (current?.caption ?? "");
  const atLast = cur === total - 1;

  return (
    <Widget
      label={title}
      title={title}
      kind={
        <>
          时序图 · <span>{done ? `全部 ${total} 条` : `第 ${cur + 1} 条 / ${total}`}</span>
        </>
      }
      foot={
        <>
          <Meta>点图上任意一条消息，也能跳到那一步</Meta>
          <span className="flex gap-2">
            <Button disabled={cur === 0} onClick={() => setCur((c) => Math.max(0, c - 1))}>
              上一条
            </Button>
            <Button
              kind="primary"
              onClick={() => setCur((c) => (done || (atLast && !summary) ? 0 : c + 1))}
            >
              {done || (atLast && !summary) ? "从头看" : atLast ? "看总结" : "下一条"}
            </Button>
          </span>
        </>
      }
    >
      <Stage height={height}>
        <EdgeLayer edges={[...lifelines, ...arrows]} height={height} />
        {actors.map((a) => {
          const box = boxes[a.id];
          return box ? (
            <NodeBox key={a.id} box={box} label={a.label} sub={a.sub} emphasis="normal" />
          ) : null;
        })}
        {messages.map((m, i) => {
          const x1 = centerX(m.from);
          const x2 = centerX(m.to);
          const state = stateOf(i);
          return (
            <button
              // biome-ignore lint/suspicious/noArrayIndexKey: 消息按顺序排，下标就是身份
              key={i}
              type="button"
              data-testid={`message-${i}`}
              data-state={state}
              onClick={() => setCur(i)}
              className={cx(
                "-translate-y-1/2 absolute flex cursor-pointer justify-center border-0 bg-transparent p-0 font-ui text-[12.5px] leading-4 transition-[color,opacity] duration-280 ease-out hover:text-teal-deep",
                LABEL_CLASS[state],
              )}
              style={{ left: Math.min(x1, x2), width: Math.abs(x2 - x1), top: FIRST_ROW + i * ROW }}
            >
              <span className="rounded-[4px] bg-surface px-1.5">
                {i + 1} · <Inline text={m.label} />
              </span>
            </button>
          );
        })}
      </Stage>
      <Caption lead={lead} motionKey={cur}>
        <Inline text={text} />
      </Caption>
    </Widget>
  );
}
