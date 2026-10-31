// patra-learn/src/app/tags/page.tsx
// 顶栏「标签」进来时落到标签表里的第一个标签
import { permanentRedirect } from "next/navigation";
import { getCatalog } from "@/lib/content/catalog";
import { PERSONAL_TAG } from "@/lib/site/views";

export default function TagsPage() {
  const [first] = getCatalog().tags;
  permanentRedirect(`/tags/${first?.slug ?? PERSONAL_TAG}`);
}
