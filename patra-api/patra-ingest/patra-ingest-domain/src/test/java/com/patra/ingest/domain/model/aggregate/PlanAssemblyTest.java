package com.patra.ingest.domain.model.aggregate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PlanAssembly} 的单元测试。
 */
class PlanAssemblyTest {

    @Test
    @DisplayName("plan 不能为空；slices/tasks/status 提供默认值")
    void ctorDefaultsAndValidation() {
        PlanAggregate plan = PlanAggregate.create(1L, "k", "PUBMED", null, null, null, null, null, null, null, null, null, null);

        PlanAssembly assembly = new PlanAssembly(plan, null, null, null);
        assertNotNull(assembly);
        assertEquals(0, assembly.slices().size());
        assertEquals(0, assembly.tasks().size());
        assertEquals(PlanAssembly.PlanAssemblyStatus.READY, assembly.status());

        // 非空集合也会 copy 保护
        var slices = List.of(PlanSliceAggregate.create(1L, "P", 1, "sig", "{}", "eh", "es"));
        var tasks = List.of(TaskAggregate.create(1L, 2L, 3L, "P", "O", "{}", "k", "e", 1, null));
        PlanAssembly assembly2 = new PlanAssembly(plan, slices, tasks, PlanAssembly.PlanAssemblyStatus.PARTIAL);
        assertEquals(1, assembly2.slices().size());
        assertEquals(1, assembly2.tasks().size());
        assertEquals(PlanAssembly.PlanAssemblyStatus.PARTIAL, assembly2.status());

        // plan 为 null 抛出
        assertThrows(NullPointerException.class, () -> new PlanAssembly(null, slices, tasks, null));
    }
}
