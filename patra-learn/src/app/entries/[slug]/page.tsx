// patra-learn/src/app/entries/[slug]/page.tsx
// 条目页：最小实现，PAP-162 按画板重做。构建时为每篇条目预渲染（用 @mdx-js/mdx 编译正文，
// 与内容校验同一套 remark 解析），运行时不读 content/
import { evaluate } from "@mdx-js/mdx";
import { notFound } from "next/navigation";
import * as runtime from "react/jsx-runtime";
import remarkGfm from "remark-gfm";
import { getCatalog } from "@/lib/content/catalog";
import { findEntry } from "@/lib/content/indexes";
import { mdxComponents } from "@/mdx-components";
import { Diagnose, type DiagnoseProps } from "@/teaching/diagnose";

export const dynamicParams = false;

export function generateStaticParams() {
  return getCatalog().entries.map(({ slug }) => ({ slug }));
}

/// 编译条目正文；失败时带上 slug，方便在构建日志里定位
async function compileBody(slug: string, body: string) {
  try {
    return (await evaluate(body, { ...runtime, remarkPlugins: [remarkGfm] })).default;
  } catch (error) {
    throw new Error(`条目 ${slug} 的 MDX 编译失败：${(error as Error).message}`);
  }
}

export default async function EntryPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const catalog = getCatalog();
  const entry = findEntry(catalog, slug);
  if (!entry) notFound();
  const prerequisites = entry.meta.prerequisites
    .map((s) => findEntry(catalog, s))
    .filter((e) => e !== undefined)
    .map((e) => ({ slug: e.slug, title: e.meta.title }));
  const Body = await compileBody(slug, entry.body);
  const components = {
    ...mdxComponents,
    Diagnose: (props: DiagnoseProps) => <Diagnose {...props} prerequisites={prerequisites} />,
  };
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-6 py-10">
      <h1 className="text-3xl font-black">{entry.meta.title}</h1>
      <Body components={components} />
    </main>
  );
}
