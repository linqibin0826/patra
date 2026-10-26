"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Bookmark } from "lucide-react";
import { useRef, useState } from "react";
import { useSaveStatus } from "@/components/library/LibraryStatusProvider";
import { type PopoverPhase, SavePopover } from "@/components/library/SavePopover";
import { ResponsivePopover } from "@/components/ui/popover";
import { collectPublication } from "@/lib/interaction/library-actions";
import { invalidateLibrary, patchItemStatus } from "@/lib/library/cache";
import { useLoginRedirect } from "@/lib/library/use-login-redirect";
import { orUnavailable } from "@/lib/or-unavailable";
import { inlinePlainText } from "@/lib/portal-api/title-derive";
import { btnSecondary } from "@/lib/portal-ui";
import { parseInlineMarkup } from "@/lib/rich-inline-text";
import { cn } from "@/lib/utils";

interface SaveButtonProps {
  /// 文献 ID（门户 URL 里的 catalog 内部 ID）
  paperKey: string;
  /// 文献标题（可含内联标记），读屏名用前 40 字
  title: string;
  /// pill：详情页页头的胶囊次按钮；icon：首页卡片、列表项的 32px 圆形图标按钮
  variant: "pill" | "icon";
}

/// 收藏按钮（简报 §3.1）：三处入口与公开收藏夹页共用一个组件，只有外形不同。状态来自一屏一批的「我的状态」
/// （`LibraryStatusProvider`）。点「收藏」立即收进文献库（乐观更新）并打开浮层；点「已收藏」只打开浮层，
/// 取消收藏在浮层里。未登录点了去登录，回来后不自动收藏。
export function SaveButton({ paperKey, title, variant }: SaveButtonProps) {
  const client = useQueryClient();
  const save = useSaveStatus(paperKey);
  const redirectToLogin = useLoginRedirect();
  const anchor = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [phase, setPhase] = useState<PopoverPhase>({ kind: "existing" });
  const [announce, setAnnounce] = useState("");
  /// 这次收藏回来的条目里的备注：状态陈旧（别处已收藏并写了备注）时浮层要显示它，不能当作没有备注
  const [collectedNote, setCollectedNote] = useState<string | null | undefined>(undefined);

  const status = save.kind === "ready" ? save.status : null;
  const saved = status?.collected === true;
  const inactive = save.kind === "pending" || save.kind === "unavailable";
  const unavailable = save.kind === "unavailable";

  async function collect() {
    setPhase({ kind: "saving" });
    setCollectedNote(undefined);
    setOpen(true);
    patchItemStatus(client, paperKey, { collected: true });
    setAnnounce("已收藏");
    const result = await orUnavailable(collectPublication(paperKey));
    if (result.ok) {
      patchItemStatus(client, paperKey, {
        collected: true,
        itemId: result.item.itemId,
        collectedAt: result.item.collectedAt,
        collectionIds: result.item.collectionIds,
        hasNote: result.item.note !== null,
      });
      setCollectedNote(result.item.note);
      setPhase({ kind: result.created ? "saved" : "existing" });
      void invalidateLibrary(client);
      return;
    }
    patchItemStatus(client, paperKey, { collected: false });
    setAnnounce("");
    if (result.reason === "signed-out") {
      setOpen(false);
      redirectToLogin();
      return;
    }
    setPhase({ kind: "failed", reason: result.reason });
  }

  function onClick() {
    if (save.kind === "anon") {
      redirectToLogin();
      return;
    }
    if (inactive || status === null) {
      return;
    }
    if (saved) {
      setPhase({ kind: "existing" });
      setOpen(true);
      return;
    }
    void collect();
  }

  const plain = inlinePlainText(parseInlineMarkup(title));
  const label = unavailable
    ? "收藏暂不可用"
    : saved
      ? "已收藏，管理收藏"
      : `收藏「${Array.from(plain).slice(0, 40).join("")}」`;
  const icon = (
    <Bookmark
      size={15}
      strokeWidth={1.6}
      aria-hidden
      data-disabled={unavailable || undefined}
      className={cn(
        "motion-safe:transition-transform duration-300 ease-out-expo data-disabled:text-fg-4",
        saved && "fill-clay-500 text-clay-600 motion-safe:scale-110",
      )}
    />
  );

  return (
    <>
      <button
        ref={anchor}
        type="button"
        aria-label={label}
        aria-pressed={saved}
        aria-disabled={inactive || undefined}
        title={variant === "icon" ? (unavailable ? "收藏暂不可用" : undefined) : undefined}
        data-open={open || undefined}
        onClick={onClick}
        className={
          variant === "icon"
            ? cn(
                "inline-flex size-8 shrink-0 items-center justify-center rounded-full text-fg-3 transition-colors duration-200 data-open:bg-paper-200",
                !inactive && "hover:bg-paper-200 hover:text-ink-900",
                inactive && "cursor-default",
              )
            : cn(btnSecondary, "data-open:bg-paper-200")
        }
      >
        {icon}
        {variant === "pill" && (unavailable ? "收藏暂不可用" : saved ? "已收藏" : "收藏")}
      </button>
      <span className="sr-only" aria-live="polite">
        {announce}
      </span>
      {open && (
        <ResponsivePopover
          open={open}
          onOpenChange={setOpen}
          anchor={anchor}
          title="收藏"
          align={variant === "icon" ? "end" : "start"}
        >
          <SavePopover
            paperKey={paperKey}
            phase={phase}
            status={status}
            knownNote={collectedNote}
            onRetry={() => void collect()}
            onRemoved={() => {
              setOpen(false);
              setAnnounce("已从文献库移除");
            }}
          />
        </ResponsivePopover>
      )}
    </>
  );
}
