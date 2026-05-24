import { ArrowRight } from "lucide-react";
import Image from "next/image";
import { JournalCoverCard } from "@/components/portal/JournalCoverCard";
import { JOURNALS } from "@/data/journals";

export function Journals() {
  return (
    <section
      data-section="journals"
      className="container mx-auto max-w-[1200px] px-6 py-14 max-[880px]:py-10"
    >
      <div className="mb-6 flex items-end justify-between gap-3 max-[880px]:flex-col max-[880px]:items-start">
        <div>
          <span className="inline-flex items-center gap-1.5 font-sans text-2xs font-semibold uppercase tracking-caps text-fg-3">
            <Image src="/brand/patra-mark.svg" alt="" aria-hidden width={4} height={14} />
            按期刊浏览
          </span>
          <h2 className="mt-1 font-serif text-3xl font-medium leading-tight tracking-tight text-ink-900">
            高影响力期刊
          </h2>
          <p className="mt-1 text-sm text-fg-3">
            从信赖的来源切入。Patra 持续追踪 1,247 本同行评审期刊；以下为本周收录最活跃的 6 本。
          </p>
        </div>
        <button
          type="button"
          disabled
          aria-disabled="true"
          title="功能即将上线"
          className="inline-flex items-center gap-1.5 text-sm text-clay-700 hover:text-clay-800 disabled:cursor-not-allowed disabled:opacity-60"
        >
          浏览全部 1,247 本 <ArrowRight className="h-3.5 w-3.5" strokeWidth={1.5} />
        </button>
      </div>

      <div className="grid grid-cols-6 gap-4 max-[1200px]:grid-cols-3 max-[720px]:grid-cols-none max-[720px]:-mx-5 max-[720px]:flex max-[720px]:gap-3 max-[720px]:overflow-x-auto max-[720px]:snap-x max-[720px]:snap-mandatory max-[720px]:px-5 max-[720px]:py-1 max-[720px]:[scrollbar-width:none] max-[720px]:[&::-webkit-scrollbar]:hidden">
        {JOURNALS.map((j) => (
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
