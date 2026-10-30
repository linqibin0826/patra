import { Eye } from "lucide-react";
import Link from "next/link";
import { CopyCollectionButton } from "@/components/library/CopyCollectionButton";
import { InteractionUnavailable } from "@/components/library/InteractionUnavailable";
import { LibraryStatusProvider } from "@/components/library/LibraryStatusProvider";
import { LinkPagination } from "@/components/library/LinkPagination";
import { PaperStats } from "@/components/library/PaperStats";
import { PublicCollectionActions } from "@/components/library/PublicCollectionActions";
import { SaveButton } from "@/components/library/SaveButton";
import { SortSelect } from "@/components/library/SortSelect";
import { UnavailableEntry } from "@/components/library/UnavailableEntry";
import { PaperListItem } from "@/components/portal/papers/PaperListItem";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { Avatar } from "@/components/user/Avatar";
import { authPageHref } from "@/lib/auth/next-path";
import type { CurrentUser } from "@/lib/auth/types";
import { formatDate } from "@/lib/format-time";
import type { PublicEntry } from "@/lib/interaction/collections";
import type { Read } from "@/lib/interaction/read";
import type { Page, PublicCollection } from "@/lib/interaction/schema";
import type { LibrarySort } from "@/lib/library/params";
import { formatCount } from "@/lib/library/scope";
import { btnPrimary } from "@/lib/portal-ui";

interface PublicCollectionViewProps {
  collection: PublicCollection;
  items: Read<Page<PublicEntry>>;
  /// 当前访客；匿名为 null
  viewer: CurrentUser | null;
  sort: LibrarySort;
  page: number;
  /// 规范地址
  link: string;
}

/// 公开收藏夹页（简报 §3.14）：给别人看的阅读页。头部（眉题、名称、简介、所有者行、操作）→ 排序 → 条目
/// （访客自己的收藏按钮，不显示所有者的备注）→ 分页。管理动作只在文献库里
export function PublicCollectionView({
  collection,
  items,
  viewer,
  sort,
  page,
  link,
}: PublicCollectionViewProps) {
  const path = `/collections/${collection.collectionId}`;
  const owner = ownerOf(collection, viewer);
  return (
    <div className="flex flex-col">
      {collection.viewerIsOwner && (
        <div className="bg-slate-50">
          <p className="mx-auto flex max-w-page flex-wrap items-center gap-x-3 gap-y-1 px-gutter py-3 text-sm text-fg-1">
            <Eye className="size-4 text-fg-2" aria-hidden="true" />
            {collection.visibility === "PUBLIC"
              ? "这是你的收藏夹，别人看到的就是这样。"
              : "这个收藏夹是私有的，只有你能看到。"}
            <Link
              href={`/library/${collection.collectionId}`}
              className="font-medium text-link hover:text-link-hover"
            >
              在文献库中管理
            </Link>
          </p>
        </div>
      )}
      <div className="mx-auto w-full max-w-page px-gutter pt-12 pb-24 max-[880px]:pt-7">
        <header className="flex max-w-[72ch] flex-col gap-4 border-b border-border-default pb-8">
          <SectionEyebrow className="mb-0">收藏夹 · COLLECTION</SectionEyebrow>
          <h1
            title={collection.name}
            className="line-clamp-2 font-serif text-display-2 leading-heading font-medium tracking-tight break-words text-fg-1"
          >
            {collection.name}
          </h1>
          {collection.description && (
            <p className="font-serif text-lg leading-relaxed whitespace-pre-line text-fg-2">
              {collection.description}
            </p>
          )}
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-fg-3">
            {owner.kind === "deleted" ? (
              <>
                <span aria-hidden="true" className="inline-block size-6 rounded-full bg-ink-200" />
                <span>已注销用户</span>
              </>
            ) : (
              owner.kind === "user" && (
                <>
                  <Avatar identity={owner} size={24} />
                  <Link
                    href={`/u/${owner.username}/collections`}
                    className="font-medium text-fg-1 hover:text-link-hover"
                  >
                    {owner.displayName}
                  </Link>
                  <span className="font-mono text-xs">@{owner.username}</span>
                </>
              )
            )}
            <span>
              {owner.kind !== "none" && "· "}
              {formatCount(collection.itemCount)} 篇 · 更新于 {formatDate(collection.updatedAt)}
            </span>
          </p>
          <div className="mt-2 flex flex-wrap gap-3 max-[880px]:[&>*:first-child]:basis-full">
            {collection.viewerIsOwner ? (
              <Link href={`/library/${collection.collectionId}`} className={btnPrimary}>
                在文献库中管理
              </Link>
            ) : viewer ? (
              <CopyCollectionButton collectionId={collection.collectionId} />
            ) : (
              <Link href={authPageHref("/login", path)} className={btnPrimary}>
                登录后复制
              </Link>
            )}
            <PublicCollectionActions
              collectionId={collection.collectionId}
              name={collection.name}
              ownerName={owner.kind === "user" ? owner.displayName : null}
              count={collection.itemCount}
              sort={sort}
              link={link}
            />
          </div>
        </header>
        <Entries items={items} sort={sort} page={page} path={path} />
      </div>
    </div>
  );
}

