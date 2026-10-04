import type { Author } from "@/types/portal";

export function AuthorList({ authors }: { authors: Author[] }) {
  return (
    <ul className="flex flex-col">
      {authors.map((a) => (
        <li
          key={a.order}
          className="flex items-baseline gap-4 border-t border-border-subtle py-3 first:border-t-0"
        >
          <span className="w-6 flex-shrink-0 text-right font-mono text-2xs tabular-nums text-fg-3">
            {a.order}
          </span>
          <span className="flex min-w-0 flex-col gap-0.5">
            <span className="font-sans text-md font-semibold text-ink-900">
              {a.name}
              {a.first && (
                <span className="ml-2 rounded-full border border-border-default bg-paper-50 px-2 py-px align-middle font-sans text-3xs font-semibold text-fg-2">
                  第一作者
                </span>
              )}
              {a.corresponding && (
                <span className="ml-2 rounded-full border border-clay-200 bg-clay-50 px-2 py-px align-middle font-sans text-3xs font-semibold text-clay-800">
                  <span aria-hidden>✉</span> 通讯
                </span>
              )}
            </span>
            {a.affiliation && (
              <span className="font-sans text-sm leading-snug text-fg-3">{a.affiliation}</span>
            )}
          </span>
        </li>
      ))}
    </ul>
  );
}
