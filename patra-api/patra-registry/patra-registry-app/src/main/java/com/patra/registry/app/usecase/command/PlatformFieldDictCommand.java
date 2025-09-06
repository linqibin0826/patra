/**
 * docref:/docs/app/usecase/command/README.md
 * docref:/docs/domain/aggregates.discovery.md
 * docref:/docs/api/rest/dto/README.md
 */
package com.patra.registry.app.usecase.command;

import com.patra.registry.domain.enums.Cardinality;
import com.patra.registry.domain.enums.DataType;
import com.patra.registry.domain.enums.DateType;
import lombok.Builder;
import lombok.Value;

public class PlatformFieldDictCommand {

    @Value
    @Builder
    public static class CreateDict {
        String code;
        String name;
        String description;
        DataType dataType;
        Cardinality cardinality;
        DateType dateType;
        String defaultValue;
        String validationRules;
        String category;
        String remarks;
    }

    @Value
    @Builder
    public static class UpdateDict {
        String code;
        String name;
        String description;
        DataType dataType;
        Cardinality cardinality;
        DateType dateType;
        String defaultValue;
        String validationRules;
        String category;
        String remarks;
    }

    @Value
    @Builder
    public static class ActivateDict {
        String code;
        String reason;
    }

    @Value
    @Builder
    public static class DeactivateDict {
        String code;
        String reason;
    }

    @Value
    @Builder
    public static class DeleteDict {
        String code;
        String reason;
    }

    @Value
    @Builder
    public static class SyncDict {
        String code;
        boolean forceSync;
    }

    @Value
    @Builder
    public static class ValidateDict {
        String code;
        String validationScope;
    }
}
