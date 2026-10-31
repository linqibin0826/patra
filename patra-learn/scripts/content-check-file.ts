// patra-learn/scripts/content-check-file.ts
// pnpm content:check-file <路径…>：只跑单篇规则（不查文件名与前置 / 路线 / 主题这类跨篇规则），
// 给维护技能的条目模板用。按行打印「规则id  文件  说明」，有问题退出码 1
import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { readContent, repoFileExists } from "@/lib/content/load";
import { parseContent } from "@/lib/content/parse";
import { validateEntryFiles } from "@/lib/content/validate";

const learnRoot = join(import.meta.dirname, "..");
const paths = process.argv.slice(2);
if (paths.length === 0) {
  process.stderr.write("用法：pnpm content:check-file <MDX 文件路径…>\n");
  process.exit(2);
}

// INIT_CWD 是 pnpm 启动时的目录，让相对路径按调用者所在目录解析
const cwd = process.env.INIT_CWD ?? process.cwd();
const files = paths.map((p) => ({ file: p, text: readFileSync(resolve(cwd, p), "utf8") }));
// 只解析标签表；它本身有问题时一并报出，免得所有标签都被误报成 tag-unknown
const { catalog, issues: tagIssues } = parseContent({
  ...readContent(learnRoot),
  entries: [],
  routes: [],
  topics: [],
});
const issues = [
  ...tagIssues,
  ...validateEntryFiles(files, catalog.tags, { fileExists: repoFileExists(learnRoot) }),
];

for (const i of issues) process.stdout.write(`${i.rule}\t${i.file}\t${i.message}\n`);
if (issues.length > 0) process.exitCode = 1;
else process.stdout.write("通过\n");
