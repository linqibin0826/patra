import { Suspense } from "react";
import { Experiences } from "@/components/experience/Experiences";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import type { ExperienceSort } from "@/lib/interaction/experience-schema";

/// 期刊页的经验区（简报 §3.13，期刊页第一个互动区块）：`section#experiences`，自己一个 Suspense（决策 F）：
/// interaction 慢或不可用时期刊页主体照常出，经验区先是骨架、再是内容或「暂不可用」
export function ExperienceSection({
  journalId,
  journalTitle,
  sort,
}: {
  journalId: string;
  journalTitle: string;
  /// 地址里的列表排序（首屏按它取）
  sort: ExperienceSort;
}) {
  return (
    <section id="experiences" aria-labelledby="experiences-title" className="scroll-mt-[126px]">
      <Suspense
        fallback={
          <div className="flex flex-col gap-5">
            <SectionEyebrow as="h2" id="experiences-title" className="mb-0 min-h-9">
              投稿经验 · 审稿周期
            </SectionEyebrow>
            <div
              aria-hidden="true"
              className="grid motion-safe:animate-pulse grid-cols-[minmax(0,2fr)_minmax(0,3fr)] gap-7 rounded-lg border border-border-default bg-paper-50 p-5 max-[880px]:grid-cols-1"
            >
              <div className="flex flex-col gap-2.5">
                <span className="h-3 w-2/5 rounded bg-paper-200" />
                <span className="h-11 w-1/2 rounded bg-paper-200" />
                <span className="h-3 w-3/4 rounded bg-paper-200" />
              </div>
              <div className="flex flex-col justify-center gap-2.5">
                <span className="h-3 w-full rounded bg-paper-200" />
                <span className="h-3 w-4/5 rounded bg-paper-200" />
              </div>
            </div>
          </div>
        }
      >
        <Experiences journalId={journalId} journalTitle={journalTitle} sort={sort} />
      </Suspense>
    </section>
  );
}
