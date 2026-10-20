import { ArrowDown, ExternalLink } from "lucide-react";
import { PdfDownloadLink } from "@/components/portal/fulltext/PdfDownloadLink";
import { deriveFullTextState } from "@/lib/portal-api/fulltext-derive";
import { deriveFullText } from "@/lib/portal-api/publication-derive";
import { btnBlock, btnPrimary, btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";
import type { PaperDetail } from "@/types/portal";

/**
 * 题录头与侧栏操作卡的全文按钮（简报 §3.1）：
 * 有全文 →「阅读全文 ↓」（页内锚点）+ 有 PDF 时「下载 PDF」；只有 PDF →「下载 PDF」主按钮；
 * 其余 → 去出版方的外链（没有任何链接时禁用）。收藏按钮由调用方放在后面。
 */
export function FullTextActions({ paper, block = false }: { paper: PaperDetail; block?: boolean }) {
  const state = deriveFullTextState(paper);
  if (state === "full") {
    return (
      <>
        <a className={cn(btnPrimary, block && btnBlock)} href="#fulltext">
          阅读全文 <ArrowDown size={14} data-icon="trailing" aria-hidden />
        </a>
        {paper.hasPdf && (
          <PdfDownloadLink paperId={paper.id} sizeBytes={paper.pdfSizeBytes} block={block} />
        )}
      </>
    );
  }
  if (state === "pdf-only") {
    return (
      <PdfDownloadLink paperId={paper.id} sizeBytes={paper.pdfSizeBytes} primary block={block} />
    );
  }
  const fullText = deriveFullText(paper);
  return fullText.href ? (
    <a
      className={cn(btnPrimary, block && btnBlock)}
      href={fullText.href}
      target="_blank"
      rel="noopener noreferrer"
    >
      {fullText.label} <ExternalLink size={14} data-icon="trailing" />
    </a>
  ) : (
    <button className={cn(btnSecondary, block && btnBlock, "opacity-55")} type="button" disabled>
      {fullText.label}
    </button>
  );
}
