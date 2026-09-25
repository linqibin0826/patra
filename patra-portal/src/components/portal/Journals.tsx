import { ArrowRight } from "lucide-react";
import Link from "next/link";
import { JournalCoverCard } from "@/components/portal/JournalCoverCard";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { fetchVenues } from "@/lib/portal-api/venues";
import type { VenueBrowse } from "@/types/portal";

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
      className="container mx-auto max-w-[1200px] px-6 py-14 max-[880px]:py-10"
    >
      <div className="mb-6 flex items-end justify-between gap-3 max-[880px]:flex-col max-[880px]:items-start">
        <div>
          <SectionEyebrow className="mb-0">按期刊浏览</SectionEyebrow>
          <h2 className="mt-1 font-serif text-3xl font-medium leading-tight tracking-tight text-ink-900">
            高影响力期刊
          </h2>
          <p className="mt-1 text-sm text-fg-3">
            从信赖的来源切入。Patra 持续追踪 15,434 本同行评审期刊；以下为影响因子最高的 6 本。
          </p>
        </div>
        <Link
          href="/journals"
          className="inline-flex items-center gap-1.5 text-sm text-clay-700 hover:text-clay-800"
        >
          浏览全部期刊 <ArrowRight className="h-3.5 w-3.5" strokeWidth={1.5} />
        </Link>
      </div>

      <div className="grid grid-cols-6 gap-4 max-[1200px]:grid-cols-3 max-[720px]:grid-cols-none max-[720px]:-mx-5 max-[720px]:flex max-[720px]:gap-3 max-[720px]:overflow-x-auto max-[720px]:snap-x max-[720px]:snap-mandatory max-[720px]:px-5 max-[720px]:py-1 max-[720px]:[scrollbar-width:none] max-[720px]:[&::-webkit-scrollbar]:hidden">
        {journals.map((j) => (
          <JournalCoverCard
            key={j.id}
            journal={j}
            className="max-[720px]:shrink-0 max-[720px]:min-w-[200px] max-[720px]:basis-[56vw] max-[720px]:snap-start"
          />
        ))}
      </div>
    </section>
  );
}
