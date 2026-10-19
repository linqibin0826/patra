import { FullTextToc, type FullTextTocProps } from "@/components/portal/fulltext/FullTextToc";
import { FullTextTocSheet } from "@/components/portal/fulltext/FullTextTocSheet";
import { fetchPublicationFullText } from "@/lib/portal-api/fulltext";
import { deriveToc, formatFetchedDate, licenseLabel } from "@/lib/portal-api/fulltext-derive";
import type { PaperDetail } from "@/types/portal";

/** 目录数据：只在有可读正文且有带标题的一级章节时给出；全文取不到时安静地没有目录（区块里会说明）。 */
async function loadToc(paper: PaperDetail): Promise<FullTextTocProps | null> {
  if (!paper.hasFullText) {
    return null;
  }
  const fullText = await fetchPublicationFullText(paper.id).catch(() => null);
  const body = fullText?.body;
  if (!fullText || !body) {
    return null;
  }
  const entries = deriveToc(body);
  if (entries.length === 0) {
    return null;
  }
  return {
    entries,
    hasReferences: body.references.length > 0,
    readMin: fullText.estimatedReadMin,
    paperId: paper.id,
    hasPdf: paper.hasPdf,
    pdfSizeBytes: paper.pdfSizeBytes,
    licenseText: licenseLabel(fullText.license),
    fetchedDate: formatFetchedDate(fullText.fetchedAt),
  };
}

/** 桌面侧栏的目录卡（服务端取数，与全文区块共用同一次请求）。 */
export async function FullTextTocCard({ paper }: { paper: PaperDetail }) {
  const toc = await loadToc(paper);
  return toc && <FullTextToc {...toc} />;
}

/** 移动端面包屑右端的「目录」按钮与底部面板。 */
export async function FullTextTocButton({ paper }: { paper: PaperDetail }) {
  const toc = await loadToc(paper);
  return toc && <FullTextTocSheet {...toc} />;
}
