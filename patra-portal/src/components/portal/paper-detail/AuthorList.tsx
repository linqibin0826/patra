import { Pill } from "@/components/portal/Pill";
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
                <Pill size="sm" className="ml-2 align-middle">
                  第一作者
                </Pill>
              )}
              {a.corresponding && (
                <Pill tone="clay" size="sm" className="ml-2 align-middle">
                  <span aria-hidden>✉</span> 通讯
                </Pill>
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
