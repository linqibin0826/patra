import { Info } from "lucide-react";

/// 「热读」标签下的一行说明（简报 v0.18 §3.5）：上榜的够一页时讲算法；不够时说清有几篇上榜、几篇是补位；一篇都没
/// 上榜时直说。篇数按实际显示的算
export function FeedNote({ ranked, fillers }: { ranked: number; fillers: number }) {
  const text =
    fillers === 0
      ? "按近 14 天的阅读排：读了全文的分量更重，越近的阅读分量越重；每小时更新一次。"
      : ranked === 0
        ? "近 7 天还没有文献的读者达到 3 位，下面先列出最新收录。"
        : `近 7 天有 3 位以上读者的文献只有 ${ranked} 篇；后面 ${fillers} 篇是最新收录，卡片上标着「最新收录」。`;
  return (
    <p className="-mt-4 mb-6 flex items-start gap-2 text-sm leading-relaxed text-fg-2">
      <Info className="mt-0.5 size-4 shrink-0 text-fg-3" strokeWidth={1.75} aria-hidden="true" />
      {text}
    </p>
  );
}
