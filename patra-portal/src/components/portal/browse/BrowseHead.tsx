import Link from "next/link";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";

interface BrowseHeadProps {
  /** 面包屑末级（当前页） */
  crumb: string;
  eyebrow: string;
  title: string;
  description: string;
}

/**
 * 浏览页标题区（静态 RSC）：面包屑 + eyebrow + h1 + 副文案。
 */
export function BrowseHead({ crumb, eyebrow, title, description }: BrowseHeadProps) {
  return (
    <div className="border-b border-border-default pb-6">
      <nav aria-label="面包屑" className="mb-4 flex items-center gap-1.5 text-xs text-fg-3">
        <Link href="/" className="transition-colors hover:text-ink-700">
          Patra
        </Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{crumb}</span>
      </nav>

      <SectionEyebrow className="mb-0">{eyebrow}</SectionEyebrow>

      <h1 className="mt-1 font-serif text-3xl font-medium leading-tight tracking-tight text-ink-900">
        {title}
      </h1>

      <p className="mt-2 max-w-xl text-sm text-fg-3">{description}</p>
    </div>
  );
}
