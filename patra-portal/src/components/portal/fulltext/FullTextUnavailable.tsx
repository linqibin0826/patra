import { ExternalLink } from "lucide-react";
import type { ReactNode } from "react";
import { sourceName } from "@/components/portal/fulltext/LicenseColophon";
import { PdfDownloadLink } from "@/components/portal/fulltext/PdfDownloadLink";
import { ReloadButton } from "@/components/portal/fulltext/ReloadButton";
import { formatFetchedDate, licenseLabel } from "@/lib/portal-api/fulltext-derive";
import { deriveFullText } from "@/lib/portal-api/publication-derive";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";
import type { FullText } from "@/types/fulltext";
import type { PaperDetail } from "@/types/portal";

export type UnavailableKind = "not-oa" | "pmc-only" | "retracted" | "pdf-only" | "error";

/** PMC 文章页地址（新版 pmc.ncbi.nlm.nih.gov）。 */
export function pmcArticleUrl(pmcid: string): string {
  return `https://pmc.ncbi.nlm.nih.gov/articles/${pmcid}/`;
}

function ExternalButton({
  href,
  primary,
  children,
}: {
  href: string;
  primary: boolean;
  children: ReactNode;
}) {
  return (
    <a
      className={primary ? btnPrimary : btnSecondary}
      href={href}
      target="_blank"
      rel="noopener noreferrer"
    >
      {children} <ExternalLink size={14} data-icon="trailing" aria-hidden />
    </a>
  );
}

const DT = "whitespace-nowrap pt-0.5 font-mono text-2xs tracking-mono text-fg-3";
const DD = "m-0 font-sans text-md leading-normal text-fg-1";
const NOTE = "mt-0.5 block font-sans text-sm text-fg-3";

/** 只有 PDF 时的署名小表（决策 D：页面在分发 PDF，同样要署名）。 */
function PdfAttribution({ fullText }: { fullText: FullText }) {
  const fetched = formatFetchedDate(fullText.fetchedAt);
  return (
    <dl className="m-0 mt-5 grid grid-cols-[max-content_1fr] gap-x-8 gap-y-3 border-t border-border-subtle pt-4 max-[540px]:grid-cols-1 max-[540px]:gap-y-1">
      <dt className={DT}>许可</dt>
      <dd className={DD}>
        {licenseLabel(fullText.license)}
        <span className={NOTE}>PDF 按原文许可转载，未作改动。</span>
      </dd>
      <dt className={DT}>数据来源</dt>
      <dd className={DD}>{sourceName(fullText.source)}</dd>
      <dt className={DT}>PMC</dt>
      <dd className={`${DD} font-mono text-sm`}>
        <a
          className="link-draw text-link hover:text-link-hover"
          href={fullText.pmcUrl}
          target="_blank"
          rel="noopener noreferrer"
        >
          {fullText.pmcid} ↗
        </a>
      </dd>
      {fetched && (
        <>
          <dt className={DT}>抓取日期</dt>
          <dd className={DD}>
            <span className="font-mono text-sm tabular-nums">{fetched}</span>
            <span className={NOTE}>PDF 抓取于 {fetched}，可能不是最新版本，以 PMC 原文为准。</span>
          </dd>
        </>
      )}
    </dl>
  );
}

/**
 * 全文区块的说明面板（简报 §3.5、§3.5.1、§3.6）：没有全文的三种原因、只有 PDF、加载失败。
 * 一句话说清原因，破折号接可能的情况；不写"暂时""即将"这类承诺。
 */
export function FullTextUnavailable({
  kind,
  paper,
  fullText,
}: {
  kind: UnavailableKind;
  paper: PaperDetail;
  fullText: FullText | null;
}) {
  const publisher = deriveFullText(paper);
  const toPublisher = (primary: boolean) =>
    publisher.href && (
      <ExternalButton href={publisher.href} primary={primary}>
        {publisher.label}
      </ExternalButton>
    );
  const pdf = (primary: boolean) =>
    paper.hasPdf && (
      <PdfDownloadLink paperId={paper.id} sizeBytes={paper.pdfSizeBytes} primary={primary} />
    );

  const content: Record<UnavailableKind, { title: string; text: string; actions: ReactNode }> = {
    "not-oa": {
      title: "暂无开放全文",
      text: "Patra 只转载 PubMed Central 开放获取子集里的全文——这篇文献不在其中，可以去出版方页面阅读。",
      actions: toPublisher(true),
    },
    "pmc-only": {
      title: "PMC 收录了这篇文献",
      text: "这篇文献收录在 PubMed Central，但没有开放许可或尚未开放——Patra 不能转载正文，可以去 PMC 查看。",
      actions: (
        <>
          {paper.pmcid && (
            <ExternalButton href={pmcArticleUrl(paper.pmcid)} primary>
              去 PMC 查看
            </ExternalButton>
          )}
          {toPublisher(false)}
        </>
      ),
    },
    retracted: {
      title: "已撤稿",
      text: "这篇文献已被撤稿，Patra 不展示其全文——撤稿说明见出版方页面。",
      actions: toPublisher(false),
    },
    "pdf-only": {
      title: "只有 PDF 版本",
      text: "PubMed Central 只提供了这篇文献的 PDF，没有可以在页面上阅读的正文——下载 PDF 即可阅读全文。",
      actions: (
        <>
          {pdf(true)}
          {toPublisher(false)}
        </>
      ),
    },
    error: {
      title: "全文没能加载",
      text: "网络或服务出了问题——刷新页面再试，或先下载 PDF。",
      actions: (
        <>
          <ReloadButton />
          {pdf(false)}
        </>
      ),
    },
  };
  const { title, text, actions } = content[kind];

  return (
    <div className="rounded-xl border border-border-default bg-paper-50 p-6">
      <h2 className="m-0 font-serif text-xl font-medium tracking-tight text-fg-1">{title}</h2>
      <p className="m-0 mt-2 mb-5 max-w-[36em] font-sans text-md leading-relaxed text-fg-2">
        {text}
      </p>
      <div className="flex flex-wrap gap-2.5">{actions}</div>
      {kind === "pdf-only" && fullText && <PdfAttribution fullText={fullText} />}
    </div>
  );
}
