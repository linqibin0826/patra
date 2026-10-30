import type { ReactNode } from "react";
import { DetailCrumbs } from "@/components/portal/DetailCrumbs";
import { AbstractBlock } from "@/components/portal/paper-detail/AbstractBlock";
import { KeywordRow } from "@/components/portal/paper-detail/KeywordRow";
import { PaperHeader } from "@/components/portal/paper-detail/PaperHeader";
import { PaperRail } from "@/components/portal/paper-detail/PaperRail";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { deriveFullTextState } from "@/lib/portal-api/fulltext-derive";
import { deriveKeywordItems } from "@/lib/portal-api/publication-derive";
import { cn } from "@/lib/utils";
import type { PaperDetail } from "@/types/portal";

interface PublicationDetailViewProps {
  paper: PaperDetail;
  /** 这篇的规范地址（页面按门户公开地址拼好），页头分享面板用 */
  shareLink: string;
  /** 事实条里来自全文接口的几格（页面在 Suspense 里流式给出） */
  fullTextFacts?: ReactNode;
  /** 事实条最后的收藏人数、讨论数两格（页面在 Suspense 里流式给出） */
  interactionFacts?: ReactNode;
  /** 全文区块（页面在 Suspense 里流式给出；没有时不渲染） */
  fullText?: ReactNode;
  /** 桌面侧栏的目录卡，只在有全文时放进侧栏 */
  tocCard?: ReactNode;
  /** 面包屑右端的移动端「目录」按钮 */
  tocButton?: ReactNode;
  /** 面包屑中段的短标题与阅读进度 */
  crumbStatus?: ReactNode;
  /** 讨论区（主栏最后、全文区块之后；页面在自己的 Suspense 里流式给出） */
  discussion?: ReactNode;
}

/// 文献详情（spec §4）：吸顶面包屑 → 整宽页头（标题、事实条、唯一一组操作）→ 两栏：
/// 主栏（摘要 → MeSH · 关键词 → 全文区块（含文末的文献档案）→ 讨论区，统一挂在标签栏上）+ 右侧栏（标识卡、AI 速读、目录卡）。
/// 没有全文时侧栏整体吸顶；有全文时侧栏卡片随页面滚走，下面的目录卡单独吸顶、一直陪到页底。
export function PublicationDetailView({
  paper,
  shareLink,
  fullTextFacts,
  interactionFacts,
  fullText,
  tocCard,
  tocButton,
  crumbStatus,
  discussion,
}: PublicationDetailViewProps) {
  const crumb = paper.pmid ? `PMID ${paper.pmid}` : `#${paper.id}`;
  // 与全文区块同一口径：已撤稿的文献即便后端标了有全文也不展示正文、不出目录卡
  const readable = deriveFullTextState(paper) === "full";

  return (
    <div className="pb-6">
      <DetailCrumbs
        trail={[
          { label: "Patra", href: "/" },
          { label: "文献", href: "/papers" },
        ]}
        current={crumb}
        status={crumbStatus}
        action={tocButton}
      />

      <div className="mx-auto max-w-page px-gutter pt-14 max-[980px]:pt-8">
        <PaperHeader
          paper={paper}
          fullTextFacts={fullTextFacts}
          interactionFacts={interactionFacts}
          shareLink={shareLink}
        />
      </div>

      <div className="mx-auto grid max-w-page grid-cols-[minmax(0,1fr)_340px] items-start gap-14 px-gutter pt-16 max-[1100px]:gap-10 max-[980px]:grid-cols-1 max-[980px]:gap-8 max-[980px]:pt-10">
        <div className="@container/main flex min-w-0 flex-col gap-12 max-md:gap-10">
          <section aria-labelledby="abstract-title" className="flex flex-col gap-7">
            <SectionEyebrow as="h2" id="abstract-title" className="mb-0">
              摘要 · Abstract
            </SectionEyebrow>
            <AbstractBlock paper={paper} />
          </section>

          <KeywordRow items={deriveKeywordItems(paper)} />

          {fullText}

          {discussion}
        </div>

        {/* sticky top = TopNav 57px + 面包屑 45px（均含 1px 底边）+ 32px 留白 = 134px */}
        <aside
          className={cn(
            "flex flex-col gap-4",
            readable ? "self-stretch" : "sticky top-[134px] max-[980px]:static",
          )}
        >
          <PaperRail paper={paper} />
          {readable && tocCard && (
            <div className="sticky top-[134px] max-[980px]:hidden">{tocCard}</div>
          )}
        </aside>
      </div>
    </div>
  );
}
