import Link from "next/link";
import { unstable_rethrow } from "next/navigation";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { logRequestError } from "@/lib/auth/gateway-response";
import type { PostJournal } from "@/lib/interaction/experience-schema";
import { fetchVenueDetail } from "@/lib/portal-api/venues";
import type { VenueDetail } from "@/types/portal";

/// 期刊的 ISSN 与 OA：catalog 取不到（出错、期刊已失效）时为 null，卡片只显示登记时的刊名快照。页面先取好再交给卡片
export async function readPostVenue(journal: PostJournal): Promise<VenueDetail | null> {
  if (journal.state !== "ACTIVE" || !journal.key) {
    return null;
  }
  try {
    return await fetchVenueDetail(journal.key);
  } catch (error) {
    // 静态渲染时 Next 用抛错切到动态渲染，这个信号必须原样抛出去
    unstable_rethrow(error);
    logRequestError(`/patra-catalog/portal/venues/${journal.key}`, error);
    return null;
  }
}

/// 经验页侧栏的期刊小卡（简报 §3.16）：刊名、缩写与 ISSN、OA 标记，`期刊详情 →` 与
/// `这本期刊的其他投稿经验 →`。期刊已失效时只有刊名，没有链接
export function PostJournalCard({
  journal,
  venue,
}: {
  journal: PostJournal;
  venue: VenueDetail | null;
}) {
  const active = journal.state === "ACTIVE" && journal.key !== null;
  const title = venue?.title ?? journal.title;
  const abbreviation = venue?.abbreviatedTitle ?? journal.abbreviation;
  const meta = [abbreviation, venue?.issnL ? `ISSN ${venue.issnL}` : null].filter(Boolean);
  return (
    <div className="flex flex-col gap-2.5 rounded-lg border border-border-default bg-paper-50 p-5">
      <SectionEyebrow className="mb-0">这本期刊</SectionEyebrow>
      {active ? (
        <Link
          href={`/journals/${journal.key}`}
          className="font-medium font-serif text-fg-1 text-xl leading-tight"
        >
          <span className="link-draw">{title}</span>
        </Link>
      ) : (
        <span className="font-medium font-serif text-fg-1 text-xl leading-tight">{title}</span>
      )}
      {venue && meta.length > 0 && (
        <span className="font-mono text-fg-3 text-xs">{meta.join(" · ")}</span>
      )}
      {venue?.isOpenAccess && (
        <span className="inline-flex h-5.5 items-center self-start rounded-full bg-moss-50 px-2 font-medium text-moss-700 text-xs">
          开放获取
        </span>
      )}
      {active && (
        <div className="mt-1.5 flex flex-col gap-1.5 border-border-subtle border-t pt-3 text-sm">
          <Link
            href={`/journals/${journal.key}`}
            className="font-medium text-link hover:text-link-hover"
          >
            期刊详情 →
          </Link>
          <Link
            href={`/journals/${journal.key}#experiences`}
            className="font-medium text-link hover:text-link-hover"
          >
            这本期刊的其他投稿经验 →
          </Link>
        </div>
      )}
    </div>
  );
}
