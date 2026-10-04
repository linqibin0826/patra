import Link from "next/link";
import { CjkDashText } from "@/components/portal/CjkDashText";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";

interface BrowseHeadProps {
  /** 面包屑末级（当前页） */
  crumb: string;
  eyebrow: string;
  title: string;
  description: string;
}

/**
 * 浏览页标题区（静态 RSC）：等宽面包屑 + eyebrow + 显示级 h1 + 副文案，底部发丝线收束。
 */
export function BrowseHead({ crumb, eyebrow, title, description }: BrowseHeadProps) {
  return (
    <div className="border-b border-border-default pb-8 max-md:pb-7">
      <nav
        aria-label="面包屑"
        className="mb-7 flex items-center gap-2 font-mono text-2xs tracking-mono text-fg-3 max-md:mb-5"
      >
        <Link href="/" className="link-draw pb-px hover:text-ink-900">
          Patra
        </Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page" className="text-fg-2">
          {crumb}
        </span>
      </nav>

      <SectionEyebrow className="anim-fade mb-4">{eyebrow}</SectionEyebrow>

      <h1 className="anim-rise font-serif text-display-3 leading-heading font-medium tracking-tight text-ink-900 [--delay:60ms]">
        {title}
      </h1>

      <p className="anim-rise mt-4 max-w-[46em] text-md leading-relaxed break-keep text-fg-2 [--delay:140ms]">
        <CjkDashText>{description}</CjkDashText>
      </p>
    </div>
  );
}
