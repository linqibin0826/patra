import { type CrumbPosition, CrumbStatus } from "@/components/portal/paper-detail/CrumbStatus";
import { fetchPublicationFullText } from "@/lib/portal-api/fulltext";
import { deriveToc, deriveTocTail } from "@/lib/portal-api/fulltext-derive";
import { shortTitle } from "@/lib/portal-api/title-derive";
import type { PaperDetail } from "@/types/portal";

/** 面包屑中段的服务端部分：取全文（与全文区块共用一次请求），拼出章节号与文末区块的位置表。 */
export async function FullTextCrumbStatus({ paper }: { paper: PaperDetail }) {
  const fullText = paper.hasFullText
    ? await fetchPublicationFullText(paper.id).catch(() => null)
    : null;
  const body = fullText?.body;
  const positions: CrumbPosition[] = [];
  if (body) {
    for (const entry of deriveToc(body)) {
      positions.push({ id: entry.id, label: entry.number ?? entry.label });
      for (const child of entry.children) {
        positions.push({ id: child.id, label: child.number ?? child.label });
      }
    }
    const tail = deriveTocTail(body);
    if (tail.declarations > 0) positions.push({ id: "declarations", label: "声明与附注" });
    if (tail.references > 0) positions.push({ id: "references", label: "参考文献" });
    positions.push({ id: "record", label: "文献档案" });
  }
  return <CrumbStatus shortTitle={shortTitle(paper.title)} positions={positions} />;
}
