package dev.patra.identity.infra.account.persistence.entity;

import dev.patra.tool.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// `idn_user_profile` 表的 JPA 实体。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "idn_user_profile")
public class UserProfileEntity extends BaseJpaEntity {

  /// 所属用户 ID
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /// 昵称，为 null 表示跟随用户名
  @Column(name = "display_name", length = 120)
  private String displayName;

  /// 简介
  @Column(name = "bio", length = 800)
  private String bio;

  /// 身份大类（枚举名）
  @Column(name = "occupation", length = 16)
  private String occupation;

  /// 职称
  @Column(name = "job_title", length = 200)
  private String jobTitle;

  /// 机构的裸 ROR ID；自由文本或没有机构时为 null
  @Column(name = "organization_ror_id", length = 9)
  private String organizationRorId;

  /// 机构名：有 ROR ID 时是 catalog 的快照，否则是手填的自由文本
  @Column(name = "organization_name", length = 800)
  private String organizationName;

  /// 科室
  @Column(name = "department", length = 400)
  private String department;

  /// 主页可见性（枚举名）
  @Column(name = "profile_visibility", nullable = false, length = 16)
  private String profileVisibility;

  /// 专业背景可见性（枚举名）
  @Column(name = "professional_visibility", nullable = false, length = 16)
  private String professionalVisibility;

  /// 是否允许搜索引擎收录主页
  @Column(name = "search_indexing", nullable = false)
  private boolean searchIndexing;

  /// 512 档头像的对象键，没上传时为 null
  @Column(name = "avatar_object_key", length = 64)
  private String avatarObjectKey;
}
