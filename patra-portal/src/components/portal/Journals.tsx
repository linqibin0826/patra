import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { JournalCoverCard } from "@/components/portal/JournalCoverCard";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { fetchVenues } from "@/lib/portal-api/venues";
import type { VenueBrowse } from "@/types/portal";

/// 首页「高影响力期刊」书架：影响因子最高的 6 本期刊封面一字排开；窄屏横向滑动。
export async function Journals() {
  let journals: VenueBrowse[];
  try {
    journals = await fetchVenues(6);
  } catch {
    // 期刊榜加载失败时静默隐藏整个区块，不阻塞首页其他模块的渲染
    return null;
  }
  if (journals.length === 0) {
    return null;
  }

  return (
    <section
      data-section="journals"
      className="mx-auto max-w-page px-gutter py-[clamp(80px,10vw,136px)]"
    >
      <div className="flex items-end justify-between gap-6 max-md:flex-col max-md:items-start">
        <div>
          <SectionEyebrow index="02" className="mb-5">
            按期刊浏览
          </SectionEyebrow>
          <h2 className="font-serif text-display-3 leading-heading font-medium tracking-tight text-ink-900">
            高影响力期刊
          </h2>
          <p className="mt-4 max-w-[40em] text-sm leading-relaxed break-keep text-fg-3">
            从信赖的来源切入。Patra 持续追踪 15,434 本同行评审期刊；以下为影响因子最高的 6 本。
          </p>
        </div>
        <Link
          href="/journals"
          className="group/link inline-flex shrink-0 items-center gap-1.5 text-sm font-medium text-ink-900"
        >
          <span className="link-draw pb-px">浏览全部期刊</span>
          <ArrowRight
            className="size-3.5 motion-safe:transition-transform duration-300 ease-out-expo motion-safe:group-hover/link:translate-x-0.5"
            strokeWidth={1.75}
            aria-hidden
          />
        </Link>
      </div>

      <ul className="reveal mt-12 grid grid-cols-6 gap-x-6 [--shelf-gap:--spacing(6)] max-[720px]:[--shelf-gap:--spacing(4)] gap-y-10 max-[1200px]:grid-cols-3 max-[720px]:-mx-gutter max-[720px]:flex max-[720px]:snap-x max-[720px]:snap-mandatory max-[720px]:gap-4 max-[720px]:overflow-x-auto max-[720px]:scroll-px-gutter max-[720px]:px-gutter max-[720px]:pt-2 max-[720px]:pb-4 max-[720px]:[scrollbar-width:none]">
        {journals.map((j) => (
          <li
            key={j.id}
            className="flex max-[720px]:w-[58vw] max-[720px]:max-w-[240px] max-[720px]:shrink-0 max-[720px]:snap-start"
          >
            <JournalCoverCard journal={j} className="w-full" />
          </li>
        ))}
      </ul>
    </section>
  );
}
