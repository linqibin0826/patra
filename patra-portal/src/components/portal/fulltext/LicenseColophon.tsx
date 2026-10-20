import { formatFetchedDate, licenseLabel } from "@/lib/portal-api/fulltext-derive";
import type { FullText } from "@/types/fulltext";
import type { PaperDetail } from "@/types/portal";

const DL =
  "m-0 grid grid-cols-[max-content_1fr] gap-x-8 gap-y-3 max-[540px]:grid-cols-1 max-[540px]:gap-y-1";
const DT = "whitespace-nowrap pt-0.5 font-mono text-2xs tracking-mono text-fg-3 max-[540px]:pt-2.5";
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
 * NLM 数据来源、作者手稿版本说明、抓取日期与「可能不是最新」的说明。
 */
export function LicenseColophon({ fullText, paper }: { fullText: FullText; paper: PaperDetail }) {
  const fetched = formatFetchedDate(fullText.fetchedAt);
  const shownAuthors = paper.authors.slice(0, MAX_AUTHORS);
  const citation = [paper.venueName, paper.publicationYear].filter((v) => v != null).join(". ");

  return (
    <section
      id="license"
      aria-labelledby="license-title"
      className="mt-12 scroll-mt-[45px] rounded-xl border border-border-default bg-paper-50 p-6"
    >
      <h2
        id="license-title"
        className="m-0 mb-4 font-serif text-xl font-medium tracking-tight text-fg-1"
      >
        许可与出处
      </h2>
      <dl className={DL}>
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

        {shownAuthors.length > 0 && (
          <>
            <dt className={DT}>作者</dt>
            <dd className={DD}>
              {shownAuthors.map((a) => a.name).join("、")}
              {paper.authors.length > MAX_AUTHORS && (
                <span className="text-fg-3"> 等 {paper.authors.length} 位</span>
              )}
            </dd>
          </>
        )}

        {citation && (
          <>
            <dt className={DT}>出处</dt>
            <dd className={DD}>
              {paper.venueName && <span className="font-serif italic">{paper.venueName}</span>}
              {paper.venueName && paper.publicationYear != null && ". "}
              {paper.publicationYear}
            </dd>
          </>
        )}

        {paper.doi && (
          <>
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
          </>
        )}

        <dt className={DT}>PMC</dt>
        <dd className={`${DD} font-mono text-sm`}>
          <a className={LINK} href={fullText.pmcUrl} target="_blank" rel="noopener noreferrer">
            {fullText.pmcid} ↗
          </a>
        </dd>

        <dt className={DT}>数据来源</dt>
        <dd className={DD}>{sourceName(fullText.source)}</dd>

        {fullText.isManuscript && (
          <>
            <dt className={DT}>版本</dt>
            <dd className={DD}>
              作者手稿
              <span className={NOTE}>
                这是期刊接受后由作者提交的稿件（Author
                Manuscript），可能与期刊正式发表的版本有出入。
              </span>
            </dd>
          </>
        )}

        {fetched && (
          <>
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
          </>
        )}
      </dl>
    </section>
  );
}
