import Link from "next/link";
import { Fragment } from "react";
import { DisclosureSection } from "@/components/portal/DisclosureSection";
import { IdentifierChip } from "@/components/portal/IdentifierChip";
import { AbstractBlock } from "@/components/portal/paper-detail/AbstractBlock";
import { AuthorList } from "@/components/portal/paper-detail/AuthorList";
import { PaperHeader } from "@/components/portal/paper-detail/PaperHeader";
import { PaperRail } from "@/components/portal/paper-detail/PaperRail";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import type { PaperDetail } from "@/types/portal";

const DL = "grid grid-cols-[max-content_1fr] gap-x-5 gap-y-2.5 max-[540px]:grid-cols-1";
const DT = "whitespace-nowrap font-sans text-sm text-fg-3";
const DD = "m-0 font-sans text-md text-fg-1";
const DD_MONO = "m-0 font-mono text-sm text-fg-1";

const DATE_LABELS: Record<string, string> = {
  received: "投稿",
  accepted: "接收",
  epublished: "电子出版",
  published: "正式出版",
};

export function PublicationDetailView({ paper }: { paper: PaperDetail }) {
  const mesh = paper.meshHeadings;
  const funding = paper.funding;
  const hasOtherIds = Boolean(paper.pmcid || paper.pii);
  const crumb = paper.pmid ?? paper.id;

  return (
    <div className="pb-6">
      <nav
        aria-label="面包屑"
        className="sticky top-14 z-30 border-b border-border-default bg-[rgba(247,242,232,0.88)] backdrop-blur"
      >
        <ol className="mx-auto flex h-11 max-w-[1200px] items-center gap-2 px-6 font-sans text-sm text-fg-3">
          <li>
            <Link
              href="/"
              className="rounded-sm px-1 py-0.5 text-fg-2 hover:bg-paper-200 hover:text-clay-700"
            >
              Patra
            </Link>
          </li>
          <li aria-hidden className="text-ink-300">
            /
          </li>
          <li className="max-[720px]:hidden">
            <Link
              href="/"
              className="rounded-sm px-1 py-0.5 text-fg-2 hover:bg-paper-200 hover:text-clay-700"
            >
              文献
            </Link>
          </li>
          <li aria-hidden className="text-ink-300 max-[720px]:hidden">
            /
          </li>
          <li aria-current="page" className="truncate font-mono font-medium text-fg-1">
            {crumb}
          </li>
        </ol>
      </nav>

      <div className="mx-auto grid max-w-[1200px] grid-cols-[minmax(0,1fr)_340px] items-start gap-10 px-6 pt-8 max-[980px]:grid-cols-1 max-[980px]:gap-7 max-[980px]:pt-6">
        <div className="flex min-w-0 flex-col gap-7">
          <PaperHeader paper={paper} />

          <section aria-label="摘要">
            <SectionEyebrow>摘要</SectionEyebrow>
            <AbstractBlock paper={paper} />
          </section>

          <section aria-label="关键标识">
            <SectionEyebrow>关键标识</SectionEyebrow>
            <div className="flex flex-wrap gap-2.5">
              <IdentifierChip
                label="DOI"
                value={paper.doi}
                href={paper.doi ? `https://doi.org/${paper.doi}` : null}
              />
              <IdentifierChip
                label="PMID"
                value={paper.pmid}
                href={paper.pmid ? `https://pubmed.ncbi.nlm.nih.gov/${paper.pmid}` : null}
              />
            </div>
          </section>

          <section aria-label="深度数据">
            <SectionEyebrow>深度数据 · 按需展开</SectionEyebrow>
            <div>
              <DisclosureSection title="完整作者与机构" count={`${paper.authors.length} 位`}>
                <AuthorList authors={paper.authors} />
              </DisclosureSection>

              {mesh.length > 0 && (
                <DisclosureSection title="MeSH 主题词 / 关键词" count={`${mesh.length} 项`}>
                  <div className="flex flex-wrap gap-2">
                    {mesh.map((h) => (
                      <span
                        key={h.descriptorUi}
                        className={
                          h.major
                            ? "rounded-sm border border-clay-200 bg-clay-50 px-2 py-0.5 font-sans text-sm text-clay-800"
                            : "rounded-sm border border-border-default bg-paper-100 px-2 py-0.5 font-sans text-sm text-fg-2"
                        }
                      >
                        {h.major && <span title="主要主题词">★ </span>}
                        {h.term}
                      </span>
                    ))}
                  </div>
                  {paper.keywords.length > 0 && (
                    <div className="mt-3 flex flex-wrap gap-2">
                      {paper.keywords.map((k) => (
                        <span key={k} className="font-mono text-[11px] text-fg-3">
                          #{k}
                        </span>
                      ))}
                    </div>
                  )}
                </DisclosureSection>
              )}

              {funding.length > 0 && (
                <DisclosureSection title="资助信息" count={`${funding.length} 项`}>
                  <dl className={DL}>
                    {funding.map((f, i) => (
                      // biome-ignore lint/suspicious/noArrayIndexKey: 静态资助列表（无状态、不重排），funder+grantId 可能完全重复需 index 保唯一
                      <Fragment key={`${f.funder ?? ""}-${f.grantId ?? ""}-${i}`}>
                        <dt className={DT}>资助方</dt>
                        <dd className={DD}>
                          {f.funder ?? "—"}
                          {f.grantId && (
                            <span className="ml-2 font-mono text-sm text-fg-3">
                              · {f.grantId}
                            </span>
                          )}
                        </dd>
                      </Fragment>
                    ))}
                  </dl>
                </DisclosureSection>
              )}

              {hasOtherIds && (
                <DisclosureSection title="其他标识符">
                  <div className="flex flex-wrap gap-2.5">
                    {paper.pmcid && (
                      <IdentifierChip
                        label="PMCID"
                        value={paper.pmcid}
                        href={`https://www.ncbi.nlm.nih.gov/pmc/articles/${paper.pmcid}/`}
                      />
                    )}
                    {paper.pii && <IdentifierChip label="PII" value={paper.pii} />}
                  </div>
                </DisclosureSection>
              )}

              {paper.dates.length > 0 && (
                <DisclosureSection title="各类日期">
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

              <DisclosureSection title="参考文献与声明">
                <dl className={DL}>
                  <dt className={DT}>参考文献数</dt>
                  <dd className={DD_MONO}>
                    {paper.numberOfReferences != null
                      ? paper.numberOfReferences.toLocaleString()
                      : "—"}
                  </dd>
                  <dt className={DT}>利益冲突</dt>
                  <dd className={DD}>{paper.conflictOfInterest || "未声明 / 暂无数据"}</dd>
                </dl>
              </DisclosureSection>
            </div>
          </section>
        </div>

        {/* sticky top = TopNav 56px + 面包屑 bar 44px + 16px 间距 = 116px */}
        <aside className="sticky top-[116px] flex flex-col gap-4 max-[980px]:static">
          <PaperRail paper={paper} />
        </aside>
      </div>
    </div>
  );
}
