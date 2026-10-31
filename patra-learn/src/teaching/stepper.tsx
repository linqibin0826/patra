// patra-learn/src/teaching/stepper.tsx
// 逐步动画 + 你猜会怎样（画板 C-Stepper）：读者点「下一步」才往前走，不自动连播；
// 关键步骤前停下问「你猜」，答完（或「不猜，直接看」）才能继续；消息块在节点间滑动，「上一步」原路滑回
"use client";

import { useState } from "react";
import {
  type DrawnEdge,
  EdgeLabel,
  EdgeLayer,
  type Emphasis,
  NodeBox,
  Packet,
  placeLabel,
  STAGE_WIDTH,
  Stage,
} from "./diagram";
import { edgeLine, rowLayout } from "./layout";
import { STEPPER_START, type StepperState, stepperNext, stepperPrev } from "./logic";
import type { PropsOf } from "./registry";
import {
  answeredStates,
  Button,
  Caption,
  cx,
  Inline,
  letterIndex,
  OptionList,
  Reveal,
  StepDots,
  type Tone,
  Verdict,
  Widget,
} from "./ui";

const LANE = 32;
const NODE_HEIGHT = 64;
const NOTE_GAP = 26;
const NOTE_HEIGHT = 40;

const NOTE_CLASS: Record<Tone | "neutral", string> = {
  neutral: "border-dashed border-ink-5 text-ink-4",
  wait: "border-amber bg-amber-bg font-semibold text-amber",
  ok: "border-correct bg-correct-bg font-semibold text-correct",
  bad: "border-wrong bg-wrong-bg font-semibold text-wrong",
};

export function Stepper({ title, nodes, links = [], steps, guesses = [] }: PropsOf<"Stepper">) {
  const [s, setS] = useState<StepperState>(STEPPER_START);
  const step = steps[s.step] ?? steps[0];
  if (!step) return null;

  const tokenIds = [...new Set(steps.flatMap((x) => (x.tokens ?? []).map((t) => t.id)))];
  const nodeY = 8 + Math.max(1, tokenIds.length) * LANE;
  const hasNotes = steps.some((x) => (x.notes ?? []).length > 0);
  const height = nodeY + NODE_HEIGHT + (hasNotes ? NOTE_GAP + NOTE_HEIGHT : 0) + 8;
  const boxes = rowLayout(
    nodes.map((n) => n.id),
    { width: STAGE_WIDTH, nodeHeight: NODE_HEIGHT, y: nodeY, nodeWidth: 120 },
  );

  const focus = new Set(step.focus ?? []);
  const litLinks = new Set(step.links ?? []);
  const nodeEmphasis = (id: string): Emphasis =>
    focus.size === 0 ? "normal" : focus.has(id) ? "on" : "dim";
  const drawn: DrawnEdge[] = links.flatMap((l) => {
    const a = boxes[l.from];
    const b = boxes[l.to];
    if (!a || !b) return [];
    const key = `${l.from}>${l.to}`;
    return [
      {
        key: `${l.from}-${l.to}`,
        line: edgeLine(a, b),
        emphasis: litLinks.has(key) ? "on" : "normal",
      },
    ];
  });

  const guess = s.gate === null ? undefined : guesses[s.gate];
  const answered = s.picked !== null;
  const last = s.step === steps.length - 1;
  const next = () => setS((cur) => stepperNext(cur, steps.length, guesses));

  return (
    <Widget
      label={title}
      title={title}
      kind={`逐步动画 · 第 ${s.step + 1} 步 / ${steps.length}`}
      foot={
        <>
          <StepDots total={steps.length} current={s.step} />
          <span className="flex items-center gap-2">
            {guess && !answered ? (
              <Button
                kind="quiet"
                onClick={() => setS({ ...s, picked: letterIndex(guess.answer) })}
              >
                不猜，直接看
              </Button>
            ) : null}
            <Button disabled={s.step === 0 && !guess} onClick={() => setS(stepperPrev)}>
              上一步
            </Button>
            <Button kind="primary" disabled={Boolean(guess) && !answered} onClick={next}>
              {guess ? (answered ? "看看是不是" : "先选一个") : last ? "重新播放" : "下一步"}
            </Button>
          </span>
        </>
      }
    >
      <Stage height={height}>
        <EdgeLayer edges={drawn} height={height} />
        {nodes.map((n) => {
          const box = boxes[n.id];
          return box ? (
            <NodeBox
              key={n.id}
              box={box}
              label={n.label}
              sub={n.sub}
              emphasis={nodeEmphasis(n.id)}
            />
          ) : null;
        })}
        {links.map((l) => {
          const d = drawn.find((x) => x.key === `${l.from}-${l.to}`);
          return l.label && d ? (
            <EdgeLabel
              key={d.key}
              placement={placeLabel(d.line, false)}
              text={l.label}
              emphasis={d.emphasis}
              testId={`link-label-${d.key}`}
            />
          ) : null;
        })}
        {(step.notes ?? []).map((note) => {
          const box = boxes[note.at];
          return box ? (
            <Reveal
              key={note.at}
              motionKey={note.text}
              style={{
                left: box.x,
                top: box.y + NODE_HEIGHT + NOTE_GAP,
                width: box.w,
                height: NOTE_HEIGHT,
              }}
              className={cx(
                "absolute box-border flex items-center justify-center rounded-lg border px-2 text-center font-ui text-[12.5px] transition-colors duration-280",
                NOTE_CLASS[note.tone ?? "neutral"],
              )}
            >
              <Inline text={note.text} />
            </Reveal>
          ) : null;
        })}
        {(step.tokens ?? []).map((t) => {
          const box = boxes[t.at];
          const lane = tokenIds.indexOf(t.id);
          return box ? (
            <Packet
              key={t.id}
              testId={`token-${t.id}`}
              label={t.label}
              state={t.state}
              x={box.x + box.w / 2 - 36}
              y={4 + lane * LANE}
            />
          ) : null;
        })}
      </Stage>
      <Caption lead={s.step + 1} motionKey={s.step}>
        <Inline text={step.caption} />
      </Caption>
      {guess ? (
        <Reveal className="flex flex-col gap-2.5 rounded-card bg-teal-wash px-4.5 py-4">
          <span className="font-semibold font-ui text-meta text-teal">你猜会怎样？</span>
          <span className="font-display font-semibold text-[16.5px] leading-[1.65]">
            <Inline text={guess.question} />
          </span>
          <OptionList
            options={guess.options}
            states={
              answered
                ? answeredStates(guess.options.length, letterIndex(guess.answer), s.picked)
                : []
            }
            disabled={answered}
            onPick={(i) => setS({ ...s, picked: i })}
          />
          {answered ? (
            <Verdict
              tone={s.picked === letterIndex(guess.answer) ? "ok" : "bad"}
              head={s.picked === letterIndex(guess.answer) ? "猜对了。" : "不是这样。"}
            >
              <Inline text={guess.explain} />
            </Verdict>
          ) : null}
        </Reveal>
      ) : null}
    </Widget>
  );
}
