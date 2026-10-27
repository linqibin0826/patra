/// notification 子域的领域层。
///
/// 站内提醒（PAP-123）：订阅讨论的事件按规则生成「回复我的」「@ 我的」两类提醒；摘录的存储写法与渲染；5 个月保留期。
///
/// 只依赖底座公开包与讨论的事件契约（`domain.discussion.event`），不依赖投稿经验：经验的所有者、匿名与快照都从登记表读。
package dev.patra.interaction.domain.notification;
