/**
 * docref:/docs/app/usecase/query/README.md
 * docref:/docs/domain/aggregates.discovery.md
 * docref:/docs/api/rest/dto/README.md
 */
package com.patra.registry.app.usecase.query;

import com.patra.registry.domain.enums.Cardinality;
import com.patra.registry.domain.enums.DataType;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

public class PlatformFieldDictQuery {

    @Value
    @Builder
    public static class FindByCode {
        String code;
    }

    @Value
    @Builder
    public static class FindActiveDicts {
        String category;
        DataType dataType;
        int page;
        int size;
    }

    @Value
    @Builder
    public static class SearchDicts {
        String keyword;
        Set<DataType> dataTypes;
        Set<Cardinality> cardinalities;
        String category;
        Boolean active;
        int page;
        int size;
        String sortBy;
        String sortDirection;
    }

    @Value
    @Builder
    public static class FindByCategory {
        String category;
        boolean includeInactive;
    }

    @Value
    @Builder
    public static class FindByDataType {
        DataType dataType;
        boolean includeInactive;
    }

    @Value
    @Builder
    public static class GetDictStatistics {
        String category;
        String dateRange;
    }

    @Value
    @Builder
    public static class FindRecentlyUpdated {
        int days;
        int limit;
    }

    @Value
    @Builder
    public static class ValidateDictConfig {
        String code;
        String configJson;
    }
}
