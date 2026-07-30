package com.hungtvb.votesystem.admin.audit;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AdminAuditApiSurfaceTests {

    @Test
    void repositoryExposesOnlyTheFilteredReadQuery() {
        Set<String> methodNames = Arrays.stream(AdminAuditLogRepository.class.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("findAllFiltered"), methodNames);
    }

    @Test
    void serviceExposesNoUpdateOrDeleteOperation() {
        Set<String> methodNames = Arrays.stream(AdminAuditLogService.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("append", "list"), methodNames);
        assertFalse(methodNames.stream().anyMatch(name -> name.startsWith("update") || name.startsWith("delete")));
    }
}
