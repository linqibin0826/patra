import { Suspense } from "react";
import { Discussion, type DiscussionEntryProps } from "@/components/discussion/Discussion";
import { DiscussionSkeleton } from "@/components/discussion/DiscussionSkeleton";
import { DiscussionHeader } from "@/components/discussion/discussion-parts";

/// 讨论区的区块（简报 §3.1）：`section#discussion`，自己一个 Suspense（决策 F）：interaction 慢或不可用时文献页、
/// 经验页主体照常出，讨论区先是骨架、再是内容或「暂不可用」
export function DiscussionSection(props: DiscussionEntryProps) {
  return (
    // tabIndex：删除最后一条评论后焦点落回讨论区
    <section
      id="discussion"
      aria-labelledby="discussion-title"
      tabIndex={-1}
      className="scroll-mt-[126px]"
    >
      <Suspense
        fallback={
          <div className="flex max-w-190 flex-col gap-5">
            <DiscussionHeader title={props.copy.title} titleId="discussion-title" />
            <DiscussionSkeleton />
          </div>
        }
      >
        <Discussion {...props} />
      </Suspense>
    </section>
  );
}
