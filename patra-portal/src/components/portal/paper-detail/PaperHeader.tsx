import Link from "next/link";
import type { ReactNode } from "react";
import { LibraryStatusProvider } from "@/components/library/LibraryStatusProvider";
import { SaveButton } from "@/components/library/SaveButton";
import { ShareButton } from "@/components/library/SharePanel";
import { pmcArticleUrl } from "@/components/portal/fulltext/FullTextUnavailable";
import {
  FullTextPdfAction,
  FullTextPrimaryAction,
} from "@/components/portal/paper-detail/FullTextActions";
import { PaperFacts } from "@/components/portal/paper-detail/PaperFacts";
import { RichInlineNodes, RichInlineText } from "@/components/portal/RichInlineText";
import { deriveFullTextState } from "@/lib/portal-api/fulltext-derive";
import { deriveByline } from "@/lib/portal-api/publication-derive";
import { splitTitle } from "@/lib/portal-api/title-derive";
import { formatPublishedDate } from "@/lib/published-date";
import { cn } from "@/lib/utils";
import type { PaperDetail } from "@/types/portal";

/// ≤ 540px 次按钮的紧凑竖排形态：48px 高，图标在上、文字在下（简报 §3.3 方案 A）
const COMPACT =
  "max-[540px]:h-12 max-[540px]:flex-col max-[540px]:gap-1 max-[540px]:px-2 max-[540px]:py-1 max-[540px]:text-xs max-[540px]:[&>svg]:shrink-0";

const TITLE_CLASS = {
  lg: "max-w-[20em] text-[clamp(30px,1.4rem+1.6vw,46px)] leading-[1.1]",
  md: "max-w-[24em] text-[clamp(26px,1.2rem+1.2vw,36px)] leading-[1.15]",
} as const;