type OwnerLine =
  | {
      kind: "user";
      username: string;
      displayName: string;
      avatarUrl: string | null;
      avatarPalette: number;
    }
  | { kind: "deleted" }
  | { kind: "none" };

/// 所有者行画谁：接口给了用接口的；本人视角、identity 不可用时 `owner` 为 null，用当前用户画
function ownerOf(collection: PublicCollection, viewer: CurrentUser | null): OwnerLine {
  const { owner } = collection;
  if (owner?.deleted) {
    return { kind: "deleted" };
  }
  if (owner?.username && owner.displayName) {
    return {
      kind: "user",
      username: owner.username,
      displayName: owner.displayName,
      avatarUrl: owner.avatarUrl,
      avatarPalette: owner.avatarPalette,
    };
  }
  if (owner === null && collection.viewerIsOwner && viewer) {
    return { kind: "user", ...viewer };
  }
  return { kind: "none" };
}

function Entries({
  items,
  sort,
  page,
  path,
}: {
  items: Read<Page<PublicEntry>>;
  sort: LibrarySort;
  page: number;
  path: string;
}) {
  if (items.kind !== "ok") {
    return <InteractionUnavailable title="收藏夹暂时打不开" retryHref={path} />;
  }
  const { data } = items;
  if (data.total === 0) {
    return <p className="py-16 text-center text-sm text-fg-3">这个收藏夹还没有文献。</p>;
  }
  const keys = data.items.flatMap((entry) => (entry.paper ? [entry.paper.id] : []));
  return (
    <div className="flex max-w-[72ch] flex-col gap-6 pt-5">
      <div className="flex items-center justify-between gap-4 border-b border-border-default pb-3">
        <SortSelect sort={sort} />
        <span className="font-mono text-xs tabular-nums text-fg-3">
          {formatCount(data.total)} 篇
        </span>
      </div>
      <LibraryStatusProvider keys={keys}>
        <ul aria-label="收藏夹条目" className="flex flex-col">
          {data.items.map((entry) => (
            <li key={entry.item.itemId}>
              {entry.paper ? (
                <PaperListItem
                  paper={entry.paper}
                  detailedDate
                  stats={
                    <PaperStats paperKey={entry.paper.id} stats={entry.stats} className="mt-1" />
                  }
                  action={
                    <SaveButton
                      paperKey={entry.paper.id}
                      title={entry.paper.title}
                      variant="icon"
                    />
                  }
                />
              ) : (
                <UnavailableEntry title={entry.item.title} />
              )}
            </li>
          ))}
        </ul>
      </LibraryStatusProvider>
      <LinkPagination sort={sort} page={page} total={data.total} pageSize={data.pageSize} />
    </div>
  );
}
