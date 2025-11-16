/**
 * Storage 应用层"记录上传"用例包。
 *
 * <p>本包包含"记录文件上传元数据"用例的编排逻辑,作为六边形架构应用层的一部分。 应用层负责协调领域对象完成业务用例,管理事务边界,并作为外部适配器和领域层之间的桥梁。
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.objectstorage.app.recordupload.RecordUploadOrchestrator} - 用例编排器,协调领域对象执行业务逻辑
 *   <li>{@link com.patra.objectstorage.app.recordupload.RecordUploadCommand} - 用例输入命令,封装编排器所需的所有参数
 *   <li>{@link com.patra.objectstorage.app.recordupload.RecordUploadResult} - 用例输出结果,包含元数据 ID 和记录时间戳
 * </ul>
 *
 * <h2>应用层职责</h2>
 *
 * <ul>
 *   <li><strong>用例编排</strong>: 协调领域对象(聚合根、值对象)完成业务操作
 *   <li><strong>事务管理</strong>: 定义事务边界,确保操作的原子性和一致性
 *   <li><strong>输入验证</strong>: 验证命令对象的有效性,确保数据完整性
 *   <li><strong>异常转换</strong>: 将领域异常转换为应用层异常(可选)
 *   <li><strong>审计日志</strong>: 记录关键业务操作的审计信息
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>薄应用层</strong>: 应用层只做编排,不包含业务逻辑(业务逻辑在领域层)
 *   <li><strong>事务边界</strong>: 使用 {@code @Transactional} 定义事务,确保数据一致性
 *   <li><strong>命令模式</strong>: 使用命令对象封装输入参数,使用结果对象封装输出
 *   <li><strong>单一职责</strong>: 每个编排器负责一个用例,职责清晰
 *   <li><strong>无状态</strong>: 编排器不持有状态,所有数据通过命令传入
 * </ul>
 *
 * <h2>用例执行流程</h2>
 *
 * <pre>
 * [适配器层] → RecordUploadCommand → [应用层编排器] → [领域层] → [仓储端口] → [基础设施层]
 *     ↓              ↓                    ↓              ↓           ↓
 *  接收 HTTP     封装参数          创建聚合根       持久化操作     数据库保存
 *  请求                          (领域逻辑)
 *     ↑              ↑                    ↑              ↑           ↑
 * [适配器层] ← RecordUploadResult ← [应用层编排器] ← [仓储端口] ← [基础设施层]
 *  返回 HTTP     返回结果             返回聚合根       返回聚合根    查询最新数据
 *  响应
 * </pre>
 *
 * <h2>RecordUploadOrchestrator - 用例编排器</h2>
 *
 * 编排"记录文件上传"用例的执行,包含以下步骤:
 *
 * <ol>
 *   <li>接收并验证 {@link com.patra.objectstorage.app.recordupload.RecordUploadCommand} 命令
 *   <li>将命令数据转换为领域对象(StorageKey、FileSize、FileChecksum、BusinessContext)
 *   <li>调用聚合根工厂方法创建 {@link com.patra.objectstorage.domain.model.aggregate.FileMetadata}
 *   <li>配置可选属性(contentType、expiresAt、recordRemarks、ipAddress)
 *   <li>通过仓储端口持久化聚合根
 *   <li>记录审计日志
 *   <li>返回 {@link com.patra.objectstorage.app.recordupload.RecordUploadResult} 结果
 * </ol>
 *
 * <h2>使用示例</h2>
 *
 * <strong>在适配器层调用编排器</strong>:
 *
 * <pre>{@code
 * @RestController
 * @RequiredArgsConstructor
 * public class StorageEndpointImpl implements StorageEndpoint {
 *
 *     private final RecordUploadOrchestrator orchestrator;
 *
 *     @Override
 *     public RecordUploadResponse recordUpload(@Valid @RequestBody UploadRecordRequest request) {
 *         // 1. 构建应用层命令对象
 *         RecordUploadCommand command = new RecordUploadCommand(
 *             request.bucketName(),
 *             request.objectKey(),
 *             request.fileSize(),
 *             request.contentType(),
 *             request.md5Hash(),
 *             request.sha256Hash(),
 *             request.serviceName(),
 *             request.businessType(),
 *             request.businessId(),
 *             request.correlationData(),
 *             request.providerType(),
 *             request.expiresAt(),
 *             extractIpAddress(),
 *             request.recordRemarks()
 *         );
 *
 *         // 2. 委托给编排器执行用例
 *         RecordUploadResult result = orchestrator.execute(command);
 *
 *         // 3. 转换为 API 响应 DTO
 *         return new RecordUploadResponse(result.metadataId(), result.recordedAt());
 *     }
 * }
 * }</pre>
 *
 * <strong>编排器实现</strong>:
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class RecordUploadOrchestrator {
 *
 *     private final FileMetadataRepository repository;
 *
 *     @Transactional  // 定义事务边界
 *     public RecordUploadResult execute(RecordUploadCommand command) {
 *         // 1. 验证命令(可选,通常在适配器层已验证)
 *         Objects.requireNonNull(command, "command 不能为 null");
 *
 *         // 2. 将命令转换为领域对象
 *         FileMetadata metadata = FileMetadata.create(
 *                 new StorageKey(command.bucketName(), command.objectKey()),
 *                 new FileSize(command.fileSize()),
 *                 new FileChecksum(command.md5Hash(), command.sha256Hash()),
 *                 new BusinessContext(
 *                     command.serviceName(),
 *                     command.businessType(),
 *                     command.businessId(),
 *                     command.correlationData()
 *                 ),
 *                 StorageProvider.fromName(command.providerType())
 *             )
 *             .withContentType(command.contentType())
 *             .withExpiresAt(command.expiresAt())
 *             .withRecordRemarks(command.recordRemarks())
 *             .withIpAddress(command.ipAddress());
 *
 *         // 3. 通过仓储持久化聚合根
 *         FileMetadata saved = repository.save(metadata);
 *
 *         // 4. 记录审计日志
 *         log.info("文件元数据已记录: id={}, storageKey={}",
 *             saved.getId(), saved.getStorageKey().fullKey());
 *
 *         // 5. 返回结果对象
 *         return new RecordUploadResult(saved.getId(), saved.getUploadedAt());
 *     }
 * }
 * }</pre>
 *
 * <h2>事务管理</h2>
 *
 * 应用层编排器使用 {@code @Transactional} 注解定义事务边界:
 *
 * <ul>
 *   <li><strong>事务开始</strong>: 进入编排器方法时开启事务
 *   <li><strong>事务提交</strong>: 方法正常返回时提交事务
 *   <li><strong>事务回滚</strong>: 抛出 RuntimeException 时回滚事务
 *   <li><strong>传播行为</strong>: 默认 REQUIRED,嵌套调用时加入现有事务
 * </ul>
 *
 * <h2>异常处理</h2>
 *
 * 编排器可能抛出以下异常:
 *
 * <ul>
 *   <li><strong>NullPointerException</strong>: 命令对象为 null
 *   <li><strong>IllegalArgumentException</strong>: 领域对象构造时验证失败(如 bucket 为空)
 *   <li><strong>DuplicateKeyException</strong>: 违反 storage_key 唯一约束
 *   <li><strong>DataAccessException</strong>: 数据库访问失败
 * </ul>
 *
 * 异常处理由适配器层的全局异常处理器统一处理,转换为 HTTP ProblemDetail 响应。
 *
 * <h2>命令对象 vs DTO</h2>
 *
 * <table border="1">
 *   <tr>
 *     <th>特性</th>
 *     <th>Command(应用层)</th>
 *     <th>DTO(API 层)</th>
 *   </tr>
 *   <tr>
 *     <td>用途</td>
 *     <td>编排器输入参数</td>
 *     <td>API 请求/响应</td>
 *   </tr>
 *   <tr>
 *     <td>验证</td>
 *     <td>应用层验证</td>
 *     <td>适配器层验证(Jakarta Validation)</td>
 *   </tr>
 *   <tr>
 *     <td>字段</td>
 *     <td>业务相关字段</td>
 *     <td>API 契约字段</td>
 *   </tr>
 *   <tr>
 *     <td>变更影响</td>
 *     <td>内部实现</td>
 *     <td>外部契约(需版本化)</td>
 *   </tr>
 * </table>
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li>领域层: {@link com.patra.objectstorage.domain.model.aggregate.FileMetadata}
 *   <li>仓储端口: {@link com.patra.objectstorage.domain.port.FileMetadataRepository}
 *   <li>适配器层: {@code patra-object-storage-adapter/adapter/rest/internal/StorageEndpointImpl}
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.objectstorage.app.recordupload;