/// 文献页头（spec §5.1 – §5.3，整宽）：等宽刊头行 → 主标题与副标题 → 原文标题 → 作者 · 刊名 · 按精度的出版日期 →
/// 事实条 → 全页唯一一组操作（阅读全文 / 下载 PDF / 收藏 / 分享 / 在 PMC 查看原文）。
export function PaperHeader({
  paper,
  fullTextFacts,
  interactionFacts,
  shareLink,
}: {
  paper: PaperDetail;
  /** 事实条里来自全文接口的几格（Suspense 包好的节点） */
  fullTextFacts?: ReactNode;
  /** 事实条最后的收藏人数、讨论数两格（Suspense 包好的节点）；本人收藏 / 取消时收藏格 ±1，所以和收藏按钮在同一个
   *  `LibraryStatusProvider` 里 */
  interactionFacts?: ReactNode;
  /** 这篇的规范地址（服务端按门户公开地址拼好），分享面板「复制链接」用 */
  shareLink: string;
}) {
  const { shown, extra } = deriveByline(paper.authors);
  const state = deriveFullTextState(paper);
  const retracted = state === "retracted";
  const types = paper.publicationTypes.slice(0, 2);
  const title = splitTitle(paper.title);
  const published =
    formatPublishedDate(paper.publishedDate) ??
    (paper.publicationYear == null ? null : String(paper.publicationYear));
  const pmcUrl =
    (state === "full" || state === "pdf-only") && paper.pmcid ? pmcArticleUrl(paper.pmcid) : null;

  return (
    <section id="masthead" aria-label="题录" className="flex flex-col">
      {(types.length > 0 || paper.isOa || retracted) && (
        <div className="anim-fade mb-5 flex flex-wrap items-center gap-x-3 gap-y-2 font-mono text-2xs uppercase tracking-mono text-fg-3">
          {types.map((t, i) => (
            <span key={t} className="inline-flex items-center gap-3">
              {i > 0 && (
                <span aria-hidden className="text-ink-300">
                  ·
                </span>
              )}
              {t}
            </span>
          ))}
          {paper.isOa && (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-moss-500 bg-moss-50 px-2.5 py-0.5 text-status-success-ink">
              <span aria-hidden className="size-1.5 rounded-full bg-moss-500" />
              开放获取
            </span>
          )}
          {retracted && (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-rust-500 bg-rust-50 px-2.5 py-0.5 text-fg-1">
              <span aria-hidden className="size-1.5 rounded-full bg-status-danger" />
              已撤稿
            </span>
          )}
        </div>
      )}

      <h1
        data-size={title.size}
        className={cn(
          "anim-rise m-0 font-serif font-medium tracking-tight text-balance text-fg-1",
          TITLE_CLASS[title.size],
        )}
      >
        <RichInlineNodes nodes={title.main} />
      </h1>
      {title.sub && (
        <p className="anim-rise m-0 mt-4 max-w-[34em] font-serif text-[clamp(19px,1rem+0.5vw,24px)] leading-[1.38] tracking-normal text-pretty text-fg-2">
          <RichInlineNodes nodes={title.sub} />
        </p>
      )}
      {paper.originalTitle && (
        <p className="m-0 mt-3 font-serif text-xl leading-snug text-fg-3 italic">
          <RichInlineText text={paper.originalTitle} />
        </p>
      )}

      <div className="anim-rise mt-6 flex flex-wrap items-center gap-y-1 font-sans text-md leading-normal text-fg-2 [--delay:120ms]">
        {shown.map((a, i) => (
          <span key={a.order} className="whitespace-nowrap">
            {a.name}
            {a.corresponding && (
              <span className="ml-px font-semibold text-clay-600" title="通讯作者">
                {" "}
                ✉
              </span>
            )}
            {i < shown.length - 1 ? "、" : ""}
          </span>
        ))}
        {extra > 0 && <span className="text-fg-3">&nbsp;等 {extra} 位</span>}
        <span aria-hidden className="mx-3 inline-block size-[3px] rounded-full bg-ink-300" />
        {paper.venueId && paper.venueName ? (
          <Link
            href={`/journals/${paper.venueId}`}
            className="link-draw pb-px font-serif text-lg font-medium text-link italic hover:text-link-hover"
          >
            {paper.venueName}
          </Link>
        ) : (
          paper.venueName && (
            <span className="font-serif text-lg text-ink-800 italic">{paper.venueName}</span>
          )
        )}
        {published != null && <span className="tabular-nums text-fg-2">&nbsp;· {published}</span>}
      </div>

      <LibraryStatusProvider keys={[paper.id]}>
        <PaperFacts
          paper={paper}
          fullTextFacts={fullTextFacts}
          interactionFacts={interactionFacts}
        />

        <div className="anim-rise mt-6 flex flex-wrap items-center gap-3 [--delay:200ms] max-[540px]:flex-col max-[540px]:items-stretch">
          <FullTextPrimaryAction paper={paper} />
          {/* 次按钮组：桌面与主按钮同一行；≤ 540px 等分一行、图标在上文字在下（方案 A），
            有几个就几等分，「收藏暂不可用」变长也不挤动其他按钮 */}
          {/* biome-ignore lint/a11y/useSemanticElements: 这是一组操作按钮，不是表单控件，用 fieldset 不对 */}
          <div
            role="group"
            aria-label="次要操作"
            className="contents max-[540px]:grid max-[540px]:grid-flow-col max-[540px]:auto-cols-fr max-[540px]:gap-2"
          >
            <FullTextPdfAction paper={paper} className={COMPACT} />
            <SaveButton
              paperKey={paper.id}
              title={paper.title}
              variant="pill"
              className={COMPACT}
            />
            <ShareButton
              paperId={paper.id}
              pmid={paper.pmid}
              link={shareLink}
              className={COMPACT}
            />
          </div>
          {pmcUrl && (
            <a
              href={pmcUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="link-draw ml-2 font-sans text-sm font-medium text-fg-2 hover:text-fg-1 max-[540px]:ml-0 max-[540px]:self-start"
            >
              在 PMC 查看原文 ↗
            </a>
          )}
        </div>
      </LibraryStatusProvider>
    </section>
  );
}
