import { Download } from "lucide-react";
import { formatFileSize } from "@/lib/portal-api/fulltext-derive";
import { btnBlock, btnPrimary, btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/** 门户同源的 PDF 下载地址（Route Handler 转发，决策 F）。 */
export function pdfHref(paperId: string): string {
  return `/api/papers/${paperId}/pdf`;
}

/**
 * 「下载 PDF」按钮，后跟等宽小字文件大小。题录头、侧栏操作卡、目录卡、说明面板共用；
 * 只在详情说能下 PDF（`hasPdf`）时出现，不显示禁用态。
 */
export function PdfDownloadLink({
  paperId,
  sizeBytes,
  primary = false,
  block = false,
}: {
  paperId: string;
  sizeBytes: string | null;
  primary?: boolean;
  block?: boolean;
}) {
  const size = formatFileSize(sizeBytes);
  return (
    <a
      href={pdfHref(paperId)}
      download
      className={cn(primary ? btnPrimary : btnSecondary, block && btnBlock)}
    >
      <Download size={15} aria-hidden />
      下载 PDF
      {size && (
        <span
          className={cn(
            "font-mono text-2xs font-normal tracking-mono tabular-nums",
            primary ? "text-fg-on-clay/80" : "text-fg-3",
          )}
        >
          {size}
        </span>
      )}
    </a>
  );
}
