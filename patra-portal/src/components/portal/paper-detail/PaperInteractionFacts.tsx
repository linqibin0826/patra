import {
  PaperDiscussionFact,
  PaperSavedFact,
} from "@/components/portal/paper-detail/PaperCountFacts";
import { fetchPaperStats } from "@/lib/interaction/stats";

/// 事实条里来自 interaction 的两格（async RSC，调用方包在 Suspense 里）：收藏人数、讨论数。取不到时两格都不出现，
/// 事实条其余格子照常（简报 §3.14）
export async function PaperInteractionFacts({ paperId }: { paperId: string }) {
  const stats = (await fetchPaperStats([paperId]))?.get(paperId);
  if (!stats) {
    return null;
  }
  return (
    <>
      <PaperSavedFact paperKey={paperId} collectorCount={stats.collectorCount} />
      <PaperDiscussionFact paperKey={paperId} discussionCount={stats.discussionCount} />
    </>
  );
}
