import { z } from "zod";
import type {
  FullText,
  FullTextBlock,
  FullTextInline,
  FullTextSectionNode,
  FullTextTableCell,
} from "@/types/fulltext";

/// 正文只认白名单里的块与行内节点（release spec 决策 C）：出现别的 kind 整篇按加载失败处理，
/// 不会把认不出的东西原样画到页面上。

const inlineSchema: z.ZodType<FullTextInline> = z.lazy(() =>
  z.discriminatedUnion("kind", [
    z.object({ kind: z.literal("text"), text: z.string() }),
    z.object({
      kind: z.literal("style"),
      style: z.enum(["italic", "bold", "sup", "sub", "underline", "smallcaps", "monospace"]),
      children: z.array(inlineSchema),
    }),
    z.object({
      kind: z.literal("xref"),
      refType: z.enum(["bibr", "fig", "table", "disp-formula", "sec", "fn", "boxed-text"]),
      targets: z.array(z.string()),
      children: z.array(inlineSchema),
    }),
    z.object({ kind: z.literal("link"), href: z.string(), children: z.array(inlineSchema) }),
    z.object({ kind: z.literal("math"), mathml: z.string() }),
    z.object({ kind: z.literal("break") }),
  ]),
);

const cellSchema: z.ZodType<FullTextTableCell> = z.object({
  content: z.array(inlineSchema),
  colspan: z.number().int(),
  rowspan: z.number().int(),
  header: z.boolean(),
});

const nullableString = z.string().nullable();
const nullableInt = z.number().int().nullable();

const blockSchema: z.ZodType<FullTextBlock> = z.lazy(() =>
  z.discriminatedUnion("kind", [
    z.object({ kind: z.literal("paragraph"), content: z.array(inlineSchema) }),
    z.object({
      kind: z.literal("figure"),
      id: nullableString,
      label: nullableString,
      caption: z.array(blockSchema),
      image: nullableString,
      width: nullableInt,
      height: nullableInt,
    }),
    z.object({
      kind: z.literal("table"),
      id: nullableString,
      label: nullableString,
      caption: z.array(blockSchema),
      head: z.array(z.array(cellSchema)),
      body: z.array(z.array(cellSchema)),
      footnotes: z.array(blockSchema),
    }),
    z.object({
      kind: z.literal("formula"),
      id: nullableString,
      label: nullableString,
      mathml: nullableString,
      image: nullableString,
      width: nullableInt,
      height: nullableInt,
    }),
    z.object({
      kind: z.literal("list"),
      ordered: z.boolean(),
      items: z.array(z.array(blockSchema)),
    }),
    z.object({ kind: z.literal("quote"), blocks: z.array(blockSchema) }),
    z.object({
      kind: z.literal("box"),
      id: nullableString,
      title: z.array(inlineSchema),
      blocks: z.array(blockSchema),
    }),
    z.object({ kind: z.literal("code"), text: z.string() }),
  ]),
);

const sectionSchema: z.ZodType<FullTextSectionNode> = z.lazy(() =>
  z.object({
    id: z.string(),
    title: z.array(inlineSchema),
    sections: z.array(sectionSchema),
    blocks: z.array(blockSchema),
  }),
);

/** 全文接口响应的运行时校验。 */
export const fullTextSchema: z.ZodType<FullText> = z.object({
  publicationId: z.string(),
  pmcid: z.string(),
  pmcUrl: z.string(),
  license: z.object({ code: z.string(), url: nullableString }).nullable(),
  source: z.string(),
  fetchedAt: nullableString,
  wordCount: nullableInt,
  estimatedReadMin: nullableInt,
  isManuscript: z.boolean().nullable(),
  body: z
    .object({
      schemaVersion: z.number().int(),
      blocks: z.array(blockSchema),
      sections: z.array(sectionSchema),
      back: z.array(sectionSchema),
      references: z.array(
        z.object({
          id: nullableString,
          label: nullableString,
          content: z.array(inlineSchema),
          pmid: nullableString,
          doi: nullableString,
        }),
      ),
      wordCount: z.number().int(),
    })
    .nullable(),
});
