/// Registry 服务错误处理 API 包。
/// 
/// 本包定义错误码目录和相关契约。错误码遵循结构化格式,确保所有 Registry 操作暴露一致的、机器可读的标识符。
/// 
/// ## 错误码格式
/// 
/// 所有错误码遵循 `REG-NNNN`:
/// 
/// - `REG` - Registry 服务前缀
///   - `NNNN` - 四位数字代码
/// 
/// ## 系列定义
/// 
/// - `0xxx` - 通过 `HttpStdErrors` 生成的通用 HTTP 对齐错误
///   - `1xxx` - 目录中维护的领域或业务特定错误
/// 
/// ## 追加式维护
/// 
/// - 可根据需要添加新代码
///   - 现有代码不得删除或修改
///   - 这保证了 API 稳定性和向后兼容性
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.registry.api.error;
