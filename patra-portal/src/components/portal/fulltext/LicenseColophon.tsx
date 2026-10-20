import { RailRow } from "@/components/portal/RailRow";
import { formatFetchedDate, licenseLabel } from "@/lib/portal-api/fulltext-derive";
import type { FullText } from "@/types/fulltext";
import type { PaperDetail } from "@/types/portal";

/** 每一行：dt 在 128px 标签格、dd 在内容格（标签栏几何见 `RailRow`）。 */
const ROW =
  "grid grid-cols-[128px_minmax(0,1fr)] gap-x-8 gap-y-1 border-b border-border-subtle py-3 @max-[580px]/main:grid-cols-1";
const DT = "pt-0.5 font-mono text-2xs tracking-mono text-fg-3";
const DD = "m-0 font-sans text-md leading-normal text-fg-1";
const NOTE = "mt-0.5 block font-sans text-sm text-fg-3";
const LINK = "link-draw text-link hover:text-link-hover";
const MAX_AUTHORS = 10;

/** 全文接口的 `source` 去掉「数据来源：」前缀，版权页的行名已经说了。 */
export function sourceName(source: string): string {
  return source.replace(/^数据来源[：:]\s*/, "");
}

/**
 * 文末「许可与出处」版权页（简报 §3.4，决策 D / E）：许可、作者、出处、DOI、PMC、
 * NLM 数据来源、作者手稿版本说明、抓取日期与「可能不是最新」的说明。PAP-89 起按标签栏排版，内容不变。
 */
export function LicenseColophon({ fullText, paper }: { fullText: FullText; paper: PaperDetail }) {
  const fetched = formatFetchedDate(fullText.fetchedAt);
  const shownAuthors = paper.authors.slice(0, MAX_AUTHORS);
  const citation = [paper.venueName, paper.publicationYear].filter((v) => v != null).join(". ");

  return (
    <section id="license" aria-labelledby="license-title" className="mt-16 scroll-mt-[45px]">
      <RailRow className="mb-4">
        <h2
          id="license-title"
          className="m-0 font-serif text-2xl font-medium tracking-tight text-fg-1"
        >
          许可与出处
        </h2>
      </RailRow>
      <dl className="m-0 border-t border-border-subtle">
        <div className={ROW}>
          <dt className={DT}>许可</dt>
          <dd className={DD}>
            {fullText.license?.url ? (
              <a
                className={LINK}
                href={fullText.license.url}
                target="_blank"
                rel="noopener noreferrer"
              >
                {licenseLabel(fullText.license)} ↗
              </a>
            ) : (
              licenseLabel(fullText.license)
            )}
            <span className={NOTE}>Patra 按原文许可转载，只做格式转换，未改动内容。</span>
          </dd>
        </div>

        {shownAuthors.length > 0 && (
          <div className={ROW}>
            <dt className={DT}>作者</dt>
            <dd className={DD}>
              {shownAuthors.map((a) => a.name).join("、")}
              {paper.authors.length > MAX_AUTHORS && (
                <span className="text-fg-3"> 等 {paper.authors.length} 位</span>
              )}
            </dd>
          </div>
        )}

        {citation && (
          <div className={ROW}>
            <dt className={DT}>出处</dt>
            <dd className={DD}>
              {paper.venueName && <span className="font-serif italic">{paper.venueName}</span>}
              {paper.venueName && paper.publicationYear != null && ". "}
              {paper.publicationYear}
            </dd>
          </div>
        )}

        {paper.doi && (
          <div className={ROW}>
            <dt className={DT}>DOI</dt>
            <dd className={`${DD} font-mono text-sm`}>
              <a
                className={LINK}
                href={`https://doi.org/${paper.doi}`}
                target="_blank"
                rel="noopener noreferrer"
              >
                {paper.doi} ↗
              </a>
            </dd>
          </div>
        )}

        <div className={ROW}>
          <dt className={DT}>PMC</dt>
          <dd className={`${DD} font-mono text-sm`}>
            <a className={LINK} href={fullText.pmcUrl} target="_blank" rel="noopener noreferrer">
              {fullText.pmcid} ↗
            </a>
          </dd>
        </div>

        <div className={ROW}>
          <dt className={DT}>数据来源</dt>
          <dd className={DD}>{sourceName(fullText.source)}</dd>
        </div>

        {fullText.isManuscript && (
          <div className={ROW}>
            <dt className={DT}>版本</dt>
            <dd className={DD}>
              作者手稿
              <span className={NOTE}>
                这是期刊接受后由作者提交的稿件（Author
                Manuscript），可能与期刊正式发表的版本有出入。
              </span>
            </dd>
          </div>
        )}

        {fetched && (
          <div className={ROW}>
            <dt className={DT}>抓取日期</dt>
            <dd className={DD}>
              <span className="font-mono text-sm tabular-nums">{fetched}</span>
              <span className={NOTE}>
                本页全文抓取于 {fetched}，PMC 之后若有更新，这里可能不是最新版本，以 PMC 原文为准。
                <a
                  className={`${LINK} ml-1.5`}
                  href={fullText.pmcUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  查看 PMC 最新版 ↗
                </a>
              </span>
            </dd>
          </div>
        )}
      </dl>
    </section>
  );
}
