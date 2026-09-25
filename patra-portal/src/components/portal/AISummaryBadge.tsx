import { cn } from "@/lib/utils";

interface AISummaryBadgeProps {
  aiSummary: string;
  estimatedReadMin?: number | null;
  className?: string;
}

export function AISummaryBadge({ aiSummary, estimatedReadMin, className }: AISummaryBadgeProps) {
  return (
    <div
      className={cn(
        "rounded-md border border-accent-border bg-accent-tint px-3 pt-2.5 pb-3 text-sm leading-snug",
        className,
      )}
    >
      <div className="mb-1.5 flex items-center gap-2">
        <span className="inline-flex items-center gap-1.5 font-mono text-2xs font-semibold tracking-mono text-accent-strong before:size-1.5 before:rounded-full before:bg-clay-500 before:content-['']">
          AI 速读
        </span>
        <span className="rounded-sm border border-accent-border bg-paper-50 px-1.5 py-px font-mono text-3xs text-accent-strong">
          自动生成
        </span>
        {estimatedReadMin != null && (
          <span className="ml-auto font-mono text-2xs text-fg-3">
            ≈ {estimatedReadMin} 分钟原文
          </span>
        )}
      </div>
      <p className="text-fg-2">{aiSummary}</p>
    </div>
  );
}
