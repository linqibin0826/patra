/// realtime 子域的基础设施层。
///
/// 实时推送：SSE 连接表，提醒写入的事务提交后推三个未读数，心跳与连接时长上限（PAP-145）。
///
/// 其他子域只能用它的公开包：`domain.realtime.port.read`、`domain.realtime.model.read`、`domain.realtime.event`。
package dev.patra.notification.infra.realtime;
