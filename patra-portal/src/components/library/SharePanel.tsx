"use client";

import { useQuery } from "@tanstack/react-query";
import { Share2 } from "lucide-react";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import {
  CITATION_STYLE_IDS,
  CITATION_STYLE_KEY,
  CitationFormatTabs,
  type CitationStyle,
} from "@/components/library/CitationFormatTabs";
import { CitationPreview } from "@/components/library/CitationPreview";
import { CopyButton } from "@/components/library/CopyButton";
import { CopyLinkField } from "@/components/library/CopyLinkField";
import { ResponsivePopover } from "@/components/ui/popover";
import { saveResponseAsFile } from "@/lib/download";
import { EXPORT_FORMATS, type ExportFormat } from "@/lib/library/export-formats";
import { btnSecondary, btnText } from "@/lib/portal-ui";
import { readChoice, writeChoice } from "@/lib/remembered-choice";
import { cn } from "@/lib/utils";

interface ShareProps {
  /// 文献 ID（catalog 内部 ID）
  paperId: string;
  /// 单篇导出的文件名用 PMID，没有时用文献 ID
  pmid: string | null;
  /// 规范地址（服务端按门户公开地址拼好）
  link: string;
}

const HEADING = "font-mono text-2xs tracking-caps text-fg-3 uppercase";

/// 详情页页头的「分享」：胶囊次按钮，点开分享面板（桌面锚在按钮下的浮层，窄屏底部抽屉）
export function ShareButton({ className, ...props }: ShareProps & { className?: string }) {
  const [open, setOpen] = useState(false);
  const anchor = useRef<HTMLButtonElement>(null);
  return (
    <>
      <button
        ref={anchor}
        type="button"
        data-open={open || undefined}
        onClick={() => setOpen(true)}
        className={cn(btnSecondary, "data-open:bg-paper-200", className)}
      >
        <Share2 size={15} strokeWidth={1.6} aria-hidden />
        分享
      </button>
      {open && (
        <ResponsivePopover
          open={open}
          onOpenChange={setOpen}
          anchor={anchor}
          title="分享"
          className="w-100"
        >
          <SharePanel {...props} />
        </ResponsivePopover>
      )}
    </>
  );
}

/// 分享面板（简报 §3.4）：链接、引用（选格式，与复制引用对话框共用上次的格式）、导出这篇。
/// 与 interaction 无关：链接是页面自己的，引用与导出来自 catalog。复制在 HTTP 下走 `useCopy` 的降级
export function SharePanel({ paperId, pmid, link }: ShareProps) {
  // 面板只在点了「分享」后在浏览器端挂载、不参与服务端渲染，初值直接读记住的格式，不先按默认格式白请求一次
  const [style, setStyle] = useState<CitationStyle>(() =>
    readChoice(CITATION_STYLE_KEY, CITATION_STYLE_IDS, "gb-t-7714-2015"),
  );
  const [downloading, setDownloading] = useState<ExportFormat | null>(null);
  const [exportFailed, setExportFailed] = useState(false);
  const preview = useRef<HTMLDivElement>(null);

  const citations = useQuery({
    queryKey: ["citations", style, [paperId]],
    queryFn: async () => {
      const res = await fetch("/api/publications/citations", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ids: [paperId], style }),
      });
      if (!res.ok) {
        throw new Error(`引用生成失败：${res.status}`);
      }
      return ((await res.json()) as { entries: { text: string }[] }).entries;
    },
    retry: false,
  });
  // 单篇只复制正文，不带序号
  const lines = (citations.data ?? []).map((entry) => entry.text);

  async function download(format: ExportFormat) {
    setDownloading(format);
    setExportFailed(false);
    try {
      const res = await fetch("/api/publications/export", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ids: [paperId], format, name: pmid ?? paperId }),
      });
      if (!res.ok) {
        throw new Error(`导出失败：${res.status}`);
      }
      toast.success(`已开始下载 ${await saveResponseAsFile(res)}`);
    } catch {
      setExportFailed(true);
    } finally {
      setDownloading(null);
    }
  }

  return (
    <div className="flex flex-col gap-5 px-4 py-4">
      {exportFailed && <FormAlert>导出没有成功，请稍后再试</FormAlert>}
      <section className="flex flex-col gap-2">
        <h3 className={HEADING}>链接</h3>
        <CopyLinkField url={link} />
      </section>
      <section className="flex flex-col gap-2">
        <h3 className={HEADING}>引用</h3>
        <CitationFormatTabs
          value={style}
          onChange={(next) => {
            setStyle(next);
            writeChoice(CITATION_STYLE_KEY, next);
          }}
        />
        <CitationPreview
          ref={preview}
          className="max-h-36"
          lines={lines}
          state={citations.isError ? "error" : citations.data ? "ready" : "loading"}
          onRetry={() => void citations.refetch()}
        />
        <CopyButton
          label="复制引用"
          text={lines.join("\n")}
          target={() => preview.current}
          disabled={!citations.data || lines.length === 0}
          className="self-start"
        />
      </section>
      <section className="flex flex-col gap-2">
        <h3 className={HEADING}>导出</h3>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          {EXPORT_FORMATS.map((format) => (
            <button
              key={format.id}
              type="button"
              disabled={downloading !== null}
              onClick={() => void download(format.id)}
              className={cn(btnText, "text-sm disabled:opacity-55")}
            >
              {downloading === format.id ? "准备中…" : format.name}
            </button>
          ))}
        </div>
        <p className="text-xs text-fg-3">RIS 可导入 Zotero、EndNote、NoteExpress</p>
      </section>
    </div>
  );
}
