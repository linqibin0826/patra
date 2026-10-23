import { btnPrimary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

interface StickySaveBarProps {
  saving: boolean;
  onDiscard: () => void;
}

/// 吸底保存条（简报 §3.6）：表单有改动时出现，墨色底；左「有未保存的修改」，右「放弃」+「保存」（提交
/// 所在的表单）。桌面在内容列宽度内、离底 16px；≤ 880px 整宽贴底，避开底部安全区。
export function StickySaveBar({ saving, onDiscard }: StickySaveBarProps) {
  return (
    <section
      aria-label="保存资料"
      className="sticky bottom-4 z-30 flex items-center gap-3 rounded-xl bg-bg-inverse py-3 pr-3 pl-5 text-fg-inverse-1 shadow-xl max-[880px]:bottom-0 max-[880px]:-mx-gutter max-[880px]:rounded-none max-[880px]:px-gutter max-[880px]:pb-[max(1.25rem,env(safe-area-inset-bottom))]"
    >
      <span className="flex-1 text-sm">有未保存的修改</span>
      <button
        type="button"
        aria-disabled={saving || undefined}
        onClick={() => {
          if (!saving) onDiscard();
        }}
        className="text-sm font-medium text-fg-inverse-2 transition-colors hover:text-fg-inverse-1 aria-disabled:cursor-not-allowed aria-disabled:opacity-55"
      >
        放弃
      </button>
      <button
        type="submit"
        aria-disabled={saving || undefined}
        className={cn(btnPrimary, "px-4.5 py-2 text-sm max-[880px]:h-12")}
      >
        {saving ? "保存中…" : "保存"}
      </button>
    </section>
  );
}
