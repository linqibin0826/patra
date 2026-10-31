/// inbox 子域的应用层。
///
/// 站内信箱：消费 interaction 的事件写提醒，收件人规则、去重与乱序防护，未读数、列表、已读、删除联动、
/// 5 个月保留；有帮助按「内容 × 北京时间一天」合并也是信箱里的一种提醒（PAP-143、PAP-144）。
///
/// 其他子域只能用它的公开包：`domain.inbox.port.read`、`domain.inbox.model.read`、`domain.inbox.event`。
package dev.patra.notification.app.inbox;
