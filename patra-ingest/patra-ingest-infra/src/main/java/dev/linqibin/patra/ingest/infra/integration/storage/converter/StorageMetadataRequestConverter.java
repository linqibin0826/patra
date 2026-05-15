package dev.linqibin.patra.ingest.infra.integration.storage.converter;

import com.patra.objectstorage.api.dto.UploadRecordRequest;
import dev.linqibin.patra.ingest.domain.port.StorageMetadataPort.MetadataRequest;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// 存储元数据请求转换器。
///
/// 负责将领域层的 {@link MetadataRequest} 转换为 RPC 层的 {@link UploadRecordRequest}。
/// 使用 {@link MappingContext} 注入基础设施层特定的默认值（serviceName、providerType）。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StorageMetadataRequestConverter {

  /// 将领域元数据请求转换为 RPC 请求。
  ///
  /// @param request 领域层元数据请求
  /// @param context 映射上下文，包含基础设施层默认值
  /// @return RPC 请求 DTO
  @Mapping(target = "md5Hash", source = "request.md5")
  @Mapping(target = "sha256Hash", source = "request.sha256")
  @Mapping(target = "correlationData", source = "request.correlation")
  @Mapping(target = "recordRemarks", source = "request.remarks")
  @Mapping(
      target = "serviceName",
      expression = "java(resolveServiceName(request.serviceName(), context))")
  @Mapping(
      target = "providerType",
      expression = "java(resolveProviderType(request.providerType(), context))")
  @Mapping(target = "expiresAt", ignore = true) // 出版物存储不使用过期时间
  UploadRecordRequest toRpcRequest(MetadataRequest request, @Context MappingContext context);

  /// 解析服务名称。
  ///
  /// 如果请求中指定了服务名称则使用，否则使用上下文中的默认值。
  ///
  /// @param requestServiceName 请求中的服务名称
  /// @param context 映射上下文
  /// @return 最终的服务名称
  default String resolveServiceName(String requestServiceName, MappingContext context) {
    return hasText(requestServiceName) ? requestServiceName : context.defaultServiceName();
  }

  /// 解析存储提供商类型。
  ///
  /// 如果请求中指定了提供商类型则使用，否则使用上下文中的默认值。
  ///
  /// @param requestProviderType 请求中的提供商类型
  /// @param context 映射上下文
  /// @return 最终的提供商类型
  default String resolveProviderType(String requestProviderType, MappingContext context) {
    return hasText(requestProviderType) ? requestProviderType : context.defaultProviderType();
  }

  /// 检查字符串是否有内容。
  ///
  /// @param str 待检查的字符串
  /// @return 如果字符串非空且非空白则返回 true
  private static boolean hasText(String str) {
    return str != null && !str.isBlank();
  }

  /// 映射上下文，携带基础设施层特定的默认值。
  ///
  /// @param defaultServiceName 默认服务名称（从 spring.application.name 获取）
  /// @param defaultProviderType 默认存储提供商类型（从 ObjectStorageProperties 获取）
  record MappingContext(String defaultServiceName, String defaultProviderType) {}
}
