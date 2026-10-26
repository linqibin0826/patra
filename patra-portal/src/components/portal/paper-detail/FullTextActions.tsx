import { ArrowDown, ExternalLink } from "lucide-react";
import { PdfDownloadLink } from "@/components/portal/fulltext/PdfDownloadLink";
import { deriveFullTextState } from "@/lib/portal-api/fulltext-derive";
import { deriveFullText } from "@/lib/portal-api/publication-derive";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";
import type { PaperDetail } from "@/types/portal";

/**
 * 页头操作行的主按钮（简报 §3.1、v0.13 §3.3）：有全文 →「阅读全文 ↓」（页内锚点）；只有 PDF →「下载 PDF」主按钮；
 * 其余 → 去出版方的外链（没有任何链接时禁用）。≤ 540px 独占一行。
 */
export function FullTextPrimaryAction({ paper }: { paper: PaperDetail }) {
  const state = deriveFullTextState(paper);
  if (state === "full") {
    return (
      <a className={btnPrimary} href="#fulltext">
        阅读全文 <ArrowDown size={14} data-icon="trailing" aria-hidden />
      </a>
    );
  }
  if (state === "pdf-only") {
    return <PdfDownloadLink paperId={paper.id} sizeBytes={paper.pdfSizeBytes} primary />;
  }
  const fullText = deriveFullText(paper);
  return fullText.href ? (
    <a className={btnPrimary} href={fullText.href} target="_blank" rel="noopener noreferrer">
      {fullText.label} <ExternalLink size={14} data-icon="trailing" />
    </a>
  ) : (
    <button className={cn(btnSecondary, "opacity-55")} type="button" disabled>
      {fullText.label}
    </button>
  );
}

/** 有全文且有 PDF 时次按钮组里的「下载 PDF」；其余情况没有这一项（只有 PDF 时它是主按钮）。 */
export function FullTextPdfAction({
  paper,
  className,
}: {
  paper: PaperDetail;
  className?: string;
}) {
  if (deriveFullTextState(paper) !== "full" || !paper.hasPdf) {
    return null;
  }
  return (
    <PdfDownloadLink paperId={paper.id} sizeBytes={paper.pdfSizeBytes} className={className} />
  );
}
