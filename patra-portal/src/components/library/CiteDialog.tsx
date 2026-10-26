"use client";

import { useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import {
  CITATION_STYLE_IDS,
  CITATION_STYLE_KEY,
  CitationFormatTabs,
  type CitationStyle,
} from "@/components/library/CitationFormatTabs";
import { CopyButton } from "@/components/library/CopyButton";
import { Dialog } from "@/components/ui/dialog";
import { LIMITS } from "@/lib/library/messages";
import { type DialogScope, formatCount, scopeCount, useScopeKeys } from "@/lib/library/scope";
import { btnSecondary, btnText } from "@/lib/portal-ui";
import { readChoice, writeChoice } from "@/lib/remembered-choice";
import { cn } from "@/lib/utils";

interface Entry {
  label: string | null;
  text: string;
}

interface CiteDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  scope: DialogScope;
  /// 超 100 篇时「导出…」：关掉本框、以同一范围打开导出框
  onExport: () => void;
}

/// 超 100 篇的说明里「这里有多少篇」的说法
function overLimitText(scope: DialogScope): string {
  const count = formatCount(scopeCount(scope));
  const where =
    scope.kind === "collection" || scope.kind === "public-collection"
      ? "这个收藏夹"
      : scope.kind === "library"
        ? scope.view === "unfiled"
          ? "未分类"
          : "整个文献库"
        : "这里";
  return `一次最多复制 100 条引用。${where}有 ${count} 篇，可以选中部分再复制，或者改用导出。`;
}

/// 复制引用对话框（简报 §3.5）：格式切换（记住上次）→ catalog 生成的预览 → 复制。编号样式按列表顺序带序号，
/// APA 由 catalog 按作者排好、不带序号；单篇只复制正文。超 100 篇不请求，引导去导出。
export function CiteDialog({ open, onOpenChange, scope, onExport }: CiteDialogProps) {
  const [style, setStyle] = useState<CitationStyle>("gb-t-7714-2015");
  const preview = useRef<HTMLDivElement>(null);
  const { keys, retry } = useScopeKeys(scope, LIMITS.cite, open);
  const single = scope.kind === "items" && scope.items.length === 1;

  useEffect(() => {
    if (open) {
      setStyle(readChoice(CITATION_STYLE_KEY, CITATION_STYLE_IDS, "gb-t-7714-2015"));
    }
  }, [open]);

  const ids = keys.kind === "ready" ? keys.keys : [];
  const citations = useQuery({
    queryKey: ["citations", style, ids],
    queryFn: async () => {
      const res = await fetch("/api/publications/citations", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ids, style }),
      });
      if (!res.ok) {
        throw new Error(`引用生成失败：${res.status}`);
      }
      return ((await res.json()) as { entries: Entry[] }).entries;
    },
    enabled: open && ids.length > 0,
    retry: false,
  });

  const lines = (citations.data ?? []).map((entry) =>
    single || !entry.label ? entry.text : `${entry.label} ${entry.text}`,
  );
  const count = scopeCount(scope);
  const title = single ? "复制引用" : `复制引用 · ${formatCount(count)} 篇`;

  return (
    <Dialog open={open} onOpenChange={onOpenChange} title={title} size="md">
      {keys.kind === "too-large" ? (
        <div className="flex flex-col gap-4">
          <p className="text-sm leading-6 text-fg-2">{overLimitText(scope)}</p>
          <button type="button" className={cn(btnSecondary, "self-start")} onClick={onExport}>
            导出…
          </button>
        </div>
      ) : (
        <>
          <CitationFormatTabs
            value={style}
            onChange={(next) => {
              setStyle(next);
              writeChoice(CITATION_STYLE_KEY, next);
            }}
          />
          {keys.kind === "ready" && keys.skipped > 0 && (
            <p className="text-sm text-fg-3">{keys.skipped} 篇已失效，不含在内。</p>
          )}
          <div
            ref={preview}
            className="max-h-90 overflow-y-auto rounded-md bg-paper-100 px-4 py-3 font-serif text-sm leading-6 text-fg-1 select-text"
          >
            {citations.isError || keys.kind === "error" ? (
              <p className="flex items-center gap-3 font-sans text-fg-2">
                引用暂时生成不了
                <button
                  type="button"
                  className={cn(btnText, "text-sm")}
                  onClick={() => (keys.kind === "error" ? retry() : void citations.refetch())}
                >
                  重试
                </button>
              </p>
            ) : citations.data ? (
              lines.map((line) => (
                <p key={line} className="m-0 [overflow-wrap:anywhere]">
                  {line}
                </p>
              ))
            ) : (
              <div aria-hidden="true" className="flex flex-col gap-2">
                <div className="shimmer h-4 w-full rounded-sm" />
                <div className="shimmer h-4 w-2/3 rounded-sm" />
              </div>
            )}
          </div>
          <div className="flex items-center justify-end gap-4">
            <button
              type="button"
              className={cn(btnText, "text-sm")}
              onClick={() => onOpenChange(false)}
            >
              关闭
            </button>
            <CopyButton
              primary
              label={single ? "复制引用" : `复制 ${lines.length} 条引用`}
              text={lines.join("\n")}
              target={() => preview.current}
              disabled={!citations.data || lines.length === 0}
            />
          </div>
        </>
      )}
    </Dialog>
  );
}
