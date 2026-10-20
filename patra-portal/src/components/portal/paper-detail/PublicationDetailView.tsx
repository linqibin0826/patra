import type { ReactNode } from "react";
import { DetailCrumbs } from "@/components/portal/DetailCrumbs";
import { AbstractBlock } from "@/components/portal/paper-detail/AbstractBlock";
import { DetailSectionTitle } from "@/components/portal/paper-detail/DetailSectionTitle";
import { KeywordRow } from "@/components/portal/paper-detail/KeywordRow";
import { PaperHeader } from "@/components/portal/paper-detail/PaperHeader";
import { PaperRail } from "@/components/portal/paper-detail/PaperRail";
import { cn } from "@/lib/utils";
import type { PaperDetail } from "@/types/portal";

interface PublicationDetailViewProps {
  paper: PaperDetail;
  /** 事实条里来自全文接口的几格（页面在 Suspense 里流式给出） */
  fullTextFacts?: ReactNode;
  /** 全文区块（页面在 Suspense 里流式给出；没有时不渲染） */
  fullText?: ReactNode;
  /** 文献档案 */
  record?: ReactNode;
  /** 桌面侧栏的目录卡，只在有全文时放进侧栏 */
  tocCard?: ReactNode;
  /** 面包屑右端的移动端「目录」按钮 */
  tocButton?: ReactNode;
}

/// 文献详情（spec §4）：吸顶面包屑 → 整宽页头（标题、事实条、唯一一组操作）→ 两栏：
/// 主栏（摘要 → MeSH · 关键词 → 全文 → 文献档案，统一挂在标签栏上）+ 右侧栏（标识卡、AI 速读、目录卡）。
/// 没有全文时侧栏整体吸顶；有全文时侧栏卡片随页面滚走，下面的目录卡单独吸顶、一直陪到页底。
export function PublicationDetailView({
  paper,
  fullTextFacts,
  fullText,
  record,
  tocCard,
  tocButton,
}: PublicationDetailViewProps) {
  const crumb = paper.pmid ? `PMID ${paper.pmid}` : `#${paper.id}`;

  return (
    <div className="pb-6">
      <DetailCrumbs
        trail={[
          { label: "Patra", href: "/" },
          { label: "文献", href: "/papers" },
        ]}
        current={crumb}
        action={tocButton}
      />

      <div className="mx-auto max-w-page px-gutter pt-14 max-[980px]:pt-8">
        <PaperHeader paper={paper} fullTextFacts={fullTextFacts} />
      </div>

      <div className="mx-auto grid max-w-page grid-cols-[minmax(0,1fr)_340px] items-start gap-14 px-gutter pt-16 max-[1100px]:gap-10 max-[980px]:grid-cols-1 max-[980px]:gap-8 max-[980px]:pt-10">
        <div className="@container/main flex min-w-0 flex-col gap-12 max-md:gap-10">
          <section aria-labelledby="abstract-title" className="flex flex-col gap-7">
            <DetailSectionTitle id="abstract-title" zh="摘要" en="ABSTRACT" />
            <AbstractBlock paper={paper} />
          </section>

          <KeywordRow paper={paper} />

          {fullText}
          {record}
        </div>

        {/* sticky top = TopNav 57px + 面包屑 45px（均含 1px 底边）+ 32px 留白 = 134px */}
        <aside
          className={cn(
            "flex flex-col gap-4",
            paper.hasFullText ? "self-stretch" : "sticky top-[134px] max-[980px]:static",
          )}
        >
          <PaperRail paper={paper} />
          {paper.hasFullText && tocCard && (
            <div className="sticky top-[134px] max-[980px]:hidden">{tocCard}</div>
          )}
        </aside>
      </div>
    </div>
  );
}
