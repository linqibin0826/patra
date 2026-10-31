// patra-learn/src/components/progress-ui.tsx
// 读本地进度的小件：掌握度徽章与圆点、按掌握度分段的进度条。服务端与水合时显示「没读过」
"use client";

import { useProgress } from "@/lib/progress/use-progress";
import { masteryCounts } from "@/lib/site/today";
import { MasteryChip, MasteryDot, type ShownLevel } from "./ui";

export function useLevel(slug: string): ShownLevel {
  return useProgress().progress[slug]?.level ?? 0;
}

export function LiveMasteryChip({ slug }: { slug: string }) {
  return <MasteryChip level={useLevel(slug)} />;
}

export function LiveMasteryDot({ slug, className }: { slug: string; className?: string }) {
  return <MasteryDot level={useLevel(slug)} {...(className ? { className } : {})} />;
}

/// 吃透了 / 理解中 / 刚接触三段，宽度按篇数占比；track 是底色
export function ProgressBar({
  slugs,
  height = "h-1.5",
  track = "bg-line-soft",
}: {
  slugs: string[];
  height?: string;
  track?: string;
}) {
  const { progress } = useProgress();
  const counts = masteryCounts(slugs, progress);
  const pct = (n: number) => `${slugs.length ? (n / slugs.length) * 100 : 0}%`;
  return (
    <span
      role="img"
      aria-label={`读过 ${counts.read} 篇，共 ${slugs.length} 篇`}
      className={`flex overflow-hidden rounded-full ${height} ${track}`}
    >
      <span className="bg-teal" style={{ width: pct(counts[3]) }} />
      <span className="bg-teal-soft" style={{ width: pct(counts[2]) }} />
      <span className="bg-teal-pale" style={{ width: pct(counts[1]) }} />
    </span>
  );
}

/// 「读过 n 篇」；一篇没标时写「还没开始」
export function ReadCount({ slugs, empty = "还没开始" }: { slugs: string[]; empty?: string }) {
  const { progress } = useProgress();
  const { read } = masteryCounts(slugs, progress);
  return <>{read > 0 ? `读过 ${read} 篇` : empty}</>;
}
