// patra-learn/src/teaching/state-machine.tsx
// 状态机（画板 C-StateMachine）：点事件推进状态，当前状态弹一下；
// 点了当前状态下不会发生的事件，说明条抖动变红，并列出现在能发生的事件（由转移表推出，作者不写）
"use client";

import { useState } from "react";
import {
  type DrawnEdge,
  EdgeLabel,
  EdgeLayer,
  type Emphasis,
  NodeBox,
  placeLabel,
  STAGE_WIDTH,
  Stage,
} from "./diagram";
import { edgeLine, gridLayout, nodeWidth } from "./layout";
import { pop, useMotionOnChange } from "./motion";
import type { PropsOf } from "./registry";
import { Button, Caption, cx, Inline, Meta, Widget } from "./ui";

interface MachineState {
  current: string;
  path: string[];
  /// 上一次成功发生的事件（连线高亮用）
  last: string | null;
  /// 点了不会发生的事件；count 让连续点同一个也能再抖一次
  rejected: { event: string; count: number } | null;
}

/// 当前状态弹一下的外框：current 变化时对它播 spring
function PopFrame({ motionKey, children }: { motionKey: string; children: React.ReactNode }) {
  const ref = useMotionOnChange<HTMLDivElement>(
    (el) => pop(el?.querySelector('[data-state="on"]') ?? null),
    motionKey,
  );
  return (
    <div ref={ref} className="contents">
      {children}
    </div>
  );
}

export function StateMachine({
  title,
  subject,
  states,
  start,
  events,
  transitions,
}: PropsOf<"StateMachine">) {
  const initial: MachineState = { current: start, path: [start], last: null, rejected: null };
  const [s, setS] = useState<MachineState>(initial);
  const { boxes, height: gridHeight } = gridLayout(
    states.map((st) => ({ ...st, width: nodeWidth(st, { min: 88, max: 128 }) })),
    { width: STAGE_WIDTH, nodeHeight: 56, rowGap: 80 },
  );
  for (const b of Object.values(boxes)) b.y += 8;
  const height = gridHeight + 16;

  const label = (id: string) => states.find((x) => x.id === id)?.label ?? id;
  const eventLabel = (id: string) => events.find((e) => e.id === id)?.label ?? id;
  const allowed = transitions.filter((t) => t.from === s.current);
  const fire = (event: string) => {
    const t = allowed.find((x) => x.event === event);
    if (!t) {
      setS({ ...s, rejected: { event, count: (s.rejected?.count ?? 0) + 1 } });
      return;
    }
    setS({ current: t.to, path: [...s.path, t.to], last: `${t.from}:${t.event}`, rejected: null });
  };

  const transitionKey = (t: { from: string; event: string }) => `${t.from}:${t.event}`;
  const edgeEmphasis = (t: { from: string; event: string }): Emphasis =>
    s.last === transitionKey(t) ? "on" : t.from === s.current ? "normal" : "dim";
  const drawable = transitions.filter((t) => t.from !== t.to);
  const drawn: DrawnEdge[] = drawable.flatMap((t) => {
    const a = boxes[t.from];
    const b = boxes[t.to];
    if (!a || !b) return [];
    const reverse = drawable.some((x) => x.from === t.to && x.to === t.from);
    return [
      { key: transitionKey(t), line: edgeLine(a, b, reverse ? 8 : 0), emphasis: edgeEmphasis(t) },
    ];
  });

  const currentState = states.find((x) => x.id === s.current);
  const caption = s.rejected
    ? {
        lead: "这个状态下不会发生　",
        text: `「${eventLabel(s.rejected.event)}」不会发生在「${label(s.current)}」。现在能发生的是：${
          allowed.map((t) => `「${eventLabel(t.event)}」`).join("、") || "没有了，点「从头来」"
        }。`,
      }
    : { lead: `${currentState?.label ?? s.current}　`, text: currentState?.desc ?? "" };

  return (
    <Widget
      label={title}
      title={title}
      kind={subject ? `状态机 · ${subject}` : "状态机"}
      foot={
        <>
          <Meta>走过：{s.path.map(label).join(" → ")}</Meta>
          <Button kind="quiet" onClick={() => setS(initial)}>
            从头来
          </Button>
        </>
      }
    >
      <Stage height={height}>
        <EdgeLayer edges={drawn} height={height} />
        <PopFrame motionKey={s.path.join(">")}>
          {states.map((st) => {
            const box = boxes[st.id];
            if (!box) return null;
            const emphasis: Emphasis =
              st.id === s.current ? "on" : s.path.includes(st.id) ? "normal" : "dim";
            return (
              <NodeBox
                key={st.id}
                testId={`state-${st.id}`}
                box={box}
                label={st.label}
                sub={st.sub}
                dashed={st.dashed}
                emphasis={emphasis}
              />
            );
          })}
        </PopFrame>
        {drawn.map((d) => {
          const t = drawable.find((x) => transitionKey(x) === d.key);
          if (!t) return null;
          return (
            <EdgeLabel
              key={d.key}
              placement={placeLabel(
                d.line,
                drawable.some((x) => x.from === t.to && x.to === t.from),
              )}
              text={t.label ?? eventLabel(t.event)}
              emphasis={d.emphasis}
            />
          );
        })}
      </Stage>
      <Caption
        lead={caption.lead}
        tone={s.rejected ? "bad" : "normal"}
        motionKey={s.rejected ? `bad:${s.rejected.count}` : s.path.join(">")}
      >
        <Inline text={caption.text} />
      </Caption>
      <div className="flex flex-col gap-2">
        <Meta>发生了什么？点一个事件</Meta>
        <div className="flex flex-wrap gap-2">
          {events.map((e) => (
            <Button
              key={e.id}
              onClick={() => fire(e.id)}
              className={cx(!allowed.some((t) => t.event === e.id) && "border-dashed text-ink-4")}
            >
              {e.label}
            </Button>
          ))}
        </div>
      </div>
    </Widget>
  );
}
