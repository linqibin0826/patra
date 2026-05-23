import { cn } from "@/lib/utils";

interface AISummaryBadgeProps {
  summary: string;
  className?: string;
}

export function AISummaryBadge({ summary, className }: AISummaryBadgeProps) {
  return (
    <div
      className={cn(
        "border-l-2 border-l-clay-500 bg-amber-50/60 px-3 py-2 text-sm leading-snug text-ink-800",
        className,
      )}
    >
      <span className="mr-2 font-mono text-2xs uppercase tracking-caps text-clay-700">AI 速读</span>
      <span className="text-fg-2">{summary}</span>
    </div>
  );
}
