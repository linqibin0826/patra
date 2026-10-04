import type { CSSProperties } from "react";
import { cn } from "@/lib/utils";
import { generateVenation, type VeinKind } from "@/lib/venation";

/// 各级叶脉的线宽（屏幕像素，non-scaling）、不透明度与描线时长（ms）。
const STROKE: Record<VeinKind, { width: number; opacity: number; dur: number }> = {
  margin: { width: 1.1, opacity: 0.9, dur: 2200 },
  midrib: { width: 2, opacity: 0.95, dur: 1500 },
  secondary: { width: 1, opacity: 0.8, dur: 1100 },
  loop: { width: 0.75, opacity: 0.6, dur: 800 },
  tertiary: { width: 0.5, opacity: 0.45, dur: 650 },
};

/// 描边渐变两端色：纸面上陶土 → 墨，墨底上浅陶土 → 纸。
const TONE = {
  paper: "[--leaf-base:var(--clay-700)] [--leaf-tip:var(--ink-900)]",
  inverse: "[--leaf-base:var(--clay-300)] [--leaf-tip:var(--paper-50)]",
} as const;

/** 首条叶脉开始描线前的停顿，让标题文字先出场 */
const DRAW_LEAD_MS = 250;
/** 绘制次序 order（0–1）映射到的总错峰跨度 */
const DRAW_SPAN_MS = 1500;

interface VenationProps {
  /** 渐变 id 前缀：同页多片叶脉时必须不同 */
  id: string;
  className?: string;
  /** 按绘制次序错峰描线入场（仅 motion-safe 生效） */
  animated?: boolean;
  tone?: keyof typeof TONE;
  seed?: number;
}

/// 叶脉线稿（品牌母题，纯装饰）：Patra 梵语意为「叶」，每条支脉汇入同一根主脉。
export function Venation({ id, className, animated = false, tone = "paper", seed }: VenationProps) {
  const { width, height, paths } = generateVenation({ seed });
  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      fill="none"
      aria-hidden
      className={cn(TONE[tone], className)}
    >
      <defs>
        <linearGradient
          id={`${id}-grad`}
          gradientUnits="userSpaceOnUse"
          x1="0"
          y1={height}
          x2="0"
          y2="0"
        >
          <stop offset="0" className="[stop-color:var(--leaf-base)]" />
          <stop offset="1" className="[stop-color:var(--leaf-tip)]" />
        </linearGradient>
      </defs>
      <g stroke={`url(#${id}-grad)`} strokeLinecap="round" strokeLinejoin="round">
        {paths.map((p, i) => (
          <path
            // 路径由确定性生成器产出、静态不重排，index 作 key 安全
            // biome-ignore lint/suspicious/noArrayIndexKey: 同上
            key={i}
            d={p.d}
            pathLength={1}
            strokeWidth={STROKE[p.kind].width}
            opacity={STROKE[p.kind].opacity}
            vectorEffect="non-scaling-stroke"
            className={animated ? "anim-draw" : undefined}
            // 每条路径的错峰与时长是数据驱动的，只能经 CSS 变量传入
            style={
              animated
                ? ({
                    "--delay": `${Math.round(DRAW_LEAD_MS + p.order * DRAW_SPAN_MS)}ms`,
                    "--dur": `${STROKE[p.kind].dur}ms`,
                  } as CSSProperties)
                : undefined
            }
          />
        ))}
      </g>
    </svg>
  );
}
