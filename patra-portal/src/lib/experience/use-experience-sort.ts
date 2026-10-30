import { useSearchParams } from "next/navigation";
import { parseExperienceSort } from "@/lib/experience/sort";
import type { ExperienceSort } from "@/lib/interaction/experience-schema";
import { writeQuery } from "@/lib/write-query";

/// 期刊页经验列表的排序以地址为准（简报 v0.15 §2）：切换替换当前历史、不触发服务端渲染，`#experiences` 保留；
/// 最新投稿是默认，不写进地址
export function useExperienceSort(): {
  sort: ExperienceSort;
  setSort: (sort: ExperienceSort) => void;
} {
  const sort = parseExperienceSort(useSearchParams().get("sort"));
  return {
    sort,
    setSort: (next) =>
      writeQuery(
        (query) => (next === "latest" ? query.delete("sort") : query.set("sort", next)),
        "replace",
      ),
  };
}
