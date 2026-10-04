import Image from "next/image";
import { cn } from "@/lib/utils";

/// patra-mark.svg 的固有宽高比（viewBox 152.21 × 593.32）
const MARK_RATIO = 152.21 / 593.32;

/// 品牌叶标（装饰，aria-hidden）。height 由 width 按固有比例推出，使 width / height 属性与
/// 渲染尺寸一致——否则 Tailwind preflight 的 `height: auto` 会改写其中一边，触发 next/image 的比例告警。
/// 选用的 width 应让 width / MARK_RATIO 接近整数（如 6 → 23、9 → 35、25 → 97）。
export function BrandMark({ width, className }: { width: number; className?: string }) {
  return (
    <Image
      src="/brand/patra-mark.svg"
      alt=""
      aria-hidden
      width={width}
      height={Math.round(width / MARK_RATIO)}
      className={cn("shrink-0", className)}
    />
  );
}
