package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 鉴权 / 密钥（Credential）配置响应 DTO。<br>
 * <p>对应表：reg_prov_credential。支持多种鉴权形态（API_KEY / BASIC / OAUTH2 等），并允许在不同端点/任务维度下多条并行。</p>
 * 字段说明：
 * <ul>
 *   <li>{@code id} 主键。</li>
 *   <li>{@code provenanceId} 来源 ID。</li>
 *   <li>{@code scopeCode} 作用域。</li>
 *   <li>{@code taskType} 任务类型。</li>
 *   <li>{@code taskTypeKey} 任务子键。</li>
 *   <li>{@code endpointId} 绑定端点（为空表示在该来源/任务全局可用）。</li>
 *   <li>{@code credentialName} 凭证名称（唯一标识，同来源内区分不同密钥）。</li>
 *   <li>{@code authType} 认证类型枚举：API_KEY / BASIC / OAUTH2_CLIENT_CREDENTIALS / BEARER_TOKEN 等。</li>
 *   <li>{@code inboundLocationCode} 凭证注入位置：HEADER / QUERY / BODY / PATH / COOKIE。</li>
 *   <li>{@code credentialFieldName} 注入字段名（如 Authorization / api-key）。</li>
 *   <li>{@code credentialValuePrefix} 值前缀（如 "Bearer " / "Basic "）。</li>
 *   <li>{@code credentialValueRef} 密钥值引用（指向安全存储 Key，如 vault://path/key）。</li>
 *   <li>{@code basicUsernameRef} BASIC 模式用户名引用。</li>
 *   <li>{@code basicPasswordRef} BASIC 模式密码引用。</li>
 *   <li>{@code oauthTokenUrl} OAuth2 获取 token 的授权端点。</li>
 *   <li>{@code oauthClientIdRef} OAuth2 ClientId 引用。</li>
 *   <li>{@code oauthClientSecretRef} OAuth2 ClientSecret 引用。</li>
 *   <li>{@code oauthScope} OAuth 请求 scope（空格分隔）。</li>
 *   <li>{@code oauthAudience} OAuth audience（部分供应商要求）。</li>
 *   <li>{@code extraJson} 额外扩展 JSON（刷新策略 / 自定义 Header 等）。</li>
 *   <li>{@code effectiveFrom} 生效起。</li>
 *   <li>{@code effectiveTo} 生效止。</li>
 *   <li>{@code defaultPreferred} 是否在同类凭证中默认优选。</li>
 *   <li>{@code lifecycleStatusCode} 生命周期：ACTIVE / ROTATING / DEPRECATED / REVOKED。</li>
 * </ul>
 * 使用建议：调用方在多凭证时按：可用生命周期 -> defaultPreferred -> 熵值/权重 进行选择，支持灰度与轮换。
 */
public record CredentialResp(
        /** 主键 ID */
        Long id,
        /** 来源 ID */
        Long provenanceId,
        /** 作用域编码 */
        String scopeCode,
        /** 任务类型 */
        String taskType,
        /** 任务子键 */
        String taskTypeKey,
        /** 绑定端点 ID（可空） */
        Long endpointId,
        /** 凭证名称 */
        String credentialName,
        /** 认证类型 */
        String authType,
        /** 注入位置编码 */
        String inboundLocationCode,
        /** 注入字段名 */
        String credentialFieldName,
        /** 值前缀（如 Bearer ） */
        String credentialValuePrefix,
        /** 密钥值引用 */
        String credentialValueRef,
        /** BASIC 用户名引用 */
        String basicUsernameRef,
        /** BASIC 密码引用 */
        String basicPasswordRef,
        /** OAuth token URL */
        String oauthTokenUrl,
        /** OAuth clientId 引用 */
        String oauthClientIdRef,
        /** OAuth clientSecret 引用 */
        String oauthClientSecretRef,
        /** OAuth scope */
        String oauthScope,
        /** OAuth audience */
        String oauthAudience,
        /** 额外扩展 JSON */
        String extraJson,
        /** 生效起 */
        Instant effectiveFrom,
        /** 生效止（不含） */
        Instant effectiveTo,
        /** 是否默认优先 */
        boolean defaultPreferred,
        /** 生命周期状态编码 */
        String lifecycleStatusCode
) {
}
