"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import { Dialog } from "@/components/ui/dialog";
import { saveResponseAsFile } from "@/lib/download";
import { EXPORT_FORMATS, type ExportFormat } from "@/lib/library/export-formats";
import { LIMITS } from "@/lib/library/messages";
import { type DialogScope, formatCount, scopeCount, useScopeKeys } from "@/lib/library/scope";
import { btnPrimary, btnText, RADIO_INPUT } from "@/lib/portal-ui";
import { readChoice, writeChoice } from "@/lib/remembered-choice";
import { cn } from "@/lib/utils";

export type { DialogScope } from "@/lib/library/scope";

const FORMAT_IDS = EXPORT_FORMATS.map((format) => format.id);
const FORMAT_KEY = "patra.export-format";

interface ExportDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  scope: DialogScope;
}

/// 超上限说明（简报 §3.6）：整个文献库建议分收藏夹导出，未分类只能在列表里选中导出
function overLimitText(scope: DialogScope): string {
  const count = formatCount(scopeCount(scope));
  if (scope.kind === "library" && scope.view === "unfiled") {
    return `一次最多导出 5,000 篇。未分类有 ${count} 篇，可以在列表里选中部分再导出。`;
  }
  if (scope.kind === "library") {
    return `一次最多导出 5,000 篇。整个文献库有 ${count} 篇，可以分别导出各个收藏夹和「未分类」。`;
  }
  return `一次最多导出 5,000 篇。这里有 ${count} 篇，可以在列表里选中部分再导出。`;
}

/// 导出对话框（简报 §3.6）：范围（只读）→ 四种格式 → 下载。下载交给 catalog（经门户转发），文件名由 catalog 生成。
/// 记住上次的格式（浏览器本地）。超 5,000 篇时主按钮禁用；下载失败不关、就地提示。
export function ExportDialog({ open, onOpenChange, scope }: ExportDialogProps) {
  const [format, setFormat] = useState<ExportFormat>("ris");
  const [downloading, setDownloading] = useState(false);
  const [failed, setFailed] = useState(false);
  const { keys } = useScopeKeys(scope, LIMITS.export, open);

  useEffect(() => {
    if (open) {
      setFormat(readChoice(FORMAT_KEY, FORMAT_IDS, "ris"));
      setFailed(false);
    }
  }, [open]);

  const current = EXPORT_FORMATS.find((item) => item.id === format) ?? EXPORT_FORMATS[0];

  async function download() {
    if (keys.kind !== "ready" || keys.keys.length === 0) {
      return;
    }
    setDownloading(true);
    setFailed(false);
    try {
      const res = await fetch("/api/publications/export", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ids: keys.keys, format, name: scope.name }),
      });
      if (!res.ok) {
        throw new Error(`导出失败：${res.status}`);
      }
      const filename = await saveResponseAsFile(res);
      onOpenChange(false);
      toast.success(`已开始下载 ${filename}`);
    } catch {
      setFailed(true);
    } finally {
      setDownloading(false);
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title="导出"
      size="md"
      alert={
        keys.kind === "too-large" ? (
          <FormAlert tone="warning">{overLimitText(scope)}</FormAlert>
        ) : failed || keys.kind === "error" ? (
          <FormAlert>导出没有成功，请稍后再试</FormAlert>
        ) : null
      }
    >
      <p className="text-sm text-fg-2">{scope.label}</p>
      <fieldset className="grid min-w-0 grid-cols-2 gap-3 max-[880px]:grid-cols-1">
        <legend className="sr-only">格式</legend>
        {EXPORT_FORMATS.map((item) => (
          <label
            key={item.id}
            className={cn(
              "flex cursor-pointer items-start gap-3 rounded-lg border px-4 py-3 transition-colors",
              item.id === format
                ? "border-action-primary bg-paper-100"
                : "border-border-default hover:border-border-hover",
            )}
          >
            <input
              type="radio"
              name="export-format"
              className={cn(RADIO_INPUT, "mt-1")}
              checked={item.id === format}
              onChange={() => {
                setFormat(item.id);
                writeChoice(FORMAT_KEY, item.id);
              }}
            />
            <span className="flex flex-col gap-0.5">
              <span className="text-md font-medium text-fg-1">{item.name}</span>
              <span className="text-xs text-fg-3">{item.hint}</span>
            </span>
          </label>
        ))}
      </fieldset>
      {keys.kind === "ready" && keys.skipped > 0 && (
        <p className="text-sm text-fg-3">{keys.skipped} 篇已失效，不会导出。</p>
      )}
      <div className="flex items-center justify-end gap-4">
        <button
          type="button"
          className={cn(btnText, "text-sm")}
          onClick={() => onOpenChange(false)}
        >
          取消
        </button>
        <button
          type="button"
          disabled={keys.kind !== "ready" || keys.keys.length === 0}
          aria-disabled={downloading || undefined}
          onClick={() => !downloading && void download()}
          className={btnPrimary}
        >
          {downloading ? "准备中…" : `下载 .${current.ext}`}
        </button>
      </div>
    </Dialog>
  );
}
