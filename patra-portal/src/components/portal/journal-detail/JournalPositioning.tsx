import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import type { VenueDetail } from "@/types/portal";

export function JournalPositioning({
  venue,
  subjects,
}: {
  venue: VenueDetail;
  subjects: string[];
}) {
  const tags: { k: string; v: string }[] = [];
  for (const s of subjects) {
    tags.push({ k: "学科", v: s });
  }
  if (venue.frequency) {
    tags.push({ k: "出版", v: venue.frequency });
  }
  if (venue.medlineIndexed != null) {
    tags.push({ k: "索引", v: venue.medlineIndexed ? "MEDLINE 收录" : "未被 MEDLINE 收录" });
  }
  tags.push({
    k: "获取",
    v: venue.isOpenAccess ? `开放获取${venue.oaType ? ` · ${venue.oaType}` : ""}` : "订阅 / 混合",
  });

  return (
    <section className="rounded-lg border border-border-default bg-paper-50 px-5 py-[18px]">
      <SectionEyebrow>定位与范围</SectionEyebrow>
      <p className="mb-3 font-sans text-sm leading-normal text-fg-3">
        由结构化事实组合 —— 该刊暂无编辑撰写的简介文本。
      </p>
      <div className="flex flex-wrap gap-2">
        {tags.map((t) => (
          <span
            key={`${t.k}-${t.v}`}
            className="inline-flex items-center gap-1.5 rounded-sm border border-border-default bg-paper-100 px-2.5 py-1 font-sans text-sm font-medium leading-normal text-fg-2"
          >
            <span className="mr-px border-r border-border-default pr-1.5 font-mono text-[9.5px] uppercase tracking-[0.05em] text-fg-4">
              {t.k}
            </span>
            {t.v}
          </span>
        ))}
      </div>
    </section>
  );
}
