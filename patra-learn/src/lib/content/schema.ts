// patra-learn/src/lib/content/schema.ts
// frontmatter 与 YAML 文件的 schema；全部 strict，拼错字段名直接报错
import { z } from "zod";
import { DIFFICULTIES, ENTRY_TYPES } from "./types";

export const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
export const VERSION_PATTERN = /^v\d+\.\d+$/;

const slug = z.string().regex(SLUG_PATTERN, "必须是小写字母、数字与连字符组成的 slug");
const slugList = z.array(slug).refine((list) => new Set(list).size === list.length, "不能重复");

const sourceSchema = z.strictObject({
  version: z.string().regex(VERSION_PATTERN, "版本号写成 v0.16 这种格式"),
  issue: z
    .string()
    .regex(/^PAP-\d+$/, "Issue 写成 PAP-140 这种格式")
    .optional(),
  pr: z.number().int().positive().optional(),
});

export const entryMetaSchema = z.strictObject({
  title: z.string().min(1),
  summary: z.string().min(1),
  type: z.enum(ENTRY_TYPES),
  difficulty: z.enum(DIFFICULTIES),
  tags: slugList.min(1, "至少一个标签"),
  prerequisites: slugList,
  topics: slugList.default([]),
  sources: z.array(sourceSchema).default([]),
  personalEnv: z.boolean().default(false),
  supersededBy: slug.optional(),
});

export const routeSchema = z.strictObject({
  title: z.string().min(1),
  summary: z.string().min(1),
  order: z.number().int().positive(),
  entries: slugList.min(1, "至少一篇"),
});

export const topicSchema = z.strictObject({
  title: z.string().min(1),
  summary: z.string().min(1),
});

export const tagsSchema = z.array(z.strictObject({ slug, label: z.string().min(1) }));
