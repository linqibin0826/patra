/// realtime 子域的适配器层。
///
/// 实时推送：SSE 连接表，提醒写入的事务提交后推三个未读数，心跳与连接时长上限（PAP-145）；收其他实例发来的
/// 未读数广播，推给本机连接（#406）。
///
/// 其他子域只能用它的公开包：`domain.realtime.port.read`、`domain.realtime.model.read`、`domain.realtime.event`。
package dev.patra.notification.adapter.realtime;
