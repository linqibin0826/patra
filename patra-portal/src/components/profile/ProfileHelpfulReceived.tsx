import { formatCount } from "@/lib/format-count";
import { fetchHelpfulReceived } from "@/lib/interaction/stats";

/// 主页头部计数行的「获赞 N」（v0.15 简报 §3.11，async RSC，调用方包在 Suspense 里）：本人未删除、非匿名的评论与
/// 经验收到的有帮助总数。0 或取不到时整行不出现；关注、粉丝两格本版不做、不占位
export async function ProfileHelpfulReceived({ userId }: { userId: string }) {
  const count = await fetchHelpfulReceived(userId);
  if (count === null || count === 0) {
    return null;
  }
  return (
    <p className="mt-3 text-sm text-fg-3">
      获赞 <span className="font-semibold tabular-nums text-fg-1">{formatCount(count)}</span>
    </p>
  );
}
