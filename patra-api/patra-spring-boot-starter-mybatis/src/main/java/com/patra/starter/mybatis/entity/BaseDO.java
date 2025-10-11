package com.patra.starter.mybatis.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Abstract base class for Data Objects (DOs) providing common fields for auditing,
 * optimistic locking, and soft deletion.
 * <p>
 * This class is intended to be extended by all persistent entities (DOs)
 * to ensure consistent handling of common concerns like tracking creation/update metadata,
 * managing concurrent modifications, and supporting logical deletion.
 * It integrates with MyBatis-Plus's automatic field population features.
 * </p>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public abstract class BaseDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The primary key for the entity.
     * <p>
     * It uses MyBatis-Plus's {@link IdType#ASSIGN_ID}, which is typically configured
     * with a distributed ID generator (e.g., Snowflake) to ensure uniqueness across the system.
     * </p>
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * A JSON-formatted string for storing remarks or an audit trail of changes.
     * <p>
     * This field can be used to log significant events or comments related to the entity's lifecycle.
     * For example: {@code [{"timestamp":"2025-10-11T10:00:00Z","user":"admin","action":"Created"}]}
     * </p>
     * It is recommended to manage this field using a Jackson {@code JsonNode} coupled with a custom TypeHandler.
     */
    @TableField(value = "record_remarks")
    private String recordRemarks;

    /**
     * The timestamp indicating when the entity was created.
     * <p>
     * This field is automatically populated by the {@link MetaObjectHandler} upon insertion.
     * </p>
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * The identifier of the user who created the entity.
     * <p>
     * This field should be populated by the {@link MetaObjectHandler} upon insertion,
     * typically by retrieving the current user's ID from the security context.
     * </p>
     */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * The name of the user who created the entity.
     * <p>
     * This field provides a denormalized, human-readable name for the creator,
     * which can be useful for display purposes and avoids extra lookups.
     * It should be populated by the {@link MetaObjectHandler} upon insertion.
     * </p>
     */
    @TableField(value = "created_by_name", fill = FieldFill.INSERT)
    private String createdByName;

    /**
     * The timestamp indicating when the entity was last updated.
     * <p>
     * This field is automatically populated by the {@link MetaObjectHandler} upon both insertion and update.
     * </p>
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /**
     * The identifier of the user who last updated the entity.
     * <p>
     * This field should be populated by the {@link MetaObjectHandler} upon insertion and update,
     * typically by retrieving the current user's ID from the security context.
     * </p>
     */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /**
     * The name of the user who last updated the entity.
     * <p>
     * This field provides a denormalized, human-readable name for the last modifier.
     * It should be populated by the {@link MetaObjectHandler} upon insertion and update.
     * </p>
     */
    @TableField(value = "updated_by_name", fill = FieldFill.INSERT_UPDATE)
    private String updatedByName;

    /**
     * The version number used for optimistic locking.
     * <p>
     * This field is managed by MyBatis-Plus's {@link OptimisticLockerInnerInterceptor}
     * to prevent concurrent update conflicts. It is automatically incremented on each update.
     * </p>
     */
    @Version
    @TableField(value = "version")
    private Long version;

    /**
     * The IP address of the client that initiated the request, stored in a binary format.
     * <p>
     * Storing the IP address as a byte array allows for efficient storage of both IPv4 and IPv6 addresses.
     * </p>
     */
    @TableField(value = "ip_address")
    private byte[] ipAddress;

    /**
     * A flag indicating whether the entity has been logically deleted.
     * <p>
     * This field enables soft deletion. A value of {@code true} (or 1) means the entity is deleted,
     * while {@code false} (or 0) means it is active. MyBatis-Plus queries will automatically
     * filter for non-deleted records.
     * </p>
     */
    @TableLogic
    @TableField(value = "deleted")
    private Boolean deleted;
}