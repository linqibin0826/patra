/// MyBatis 数据层异常映射贡献器包。
///
/// 本包提供 {@link com.patra.starter.core.error.spi.ErrorMappingContributor} 实现,负责将 MyBatis-Plus 和底层
/// JDBC 驱动程序抛出的异常转换为标准化的平台错误码,确保 API 响应的一致性和可读性。
///
/// ## 职责
///
/// - 捕获数据访问层异常(MyBatis-Plus、JDBC SQLException)
///   - 将异常映射为标准 HTTP 错误码(如 409 Conflict、503 Service Unavailable)
///   - 识别特定数据库的错误码(如 MySQL 1062 重复键冲突)
///   - 通过 SPI 机制集成到全局异常处理流程
///
/// ## 核心组件
///
/// - {@link com.patra.starter.mybatis.error.contributor.DataLayerErrorMappingContributor} -
///       数据层异常映射贡献器
///
/// ## 设计决策
///
/// - **SPI 扩展机制:** 实现 `ErrorMappingContributor` 接口,通过服务发现机制自动注册
///   - **多层异常处理:** 优先处理 MyBatis-Plus 异常,然后处理 JDBC SQLException
///   - **供应商特定映射:** 识别 MySQL、PostgreSQL 等数据库的特定错误码
///   - **SQLState 标准映射:** 基于 SQL 标准 SQLState 代码进行通用映射(如 '08' 连接异常)
///
/// ## 异常映射规则
///
/// <table border="1">
///   <tr><th>异常类型</th><th>错误码</th><th>HTTP 状态</th><th>说明</th></tr>
///   <tr><td>MybatisPlusException</td><td>INTERNAL_ERROR</td><td>500</td><td>通用 MyBatis-Plus
// 配置错误</td></tr>
///
// <tr><td>SQLIntegrityConstraintViolationException</td><td>CONFLICT</td><td>409</td><td>唯一键冲突、外键约束违反</td></tr>
///   <tr><td>SQLException (MySQL 1062)</td><td>CONFLICT</td><td>409</td><td>MySQL 重复键冲突</td></tr>
///   <tr><td>SQLException (MySQL 1451/1452)</td><td>CONFLICT</td><td>409</td><td>MySQL
// 外键约束错误</td></tr>
///   <tr><td>SQLException (SQLState '08')</td><td>UNAVAILABLE</td><td>503</td><td>数据库连接异常</td></tr>
///   <tr><td>SQLException (SQLState 'HY')</td><td>UNAVAILABLE</td><td>503</td><td>数据库超时</td></tr>
///   <tr><td>SQLException (其他)</td><td>INTERNAL_ERROR</td><td>500</td><td>未分类的 SQL 错误</td></tr>
/// </table>
///
/// ## MySQL 错误码映射
///
/// - **1062 (ER_DUP_ENTRY):** 唯一键冲突 → `CONFLICT`
///   - **1451 (ER_ROW_IS_REFERENCED_2):** 无法删除/更新父行(外键约束) → `CONFLICT`
///   - **1452 (ER_NO_REFERENCED_ROW_2):** 无法添加/更新子行(外键约束) → `CONFLICT`
///
/// ## SQLState 标准映射
///
/// - **'08xxx':** 连接异常(Connection Exception) → `UNAVAILABLE`
///   - **'HYxxx':** 超时异常(Timeout) → `UNAVAILABLE`
///
/// ## 使用示例
///
/// **场景 1: 唯一键冲突**
///
/// ```java
/// // 数据库表定义: UNIQUE KEY `uk_name` (name)
/// ProvenanceDO provenance = ProvenanceDO.builder()
///     .name("PubMed") // 已存在的名称
///     .build();
///
/// try {
///     mapper.insert(provenance); catch (Exception ex) {
///     // DataLayerErrorMappingContributor 将 SQLException 映射为:
///     // ErrorCode: REG-0409 (CONFLICT)
///     // HTTP 状态: 409 Conflict
///     // 消息: "资源冲突,名称 'PubMed' 已存在"
/// ```
///
/// **场景 2: 数据库连接失败**
///
/// ```java
/// // 数据库服务器宕机或网络故障
/// try {
///     mapper.selectById(1L); catch (Exception ex) {
///     // DataLayerErrorMappingContributor 将 SQLException (SQLState '08') 映射为:
///     // ErrorCode: REG-0503 (UNAVAILABLE)
///     // HTTP 状态: 503 Service Unavailable
///     // 消息: "数据库服务不可用,请稍后重试"
/// ```
///
/// **场景 3: 外键约束违反**
///
/// ```java
/// // 尝试删除被引用的父记录
/// try {
///     mapper.deleteById(1L); // 该记录有子记录引用 catch (Exception ex) {
///     // DataLayerErrorMappingContributor 将 SQLException (MySQL 1451) 映射为:
///     // ErrorCode: REG-0409 (CONFLICT)
///     // HTTP 状态: 409 Conflict
///     // 消息: "无法删除记录,存在关联数据"
/// ```
///
/// ## 集成方式
///
/// **自动注册(推荐):**
///
/// ```java
/// // 通过 @Component 注解自动注册到 Spring 容器
/// @Component
/// public class DataLayerErrorMappingContributor implements ErrorMappingContributor {
///     // ...
///
/// // 全局异常处理器自动调用所有贡献器
/// @RestControllerAdvice
/// public class GlobalExceptionHandler {
///     @Autowired
///     private List<ErrorMappingContributor> contributors;
///
///     @ExceptionHandler(Exception.class)
///     public ResponseEntity<ProblemDetail> handleException(Exception ex) {
///         for (ErrorMappingContributor contributor : contributors) {
///             Optional<ErrorCodeLike> errorCode = contributor.mapException(ex);
///             if (errorCode.isPresent()) {
///                 return createResponse(errorCode.get());
///         return defaultErrorResponse(ex);
/// ```
///
/// ## 扩展指南
///
/// **添加新的数据库供应商支持:**
///
/// ```java
/// private Optional<ErrorCodeLike> mapPostgresqlErrors(SQLException sqlEx) {
///     int errorCode = sqlEx.getErrorCode();
///     switch (errorCode) {
///         case 23505: // PostgreSQL unique_violation
///             return Optional.of(http.CONFLICT());
///         case 23503: // PostgreSQL foreign_key_violation
///             return Optional.of(http.CONFLICT());
///         default:
///             return Optional.empty();
/// ```
///
/// ## 注意事项
///
/// - **异常链处理:** 贡献器会检查异常链,确保捕获嵌套的 SQLException
///   - **日志级别:** CONFLICT 错误使用 DEBUG 级别,UNAVAILABLE 使用 WARN 级别
///   - **返回 Optional.empty():** 如果贡献器无法处理异常,必须返回空 Optional,让其他贡献器尝试处理
///
/// ## 相关模块
///
/// - {@link com.patra.common.error.codes.HttpStdErrors} - 标准 HTTP 错误码定义
///   - {@link com.patra.starter.core.error.spi.ErrorMappingContributor} - 错误映射 SPI 接口
///   - {@link com.patra.starter.mybatis.autoconfig.PatraMybatisAutoConfiguration} - 自动配置贡献器 Bean
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.mybatis.error.contributor;
