import { renderFullTextBody } from "@/components/portal/fulltext/FullTextBody";
import { FullTextUnavailable } from "@/components/portal/fulltext/FullTextUnavailable";
import { LicenseColophon } from "@/components/portal/fulltext/LicenseColophon";
import { ReferenceList } from "@/components/portal/fulltext/ReferenceList";
import { Pill } from "@/components/portal/Pill";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { fetchPublicationFullText } from "@/lib/portal-api/fulltext";
import {
  deriveFullTextState,
  deriveFullTextStats,
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

function statsLine(fullText: FullText, body: FullTextBody): string {
  const stats = deriveFullTextStats(body);
  return [
    fullText.wordCount != null && `${fullText.wordCount.toLocaleString("en-US")} 词`,
    fullText.estimatedReadMin != null && `≈ ${fullText.estimatedReadMin} 分钟`,
    stats.figures > 0 && `${stats.figures} 图`,
    stats.tables > 0 && `${stats.tables} 表`,
    stats.references > 0 && `${stats.references} 篇参考文献`,
  ]
    .filter(Boolean)
    .join(" · ");
}

/** 正文开头的出处条：许可胶囊（作者手稿再加一枚）+ 数据来源 · PMCID · 抓取日期 + 跳到文末版权页。 */
function AttributionBar({ fullText }: { fullText: FullText }) {
  const fetched = formatFetchedDate(fullText.fetchedAt);
  return (
    <div className="flex flex-wrap items-center gap-x-3.5 gap-y-2 border-y border-border-default py-3 font-sans text-sm text-fg-2">
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
      <a
        href="#license"
        className="link-draw ml-auto font-medium text-link hover:text-link-hover max-[540px]:ml-0"
      >
        许可与出处 ↓
      </a>
    </div>
  );
}

/**
 * 全文区块 `#fulltext`（简报 §3.4 – §3.6），服务端组件：详情先出，本区块在 Suspense 里后到。
 * 有全文：区块头（统计行 + 出处条）→ 正文 → 参考文献 → 许可与出处；
 * 没有可读正文：说明面板（三种原因 / 只有 PDF / 加载失败）。
 */
export async function FullTextSection({ paper }: { paper: PaperDetail }) {
  const state = deriveFullTextState(paper);
  const needsFetch = state === "full" || state === "pdf-only";
  const fullText = needsFetch ? await loadFullText(paper.id) : null;
  const body = state === "full" ? fullText?.body : null;

  return (
    <article id="fulltext" aria-label="全文" className="scroll-mt-[45px]">
      <SectionEyebrow className="mb-5">全文</SectionEyebrow>
      {fullText && body ? (
        <ReadableFullText paper={paper} fullText={fullText} body={body} />
      ) : (
        <FullTextUnavailable
          kind={state === "full" ? "error" : state}
          paper={paper}
          fullText={fullText}
        />
      )}
    </article>
  );
}

function ReadableFullText({
  paper,
  fullText,
  body,
}: {
  paper: PaperDetail;
  fullText: FullText;
  body: FullTextBody;
}) {
  const rendered = renderFullTextBody(body, paper.id);
  return (
    <>
      <p className="m-0 -mt-2 mb-4 font-mono text-2xs tracking-mono text-fg-3 tabular-nums">
        {statsLine(fullText, body)}
      </p>
      <AttributionBar fullText={fullText} />
      <div className="pt-2">{rendered.content}</div>
      {rendered.declarations}
      {rendered.appendices}
      {body.references.length > 0 && (
        <ReferenceList
          firstCiteByRef={rendered.firstCiteByRef}
          items={body.references.map((ref, i) => ({
            anchorId: referenceAnchor(ref.id ?? `n${i + 1}`),
            refId: ref.id,
            label: ref.label ?? `${i + 1}`,
            content: rendered.references[i],
            doi: ref.doi,
            pmid: ref.pmid,
            citeCount: ref.id ? (rendered.citeCountByRef[ref.id] ?? 0) : 0,
          }))}
        />
      )}
      <LicenseColophon fullText={fullText} paper={paper} />
    </>
  );
}
