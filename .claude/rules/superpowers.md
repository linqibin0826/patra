# Superpowers 约定

流程方法论使用上游 `superpowers:` plugin（obra/superpowers），以下 Patra 约定覆盖其默认值：

1. **spec / plan 位置**：spec 写到 `docs/patra/specs/YYYY-MM-DD-<topic>-design.md`，plan 写到 `docs/patra/plans/YYYY-MM-DD-<feature>.md`，均为 Markdown（不使用上游默认的 `docs/superpowers/`）。`docs/patra/` 下的历史 HTML spec / plan 只读保留，不迁移。
2. **分支**：brainstorming 写 spec 前先切到本版本对应的 feature branch（按版本 × 技术栈）；spec / plan 在 feature branch 上提交，随同版本第一个代码 PR 合入，不提交到 main。
3. **测试套件**：TDD / verification 所说的"项目测试套件"——后端按 `.claude/rules/testing/conventions.md`（命名、source set、验证门控，PR 前跑 `./gradlew check`），前端在 `patra-portal/` 跑其 `package.json` 中的测试脚本。
4. **收尾**：finishing-a-development-branch 的合并 / PR 选项服从根 CLAUDE.md「PR 与代码评审」一节（draft PR、AI reviewer 节奏、Monitor）。
5. **工作树**：单会话开发直接在主检出上切到本版本的 feature branch 进行，不建 worktree；`using-git-worktrees` / `executing-plans` 要求的"隔离工作区"以此为准，只有需要同时推进多件事时才建。主检出由 IDEA 打开，构建已委托给 Gradle 并关闭了构建脚本自动同步，改了 `*.gradle.kts` 后提醒用户在 IDEA 里点「加载 Gradle 更改」。
