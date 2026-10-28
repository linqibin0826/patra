import type { Metadata } from "next";
import { formatOutcomeLine, formatSubmittedMonth } from "@/lib/experience/format";
import { graphemes } from "@/lib/grapheme";
import type { PostDetail } from "@/lib/interaction/experience-schema";
import type { PublicCollection } from "@/lib/interaction/schema";
import { excerpt } from "@/lib/markdown/plain";
import { inlinePlainText, shortTitle } from "@/lib/portal-api/title-derive";
import { formatPublishedDate } from "@/lib/published-date";
import { parseInlineMarkup } from "@/lib/rich-inline-text";
import type { PaperDetail, VenueDetail } from "@/types/portal";

/// 页面信息（简报 §3.18、§3.14）：由页面已经取到的数据构造，不碰网络。`canonical` 是绝对地址
/// （`publicUrl()` 拼好的）：项目没设 `metadataBase`，相对地址会被解析到默认的 localhost。
/// 带 Open Graph 的文字标签，不带分享图（release spec §3 H）。

const SITE = "Patra";
const PAPER_DESCRIPTION_MAX = 160;
const COLLECTION_DESCRIPTION_MAX = 120;
const POST_BODY_DESCRIPTION_MAX = 100;

/// 去掉内联标记（`<i>`、`<sup>` 等），连续空白压成一个空格
function plainText(value: string): string {
  return inlinePlainText(parseInlineMarkup(value)).replace(/\s+/g, " ").trim();
}

/// 按字素截到 `max` 个，被截断时末尾加 `…`
function truncate(value: string, max: number): string {
  const parts = graphemes(value);
  return parts.length > max ? `${parts.slice(0, max).join("")}…` : value;
}

/// 组装：描述为空时不输出 `description`（不留空的 meta）
function build(
  title: string,
  description: string,
  canonical: string,
  type: "article" | "website",
): Metadata {
  return {
    title: `${title} · ${SITE}`,
    ...(description ? { description } : {}),
    alternates: { canonical },
    openGraph: {
      title,
      ...(description ? { description } : {}),
      url: canonical,
      siteName: SITE,
      type,
    },
  };
}

/// 文献页：摘要纯文本前 160 字素；没有摘要时 `<期刊> · <按精度的出版日期> · <前 3 位作者>` 中有的部分
function paperDescription(paper: PaperDetail): string {
  const abstract =
    paper.abstractSections.length > 0
      ? paper.abstractSections.map((section) => section.text).join(" ")
      : (paper.abstractPlainText ?? "");
  const text = plainText(abstract);
  if (text) {
    return truncate(text, PAPER_DESCRIPTION_MAX);
  }
  const date =
    formatPublishedDate(paper.publishedDate) ??
    (paper.publicationYear == null ? null : String(paper.publicationYear));
  const authors = [...paper.authors].sort((a, b) => a.order - b.order);
  const names =
    authors.length === 0
      ? null
      : `${authors
          .slice(0, 3)
          .map((a) => a.name)
          .join(", ")}${authors.length > 3 ? " 等" : ""}`;
  return [paper.venueName, date, names].filter(Boolean).join(" · ");
}

export function paperMetadata(paper: PaperDetail, canonical: string): Metadata {
  return build(shortTitle(paper.title), paperDescription(paper), canonical, "article");
}

/// 期刊页：`<刊名>（ISSN <issnL>）`。`VenueDetail` 没有出版者字段，模板里只落有的部分
export function journalMetadata(venue: VenueDetail, canonical: string): Metadata {
  const description = venue.issnL ? `${venue.title}（ISSN ${venue.issnL}）` : venue.title;
  return build(venue.title, description, canonical, "website");
}

/// 公开收藏夹页：标题 `组会 · 陈宇的收藏夹`；描述 = 简介前 120 字素，没有简介时一句概述。
/// 所有者的主页不允许收录（或取不到收录设置）时 `noindex`
export function collectionMetadata(
  collection: PublicCollection,
  canonical: string,
  indexable: boolean,
): Metadata {
  const ownerName = collection.owner?.displayName ?? null;
  const title = `${collection.name} · ${ownerName ? `${ownerName}的收藏夹` : "收藏夹"}`;
  const intro = collection.description ? plainText(collection.description) : "";
  const description = intro
    ? truncate(intro, COLLECTION_DESCRIPTION_MAX)
    : `${ownerName ? `${ownerName}在 Patra 公开的收藏夹` : "Patra 上公开的收藏夹"}，共 ${collection.itemCount} 篇文献。`;
  return {
    ...build(title, description, canonical, "website"),
    ...(indexable ? {} : { robots: { index: false } }),
  };
}

/// 经验页（简报 §3.16、spec 8.5）：标题 `<期刊缩写> 投稿经验 · 2026 年 3 月投稿`；描述 = 结果行 + 正文纯文本前
/// 100 字。不写作者：匿名经验不能出现，实名的也不写，统一一种写法。已删除的不允许收录
export function postMetadata(detail: PostDetail, canonical: string): Metadata {
  const { experience } = detail;
  if (detail.deleted || !experience) {
    return { title: `该经验已删除 · ${SITE}`, robots: { index: false } };
  }
  const journal = detail.journal.abbreviation ?? detail.journal.title;
  const title = `${journal} 投稿经验 · ${formatSubmittedMonth(experience.submittedOn)}`;
  const outcome = `${formatOutcomeLine(experience).replace("  →  ", " → ")}。`;
  const body = experience.body ? excerpt(experience.body, POST_BODY_DESCRIPTION_MAX) : "";
  return build(title, `${outcome}${body}`, canonical, "article");
}
