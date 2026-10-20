import type { ReactNode } from "react";
import { CitationPopover } from "@/components/portal/fulltext/CitationPopover";
import { renderFullTextBody } from "@/components/portal/fulltext/FullTextBody";
import { FullTextUnavailable } from "@/components/portal/fulltext/FullTextUnavailable";
import { LicenseColophon } from "@/components/portal/fulltext/LicenseColophon";
import { ReferenceList } from "@/components/portal/fulltext/ReferenceList";
import { Pill } from "@/components/portal/Pill";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { fetchPublicationFullText } from "@/lib/portal-api/fulltext";
import {
  deriveFullTextState,
  formatFetchedDate,
  licenseLabel,
  referenceAnchor,
} from "@/lib/portal-api/fulltext-derive";
import type { FullText, FullTextBody } from "@/types/fulltext";
import type { PaperDetail } from "@/types/portal";

/** 取全文；出错时返回 null，由调用方按加载失败显示（不打到全局错误页）。 */
async function loadFullText(id: string): Promise<FullText | null> {
  try {
    return await fetchPublicationFullText(id);
  } catch {
    return null;
  }
}

/** 全文区块头右侧的出处行：许可胶囊（作者手稿再加一枚）+ 数据来源 · PMCID · 抓取日期 + 跳到许可与出处。 */
function AttributionLine({ fullText }: { fullText: FullText }) {
  const fetched = formatFetchedDate(fullText.fetchedAt);
  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-2 font-mono text-2xs tracking-mono text-fg-3">
      <Pill tone="clay" size="sm" className="px-2.5 py-0.5 text-xs">
        {licenseLabel(fullText.license)}
      </Pill>
      {fullText.isManuscript && (
        <Pill size="sm" className="px-2.5 py-0.5 text-xs">
          作者手稿
        </Pill>
      )}
      <span className="max-[540px]:basis-full">
        {[fullText.source, fullText.pmcid, fetched && `抓取于 ${fetched}`]
          .filter(Boolean)
          .join(" · ")}
      </span>
      <a href="#license" className="link-draw font-medium text-link hover:text-link-hover">
        许可与出处 ↓
      </a>
    </div>
  );
}

/**
 * 全文区块 `#fulltext`（简报 §3.4 – §3.6，PAP-89 spec §4），服务端组件：详情先出，本区块在 Suspense 里后到。
 * 有全文：区块头（「全文 · Full text」+ 出处行）→ 正文 → 声明与附注 → 附录 → 参考文献 → 文献档案 → 许可与出处；
 * 没有可读正文：说明面板（三种原因 / 只有 PDF / 加载失败）→ 文献档案。
 * 文献档案由页面传入（`record`），因为它只用详情数据，却要排在参考文献与许可之间。
 */
export async function FullTextSection({
  paper,
  record,
}: {
  paper: PaperDetail;
  record: ReactNode;
}) {
  const state = deriveFullTextState(paper);
  const needsFetch = state === "full" || state === "pdf-only";
  const fullText = needsFetch ? await loadFullText(paper.id) : null;
  const body = state === "full" ? fullText?.body : null;

  return (
    <article id="fulltext" aria-label="全文" className="scroll-mt-[45px]">
      <div className="mb-12 flex flex-wrap items-baseline justify-between gap-x-6 gap-y-2 border-b border-ink-900 pb-4">
        <SectionEyebrow className="mb-0">全文 · Full text</SectionEyebrow>
        {fullText && body && <AttributionLine fullText={fullText} />}
      </div>
      {fullText && body ? (
        <ReadableFullText paper={paper} fullText={fullText} body={body} record={record} />
      ) : (
        <>
          <FullTextUnavailable
            kind={state === "full" ? "error" : state}
            paper={paper}
            fullText={fullText}
          />
          {record}
        </>
      )}
    </article>
  );
}

function ReadableFullText({
  paper,
  fullText,
  body,
  record,
}: {
  paper: PaperDetail;
  fullText: FullText;
  body: FullTextBody;
  record: ReactNode;
}) {
  const rendered = renderFullTextBody(body, paper.id);
  const items = body.references.map((ref, i) => ({
    anchorId: referenceAnchor(ref.id ?? `n${i + 1}`),
    refId: ref.id,
    label: ref.label ?? `${i + 1}`,
    content: rendered.references[i],
    doi: ref.doi,
    pmid: ref.pmid,
    citeCount: ref.id ? (rendered.citeCountByRef[ref.id] ?? 0) : 0,
  }));
  return (
    <>
      {rendered.content}
      {rendered.declarations}
      {rendered.appendices}
      {items.length > 0 && <ReferenceList firstCiteByRef={rendered.firstCiteByRef} items={items} />}
      {record}
      <LicenseColophon fullText={fullText} paper={paper} />
      {items.length > 0 && <CitationPopover items={items} />}
    </>
  );
}
