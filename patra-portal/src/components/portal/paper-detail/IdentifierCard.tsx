"use client";

import { Check, Copy } from "lucide-react";
import { useEffect, useState } from "react";
import { pmcArticleUrl } from "@/components/portal/fulltext/FullTextUnavailable";

/** 复制成功后对勾停留的时长。 */
const COPIED_MS = 1600;

interface IdRow {
  type: "DOI" | "PMID" | "PMCID";
  value: string;
  href: string;
}

interface IdentifierCardProps {
  doi: string | null;
  pmid: string | null;
  pmcid: string | null;
}

function rowsOf({ doi, pmid, pmcid }: IdentifierCardProps): IdRow[] {
  const rows: IdRow[] = [];
  if (doi) rows.push({ type: "DOI", value: doi, href: `https://doi.org/${doi}` });
  if (pmid)
    rows.push({ type: "PMID", value: pmid, href: `https://pubmed.ncbi.nlm.nih.gov/${pmid}/` });
  if (pmcid) rows.push({ type: "PMCID", value: pmcid, href: pmcArticleUrl(pmcid) });
  return rows;
}

/// 侧栏标识卡（spec §5.15）：DOI / PMID / PMCID 各一行，值链到 doi.org / PubMed / PMC，右侧 44×44 复制按钮；
/// 复制成功后图标短暂换成对勾，并给读屏一句「已复制」。三个值都为空时不渲染。
/// 只接收三个标识（客户端组件，不把整份详情放进 RSC payload）。
export function IdentifierCard(props: IdentifierCardProps) {
  const rows = rowsOf(props);
  const [copied, setCopied] = useState<IdRow["type"] | null>(null);

  useEffect(() => {
    if (!copied) return;
    const timer = setTimeout(() => setCopied(null), COPIED_MS);
    return () => clearTimeout(timer);
  }, [copied]);

  if (rows.length === 0) {
    return null;
  }

  const copy = async (row: IdRow) => {
    try {
      await navigator.clipboard.writeText(row.value);
      setCopied(row.type);
    } catch {
      // 浏览器拒绝写剪贴板（非安全上下文等）时什么都不做，值本身仍可选中复制
    }
  };

  return (
    <section
      aria-labelledby="identifier-card-title"
      className="rounded-xl border border-border-default bg-paper-50 px-5 pt-5 pb-2"
    >
      <h2
        id="identifier-card-title"
        className="m-0 mb-1.5 font-sans text-2xs font-semibold tracking-caps text-fg-3"
      >
        标识
      </h2>
      {rows.map((row) => (
        <div
          key={row.type}
          className="flex min-h-11 items-center gap-3 border-b border-border-subtle last:border-b-0"
        >
          <span className="w-11 shrink-0 font-mono text-3xs tracking-mono text-fg-3">
            {row.type}
          </span>
          <a
            href={row.href}
            target="_blank"
            rel="noopener noreferrer"
            className="link-draw min-w-0 flex-1 font-mono text-sm [overflow-wrap:anywhere] text-fg-1 hover:text-link-hover"
          >
            {row.value}
          </a>
          <button
            type="button"
            aria-label={`复制 ${row.type}`}
            onClick={() => copy(row)}
            className="flex size-11 shrink-0 items-center justify-center text-fg-3 hover:text-fg-1"
          >
            {copied === row.type ? <Check size={14} aria-hidden /> : <Copy size={14} aria-hidden />}
          </button>
        </div>
      ))}
      <span role="status" className="sr-only">
        {copied ? "已复制" : ""}
      </span>
    </section>
  );
}
