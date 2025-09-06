/**
 * docref:/docs/adapter/rest/controller/README.md
 * docref:/docs/api/rest/dto/README.md
 * docref:/docs/schema/tables.inventory.md
 */
package com.patra.registry.adapter.rest.controller;

import com.patra.registry.api.rest.dto.request.PlatformFieldDictRequest;
import com.patra.registry.api.rest.dto.response.PlatformFieldDictResponse;
import com.patra.registry.app.service.PlatformFieldDictAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/registry/platform-field-dicts")
public class PlatformFieldDictController {

    private final PlatformFieldDictAppService appService;

    @PostMapping
    public ResponseEntity<PlatformFieldDictResponse> createDict(@Validated @RequestBody PlatformFieldDictRequest request) {
        // TODO: 实现创建字典功能
        return ResponseEntity.ok(PlatformFieldDictResponse.builder().build());
    }

    @GetMapping("/{code}")
    public ResponseEntity<PlatformFieldDictResponse> getDictByCode(@PathVariable String code) {
        // TODO: 实现按业务码查询功能
        return ResponseEntity.ok(PlatformFieldDictResponse.builder().build());
    }

    @PutMapping("/{code}")
    public ResponseEntity<PlatformFieldDictResponse> updateDict(@PathVariable String code, @Validated @RequestBody PlatformFieldDictRequest request) {
        // TODO: 实现更新字典功能
        return ResponseEntity.ok(PlatformFieldDictResponse.builder().build());
    }

    @PostMapping("/{code}:activate")
    public ResponseEntity<Void> activateDict(@PathVariable String code) {
        // TODO: 实现激活字典功能
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{code}:deactivate")
    public ResponseEntity<Void> deactivateDict(@PathVariable String code) {
        // TODO: 实现停用字典功能
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteDict(@PathVariable String code) {
        // TODO: 实现删除字典功能
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<PlatformFieldDictResponse>> listDicts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // TODO: 实现分页查询功能
        return ResponseEntity.ok(List.of());
    }
}
