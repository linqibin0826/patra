import Link from "next/link";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import type { PostJournal } from "@/lib/interaction/experience-schema";

/// 经验已删除（简报 §3.16 `post.deleted`）：眉题、期刊名、「该经验已删除」与说明；没有回复区
export function PostDeleted({ journal }: { journal: PostJournal }) {
  const active = journal.state === "ACTIVE" && journal.key !== null;
  return (
    <div className="flex flex-col gap-3.5">
      <SectionEyebrow className="mb-0">投稿经验 · Submission experience</SectionEyebrow>
      <p className="m-0 text-balance font-medium font-serif text-display-3 text-fg-1 leading-heading tracking-tight">
        {journal.title}
      </p>
      <div className="mt-4 flex flex-col gap-3 border-border-default border-t pt-8 pb-2">
        <h1 className="m-0 font-medium font-serif text-2xl text-fg-1">该经验已删除</h1>
        <p className="m-0 max-w-[40em] text-fg-2 text-md leading-relaxed">
          作者删除了这条投稿经验，它已经从期刊页的列表和统计中去掉，下面的回复也不再显示。
        </p>
        {active && (
          <Link
            href={`/journals/${journal.key}#experiences`}
            className="font-medium text-link hover:text-link-hover"
          >
            看这本期刊的其他投稿经验 →
          </Link>
        )}
      </div>
    </div>
  );
}
