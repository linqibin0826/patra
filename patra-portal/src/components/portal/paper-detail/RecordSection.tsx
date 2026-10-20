import { Fragment } from "react";
import { DisclosureSection } from "@/components/portal/DisclosureSection";
import { IdentifierChip } from "@/components/portal/IdentifierChip";
import { AuthorList } from "@/components/portal/paper-detail/AuthorList";
import { RailRow } from "@/components/portal/RailRow";
import type { PaperDetail } from "@/types/portal";

const DL = "grid grid-cols-[max-content_1fr] gap-x-8 gap-y-3 max-[540px]:grid-cols-1";
const DT = "whitespace-nowrap font-mono text-2xs tracking-mono text-fg-3 pt-0.5";
const DD = "m-0 font-sans text-md text-fg-1";
const DD_MONO = "m-0 font-mono text-sm text-fg-1 tabular-nums";

const DATE_LABELS: Record<string, string> = {
  received: "投稿",
  accepted: "接收",
  epublished: "电子出版",
  published: "正式出版",
};

/// 文献档案（spec §5.13）：原来正文前的「深度数据」移到文末。完整作者与机构默认展开，
/// 其他标识符、各类日期、资助信息、参考文献与声明默认收起；没有数据的项不出现。
export function RecordSection({ paper }: { paper: PaperDetail }) {
  const ids = [
    paper.doi && { label: "DOI", value: paper.doi, href: `https://doi.org/${paper.doi}` },
    paper.pmid && {
      label: "PMID",
      value: paper.pmid,
      href: `https://pubmed.ncbi.nlm.nih.gov/${paper.pmid}/`,
    },
    paper.pmcid && {
      label: "PMCID",
      value: paper.pmcid,
      href: `https://pmc.ncbi.nlm.nih.gov/articles/${paper.pmcid}/`,
    },
    paper.pii && { label: "PII", value: paper.pii, href: null },
  ].filter((x): x is { label: string; value: string; href: string | null } => Boolean(x));

  return (
    <section id="record" aria-labelledby="record-title" className="mt-14 scroll-mt-[45px]">
      <RailRow
        className="mb-2"
        label={<span className="font-mono text-2xs tracking-caps text-fg-3">RECORD</span>}
      >
        <h2
          id="record-title"
          className="m-0 font-serif text-2xl font-medium tracking-tight text-fg-1"
        >
          文献档案
        </h2>
      </RailRow>

      {paper.authors.length > 0 && (
        <DisclosureSection title="完整作者与机构" count={`${paper.authors.length} 位`} defaultOpen>
          <AuthorList authors={paper.authors} />
        </DisclosureSection>
      )}

      {ids.length > 0 && (
        <DisclosureSection title="其他标识符" count="ID">
          <div className="flex flex-wrap gap-3">
            {ids.map((id) => (
              <IdentifierChip key={id.label} label={id.label} value={id.value} href={id.href} />
            ))}
          </div>
        </DisclosureSection>
      )}

      {paper.dates.length > 0 && (
        <DisclosureSection title="各类日期" count="DATES">
          <dl className={DL}>
            {paper.dates.map((d) => (
              <Fragment key={`${d.type}-${d.date}`}>
                <dt className={DT}>{DATE_LABELS[d.type.toLowerCase()] ?? d.type}</dt>
                <dd className={DD_MONO}>{d.date}</dd>
              </Fragment>
            ))}
          </dl>
        </DisclosureSection>
      )}

      {paper.funding.length > 0 && (
        <DisclosureSection title="资助信息" count={`${paper.funding.length} 项`}>
          <dl className={DL}>
            {paper.funding.map((f, i) => (
              // biome-ignore lint/suspicious/noArrayIndexKey: 静态资助列表（无状态、不重排），funder+grantId 可能完全重复需 index 保唯一
              <Fragment key={`${f.funder ?? ""}-${f.grantId ?? ""}-${i}`}>
                <dt className={DT}>资助方</dt>
                <dd className={DD}>
                  {f.funder ?? "—"}
                  {f.grantId && (
                    <span className="ml-2 font-mono text-sm text-fg-3">· {f.grantId}</span>
                  )}
                </dd>
              </Fragment>
            ))}
          </dl>
        </DisclosureSection>
      )}

      {(paper.numberOfReferences != null || paper.conflictOfInterest) && (
        <DisclosureSection title="参考文献与声明" count="NOTES">
          <dl className={DL}>
            <dt className={DT}>参考文献数</dt>
            <dd className={DD_MONO}>
              {paper.numberOfReferences != null ? paper.numberOfReferences.toLocaleString() : "—"}
            </dd>
            <dt className={DT}>利益冲突</dt>
            <dd className={DD}>{paper.conflictOfInterest || "未声明 / 暂无数据"}</dd>
          </dl>
        </DisclosureSection>
      )}
    </section>
  );
}
