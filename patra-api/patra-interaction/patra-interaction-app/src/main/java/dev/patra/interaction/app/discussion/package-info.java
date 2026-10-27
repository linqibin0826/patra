/// discussion 子域的应用层。
///
/// 文献与投稿经验下的讨论：评论与回复（树存、两层展示）、单楼视图与对话链、Markdown 正文与 @ 提及、修改历史与删除
/// 占位、@ 联想；评论写入时在同一事务里发布进程内事件。
///
/// 别的子域只能用它的 `domain.discussion.port.read`、`domain.discussion.model.read`、
/// `domain.discussion.event`；提醒子域只经事件契约 `domain.discussion.event` 订阅。
package dev.patra.interaction.app.discussion;
