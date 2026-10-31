// patra-learn/src/teaching/structure.tsx
// 结构图（画板 C-Structure）：默认 / 选中方块 / 选中连线 / 消息送到 / 消息没人接
"use client";

import { useEffect, useState } from "react";
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
import { edgeLine, gridLayout, nodeWidth } from "./layout";
import type { PropsOf } from "./registry";
import { Button, Caption, Inline, Meta, Widget } from "./ui";

/// 每跳之间的间隔（毫秒）：消息块移动 560 ms，再停一会儿让读者读说明
const HOP_INTERVAL = 800;
const PACKET_LANE = 44;

type Selection =
  | { kind: "none" }
  | { kind: "node"; id: string }
  | { kind: "edge"; key: string }
  | { kind: "trace"; index: number; hop: number };

const edgeKey = (from: string, to: string) => `${from}-${to}`;

export function Structure({ title, intro, nodes, edges = [], traces = [] }: PropsOf<"Structure">) {
  const [sel, setSel] = useState<Selection>({ kind: "none" });
  const { boxes, height: gridHeight } = gridLayout(
    nodes.map((n) => ({ ...n, width: nodeWidth(n) })),
    { width: STAGE_WIDTH, nodeHeight: 60, rowGap: 72 },
  );
  for (const b of Object.values(boxes)) b.y += PACKET_LANE;
  const height = PACKET_LANE + gridHeight + 8;

  const trace = sel.kind === "trace" ? traces[sel.index] : undefined;
  const hopCount = trace?.hops.length ?? 0;
  const hopIndex = sel.kind === "trace" ? sel.hop : -1;
  useEffect(() => {
    if (sel.kind !== "trace" || sel.hop >= hopCount - 1) return;
    const t = setTimeout(() => setSel({ ...sel, hop: sel.hop + 1 }), HOP_INTERVAL);
    return () => clearTimeout(t);
  }, [sel, hopCount]);

  const litNodes = new Set<string>();
  const litEdges = new Set<string>();
  if (sel.kind === "node") {
    litNodes.add(sel.id);
    for (const e of edges)
      if (e.from === sel.id || e.to === sel.id) litEdges.add(edgeKey(e.from, e.to));
  } else if (sel.kind === "edge") {
    litEdges.add(sel.key);
    const e = edges.find((x) => edgeKey(x.from, x.to) === sel.key);
    if (e) litNodes.add(e.from).add(e.to);
  } else if (trace) {
    trace.hops.slice(0, hopIndex + 1).forEach((h, i, hops) => {
      litNodes.add(h.at);
      const prev = hops[i - 1];
      if (!prev) return;
      for (const e of edges) {
        if ((e.from === prev.at && e.to === h.at) || (e.from === h.at && e.to === prev.at)) {
          litEdges.add(edgeKey(e.from, e.to));
        }
      }
    });
  }
  const any = litNodes.size > 0 || litEdges.size > 0;
  const emphasis = (lit: boolean): Emphasis => (lit ? "on" : any ? "dim" : "normal");

  const drawn: DrawnEdge[] = edges.flatMap((e) => {
    const a = boxes[e.from];
    const b = boxes[e.to];
    if (!a || !b) return [];
    const reverse = edges.some((x) => x.from === e.to && x.to === e.from);
    return [
      {
        key: edgeKey(e.from, e.to),
        line: edgeLine(a, b, reverse ? 6 : 0),
        emphasis: emphasis(litEdges.has(edgeKey(e.from, e.to))),
        dashed: e.dashed,
      },
    ];
  });

  const hop = trace?.hops[hopIndex];
  const hopBox = hop ? boxes[hop.at] : undefined;
  const selectedNode = sel.kind === "node" ? nodes.find((n) => n.id === sel.id) : undefined;
  const selectedEdge =
    sel.kind === "edge" ? edges.find((e) => edgeKey(e.from, e.to) === sel.key) : undefined;
  const caption = hop
    ? { lead: undefined, text: hop.caption }
    : selectedNode
      ? { lead: `${selectedNode.label}　`, text: selectedNode.desc }
      : selectedEdge
        ? { lead: `${selectedEdge.label ?? "连线"}　`, text: selectedEdge.desc ?? "" }
        : { lead: undefined, text: intro ?? "点图上的方块或连线，看它管什么。" };

  return (
    <Widget
      label={title}
      title={title}
      kind="结构图"
      foot={
        <>
          <Meta>点图上的方块或连线，看它管什么</Meta>
          {traces.length > 0 ? (
            <span className="flex flex-wrap gap-2">
              {traces.map((t, i) => (
                <Button key={t.button} onClick={() => setSel({ kind: "trace", index: i, hop: 0 })}>
                  {t.button}
                </Button>
              ))}
            </span>
          ) : null}
        </>
      }
    >
      <Stage height={height}>
        <EdgeLayer edges={drawn} height={height} />
        {nodes.map((n) => {
          const box = boxes[n.id];
          if (!box) return null;
          return (
            <NodeBox
              key={n.id}
              testId={`node-${n.id}`}
              box={box}
              label={n.label}
              sub={n.sub}
              code={n.code}
              emphasis={emphasis(litNodes.has(n.id))}
              onClick={() => setSel({ kind: "node", id: n.id })}
            />
          );
        })}
        {edges.map((e) => {
          const line = drawn.find((d) => d.key === edgeKey(e.from, e.to))?.line;
          if (!e.label || !line) return null;
          const key = edgeKey(e.from, e.to);
          return (
            <EdgeLabel
              key={key}
              placement={placeLabel(
                line,
                edges.some((x) => x.from === e.to && x.to === e.from),
              )}
              text={e.label}
              emphasis={emphasis(litEdges.has(key))}
              onClick={e.desc ? () => setSel({ kind: "edge", key }) : undefined}
            />
          );
        })}
        {trace && hopBox && hop ? (
          <Packet
            testId="packet"
            label={trace.packet}
            state={hop.drop ? "dropped" : undefined}
            x={hopBox.x + hopBox.w / 2 - 36}
            y={8}
          />
        ) : null}
      </Stage>
      <Caption lead={caption.lead} motionKey={`${sel.kind}:${caption.text}`}>
        <Inline text={caption.text} />
      </Caption>
    </Widget>
  );
}
