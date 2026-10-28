import { formatDateCn, formatDays } from "@/lib/experience/format";
import {
  FINAL_OUTCOME_LABELS,
  FIRST_DECISION_LABELS,
  isTerminalFirstDecision,
} from "@/lib/experience/labels";
import type { Experience } from "@/lib/interaction/experience-schema";
import { cn } from "@/lib/utils";

interface Node {
  event: string;
  /// 日期；进行中没有
  date: string | null;
  /// 到下一个节点的用时（写在连线上，也写进读屏文字）；不知道时为 null
  toNext: number | null;
  /// 进行中：空心节点
  open: boolean;
  /// 下一个节点是进行中：连线画虚线
  toOpen: boolean;
}

function nodesOf(experience: Experience): Node[] {
  const terminal = isTerminalFirstDecision(experience.firstDecision);
  const inProgress = experience.finalOutcome === "IN_PROGRESS";
  const nodes: Node[] = [
    {
      event: "投稿",
      date: formatDateCn(experience.submittedOn),
      toNext: experience.firstRoundDays,
      open: false,
      toOpen: false,
    },
    {
      event: `首轮：${FIRST_DECISION_LABELS[experience.firstDecision]}`,
      date: formatDateCn(experience.firstDecisionOn),
      toNext:
        !terminal && experience.totalDays !== null
          ? experience.totalDays - experience.firstRoundDays
          : null,
      open: false,
      toOpen: inProgress,
    },
  ];
  if (!terminal) {
    nodes.push({
      event: FINAL_OUTCOME_LABELS[experience.finalOutcome],
      date: inProgress
        ? null
        : experience.finalDecisionOn
          ? formatDateCn(experience.finalDecisionOn)
          : "未填日期",
      toNext: null,
      open: inProgress,
      toOpen: false,
    });
  }
  return nodes;
}

/// 经验页的时间线（简报 §3.16）：投稿 → 首轮 → 最终，线上居中标用时；进行中最后一个节点空心、连线虚线；
/// 首轮即最终只有两个节点。是有序列表，用时也写进文字（不只在线上）。≤ 640px 竖排
export function ExperienceTimeline({ experience }: { experience: Experience }) {
  const nodes = nodesOf(experience);
  return (
    <ol
      aria-label="投稿时间线"
      className="m-0 grid list-none auto-cols-[minmax(0,1fr)] grid-flow-col rounded-lg border border-border-default bg-paper-50 px-6 pt-5.5 pb-4.5 max-[640px]:grid-flow-row max-[640px]:auto-cols-auto max-[640px]:px-4 max-[640px]:py-4.5"
    >
      {nodes.map((node, index) => {
        const last = index === nodes.length - 1;
        return (
          <li
            key={node.event}
            className={cn(
              "relative flex min-w-0 flex-col gap-0.75 pt-6 max-[640px]:pt-0 max-[640px]:pb-5.5 max-[640px]:pl-6.5 max-[640px]:last:pb-0",
              // 节点：10px 实心圆；进行中空心
              "before:absolute before:top-0 before:left-0 before:size-2.75 before:rounded-full before:content-[''] max-[640px]:before:top-1",
              node.open
                ? "before:border-[1.5px] before:border-ink-700 before:bg-paper-50"
                : "before:bg-ink-900",
              // 连线：到下一个节点；下一个是进行中时画虚线
              !last &&
                "after:absolute after:top-1.25 after:right-2 after:left-4.5 after:h-px after:content-[''] max-[640px]:after:top-4.5 max-[640px]:after:right-auto max-[640px]:after:bottom-0.5 max-[640px]:after:left-1.25 max-[640px]:after:h-auto max-[640px]:after:w-px",
              !last &&
                (node.toOpen
                  ? "after:bg-[repeating-linear-gradient(90deg,var(--ink-300)_0_5px,transparent_5px_9px)] max-[640px]:after:bg-[repeating-linear-gradient(180deg,var(--ink-300)_0_5px,transparent_5px_9px)]"
                  : "after:bg-ink-300"),
            )}
          >
            <span className="font-semibold text-fg-1 text-sm">{node.event}</span>
            {node.date && <span className="font-mono text-2xs text-fg-3">{node.date}</span>}
            {node.toNext !== null && (
              <span className="absolute -top-2.25 left-1/2 -translate-x-[30%] whitespace-nowrap bg-paper-50 px-2 font-mono text-fg-2 text-xs max-[640px]:static max-[640px]:mt-0.5 max-[640px]:translate-x-0 max-[640px]:bg-transparent max-[640px]:p-0">
                <span className="sr-only">，到下一步用时 </span>
                {formatDays(node.toNext)}
              </span>
            )}
          </li>
        );
      })}
    </ol>
  );
}
