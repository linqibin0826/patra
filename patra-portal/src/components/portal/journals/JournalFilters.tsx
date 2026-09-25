"use client";

import { useState } from "react";
import { useBrowseQuery } from "@/components/portal/browse/BrowseQueryProvider";
import { FacetCheckRow } from "@/components/portal/browse/FacetCheckRow";
import { FacetGroup } from "@/components/portal/browse/FacetGroup";
import { FacetToggleRow } from "@/components/portal/browse/FacetToggleRow";
import { FilterPanel } from "@/components/portal/browse/FilterPanel";
import { Input } from "@/components/ui/input";
import { CAS_ZONE_ORDER, JCR_QUARTILE_ORDER } from "@/lib/portal-api/venue-browse";
import { useBrowseFilterUiStore } from "@/store/browse-filter-ui";
import type { VenueBrowseFacets, VenueBrowseQuery } from "@/types/portal";

interface Props {
  facets: VenueBrowseFacets;
  resultTotal?: number;
}

// ---- 内部 helper ----

function toggleArrValue(
  query: VenueBrowseQuery,
  key: "subject" | "jcr" | "cas" | "country",
  value: string,
): VenueBrowseQuery {
  const cur = query[key];
  const next = cur.includes(value) ? cur.filter((v) => v !== value) : [...cur, value];
  return { ...query, [key]: next, page: 1 };
}

function toggleBoolValue(query: VenueBrowseQuery, key: "casTop" | "oa" | "doaj"): VenueBrowseQuery {
  return { ...query, [key]: !query[key], page: 1 };
}

interface SearchableCheckListProps {
  options: { value: string; count: number }[];
  selected: string[];
  searchPlaceholder: string;
  onToggle: (value: string) => void;
}

// 可搜索 + 可滚动的复选列表（学科、国家等长列表 facet 共用，期刊页专属）
function SearchableCheckList({
  options,
  selected,
  searchPlaceholder,
  onToggle,
}: SearchableCheckListProps) {
  const [search, setSearch] = useState("");
  const filtered = search
    ? options.filter((opt) => opt.value.toLowerCase().includes(search.toLowerCase()))
    : options;
  return (
    <>
      <div className="mb-1.5 pr-1">
        <Input
          placeholder={searchPlaceholder}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="h-7 text-xs"
        />
      </div>
      <div className="max-h-48 overflow-y-auto">
        {filtered.map((opt) => (
          <FacetCheckRow
            key={opt.value}
            label={opt.value}
            count={opt.count}
            checked={selected.includes(opt.value)}
            onToggle={() => onToggle(opt.value)}
          />
        ))}
      </div>
    </>
  );
}

// ---- 主筛选内容（rail 与 sheet 共用） ----

function FilterControls({ facets }: { facets: VenueBrowseFacets }) {
  const { query, navigate } = useBrowseQuery<VenueBrowseQuery>();

  // JCR / CAS 按规范顺序排列，只展示 facets 中有的项
  const jcrOptions = JCR_QUARTILE_ORDER.filter((q) => facets.jcr.some((opt) => opt.value === q));
  const casOptions = CAS_ZONE_ORDER.filter((z) => facets.cas.some((opt) => opt.value === z));

  const getCount = (options: { value: string; count: number }[], value: string) =>
    options.find((o) => o.value === value)?.count ?? 0;

  return (
    <div className="flex flex-col">
      <FacetGroup title="学科领域" selCount={query.subject.length}>
        <SearchableCheckList
          options={facets.subject}
          selected={query.subject}
          searchPlaceholder="搜索学科…"
          onToggle={(value) => navigate((cur) => toggleArrValue(cur, "subject", value))}
        />
      </FacetGroup>

      <FacetGroup title="JCR 分区" selCount={query.jcr.length}>
        {jcrOptions.map((q) => (
          <FacetCheckRow
            key={q}
            label={q}
            count={getCount(facets.jcr, q)}
            checked={query.jcr.includes(q)}
            onToggle={() => navigate((cur) => toggleArrValue(cur, "jcr", q))}
          />
        ))}
      </FacetGroup>

      <FacetGroup title="中科院分区" selCount={query.cas.length + (query.casTop ? 1 : 0)}>
        {casOptions.map((z) => (
          <FacetCheckRow
            key={z}
            label={z}
            count={getCount(facets.cas, z)}
            checked={query.cas.includes(z)}
            onToggle={() => navigate((cur) => toggleArrValue(cur, "cas", z))}
          />
        ))}
        <div className="mt-1 border-t border-border pt-1">
          <FacetToggleRow
            label="仅 Top 期刊"
            checked={query.casTop}
            count={facets.casTop}
            onToggle={() => navigate((cur) => toggleBoolValue(cur, "casTop"))}
          />
        </div>
      </FacetGroup>

      <FacetGroup title="开放获取" selCount={(query.oa ? 1 : 0) + (query.doaj ? 1 : 0)}>
        <FacetToggleRow
          label="仅开放获取"
          checked={query.oa}
          count={facets.oa}
          onToggle={() => navigate((cur) => toggleBoolValue(cur, "oa"))}
        />
        <FacetToggleRow
          label="收录于 DOAJ"
          checked={query.doaj}
          count={facets.doaj}
          onToggle={() => navigate((cur) => toggleBoolValue(cur, "doaj"))}
        />
      </FacetGroup>

      <FacetGroup title="国家 / 地区" selCount={query.country.length}>
        <SearchableCheckList
          options={facets.country}
          selected={query.country}
          searchPlaceholder="搜索国家 / 地区…"
          onToggle={(value) => navigate((cur) => toggleArrValue(cur, "country", value))}
        />
      </FacetGroup>
    </div>
  );
}

// ---- 导出组件 ----

/// 期刊浏览筛选面板：勾选状态读 Provider 的乐观 query（点击即打勾），变更经 navigate 基于最新状态叠加。
/// 桌面（md+）为侧栏；移动端为左侧 Sheet 抽屉。
export function JournalFilters({ facets, resultTotal }: Props) {
  const close = useBrowseFilterUiStore((s) => s.close);
  return (
    <FilterPanel
      sheetTitle="筛选"
      sheetFooter={
        <button
          type="button"
          onClick={close}
          className="w-full rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
        >
          {resultTotal != null ? `查看 ${resultTotal} 本结果` : "查看结果"}
        </button>
      }
    >
      <FilterControls facets={facets} />
    </FilterPanel>
  );
}
