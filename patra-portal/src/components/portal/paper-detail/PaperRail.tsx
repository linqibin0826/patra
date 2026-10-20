import { Sparkles } from "lucide-react";
import { IdentifierCard } from "@/components/portal/paper-detail/IdentifierCard";
import type { PaperDetail } from "@/types/portal";

const CARD_LABEL =
  "mb-4 flex items-center gap-2 font-mono text-2xs uppercase tracking-mono text-fg-3";

/// 文献详情右侧栏（spec §5.15）：标识卡 → AI 速读（尚未上线：后端 aiSummary 恒为 null）。
/// 操作在页头，速览进了事实条，这里不再重复。
export function PaperRail({ paper }: { paper: PaperDetail }) {
  return (
    <>
      <IdentifierCard paper={paper} />

      <div className="rounded-xl border border-clay-200 bg-clay-50 p-5">
        <div className={CARD_LABEL}>
          <span aria-hidden className="h-3 w-1 rounded-[1px] bg-clay-600" />
          AI 速读
          <span className="ml-auto rounded-full border border-clay-200 bg-paper-50 px-2 py-px text-3xs normal-case text-clay-700">
            即将上线
          </span>
        </div>
        {paper.aiSummary ? (
          <p className="m-0 font-serif text-md leading-relaxed text-ink-800">{paper.aiSummary}</p>
        ) : (
          <p className="m-0 font-sans text-sm leading-relaxed text-fg-2">
            AI 速读即将上线，上线后也只作线索，不能替代阅读原文。
          </p>
        )}
        <button
          type="button"
          disabled
          title="功能即将上线"
          className="mt-4 inline-flex cursor-not-allowed items-center gap-1.5 rounded-full border border-clay-200 bg-paper-50 px-3 py-1.5 font-sans text-sm text-clay-700 opacity-70"
        >
          <Sparkles size={13} aria-hidden /> {paper.aiSummary ? "重新生成" : "生成速读"}
        </button>
      </div>
    </>
  );
}
