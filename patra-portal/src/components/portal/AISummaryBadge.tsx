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
        "border-l-2 border-l-clay-500 bg-amber-50/60 px-3 py-2.5 text-sm leading-snug text-ink-800",
        className,
      )}
    >
      <div className="mb-1.5 flex items-center gap-2">
        <span className="font-mono text-2xs uppercase tracking-caps text-clay-700">AI 速读</span>
        <span className="rounded-sm border border-clay-200 bg-paper-50 px-1.5 py-px font-mono text-3xs text-clay-700">
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
