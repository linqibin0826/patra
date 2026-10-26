import Link from "next/link";
import { InteractionUnavailable } from "@/components/library/InteractionUnavailable";
import { formatMonthDay } from "@/lib/format-time";
import type { Read } from "@/lib/interaction/read";
import type { PublicCollectionCard } from "@/lib/interaction/schema";
import { formatCount } from "@/lib/library/scope";
import { btnSecondary } from "@/lib/portal-ui";

interface ProfileCollectionsProps {
  username: string;
  self: boolean;
  collections: Read<PublicCollectionCard[]>;
}

/// 主页「收藏夹」标签的内容（简报 §3.15）：公开收藏夹的卡片网格（桌面 2 列、移动 1 列），最近更新的在前，
/// 一次全部出。本人视角上方说明「只显示公开的」；失败时两种视角一样是错误块
export function ProfileCollections({ username, self, collections }: ProfileCollectionsProps) {
  if (collections.kind !== "ok") {
    return (
      <InteractionUnavailable title="收藏夹暂时打不开" retryHref={`/u/${username}/collections`} />
    );
  }
  const cards = collections.data;
  if (cards.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-center">
        <p className="font-serif text-xl text-fg-1">还没有公开的收藏夹</p>
        {self && (
          <>
            <p className="text-sm text-fg-2">在文献库里把收藏夹设为公开，它就会出现在这里。</p>
            <Link href="/library" className={btnSecondary}>
              去文献库
            </Link>
          </>
        )}
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      {self && (
        <p className="text-sm text-fg-3">
          这里只显示公开的收藏夹。
          <Link href="/library" className="ml-1 font-medium text-link hover:text-link-hover">
            管理收藏夹
          </Link>
        </p>
      )}
      <ul className="grid grid-cols-2 gap-4 max-[880px]:grid-cols-1">
        {cards.map((card) => (
          <li key={card.collectionId}>
            <Link
              href={`/collections/${card.collectionId}`}
              className="group/card flex h-full flex-col gap-2 rounded-lg border border-border-default bg-paper-50 p-5 transition-colors hover:border-ink-500"
            >
              <span className="line-clamp-2 font-serif text-lg leading-snug font-medium text-fg-1">
                <span className="link-draw group-hover/card:[background-size:100%_1px]">
                  {card.name}
                </span>
              </span>
              {card.description && (
                <span className="line-clamp-2 text-sm text-fg-2">{card.description}</span>
              )}
              <span className="mt-auto font-mono text-xs text-fg-3">
                {formatCount(card.itemCount)} 篇 · 更新于 {formatMonthDay(card.updatedAt)}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
