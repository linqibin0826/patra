/// discussion 子域的基础设施层。
///
/// 文献与投稿经验下的讨论：评论与回复（树存、两层展示）、单楼视图与对话链、Markdown 正文与 @ 提及、修改历史与删除
/// 占位、@ 联想；评论写入时在同一事务里发布进程内事件。
///
/// 别的子域只能用它的 `domain.discussion.port.read`、`domain.discussion.model.read`、
/// `domain.discussion.event`。评论的发表、编辑、删除另外外送给通知服务（PAP-141），站内提醒由它生成（PAP-143）。
package dev.patra.interaction.infra.discussion;
