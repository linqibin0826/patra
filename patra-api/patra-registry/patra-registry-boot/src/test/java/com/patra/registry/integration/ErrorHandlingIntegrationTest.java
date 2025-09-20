package com.patra.registry.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.registry.api.error.RegistryErrorCode;
import com.patra.registry.domain.exception.DictionaryItemDisabled;
import com.patra.registry.domain.exception.DictionaryNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the complete error handling system.
 * Tests the end-to-end flow from domain exceptions to HTTP responses.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@SpringBootTest(classes = {
    ErrorHandlingIntegrationTest.TestController.class,
    com.patra.registry.PatraRegistryApplication.class
})
@AutoConfigureWebMvc
@TestPropertySource(properties = {
    "patra.error.context-prefix=REG",
    "patra.web.problem.type-base-url=https://registry.errors.com/",
    "logging.level.com.patra=DEBUG"
})
class ErrorHandlingIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Test DictionaryNotFoundException is converted to proper ProblemDetail response.
     */
    @Test
    void shouldConvertDictionaryNotFoundExceptionToProblemDetail() throws Exception {
        // When & Then
        MvcResult result = mockMvc.perform(get("/test/dictionary-not-found/USER_TYPE"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://registry.errors.com/reg-0404"))
            .andExpect(jsonPath("$.title").value("REG-0404"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("Dictionary type not found: USER_TYPE"))
            .andExpect(jsonPath("$.code").value("REG-0404"))
            .andExpect(jsonPath("$.path").value("/test/dictionary-not-found/USER_TYPE"))
            .andExpect(jsonPath("$.timestamp").exists())
            .andReturn();
        
        // Verify response structure
        String responseBody = result.getResponse().getContentAsString();
        JsonNode problemDetail = objectMapper.readTree(responseBody);
        
        assertThat(problemDetail.has("type")).isTrue();
        assertThat(problemDetail.has("title")).isTrue();
        assertThat(problemDetail.has("status")).isTrue();
        assertThat(problemDetail.has("detail")).isTrue();
        assertThat(problemDetail.has("code")).isTrue();
        assertThat(problemDetail.has("path")).isTrue();
        assertThat(problemDetail.has("timestamp")).isTrue();
    }
    
    /**
     * Test DictionaryItemDisabled is converted to proper ProblemDetail response with specific error code.
     */
    @Test
    void shouldConvertDictionaryItemDisabledToProblemDetailWithSpecificCode() throws Exception {
        // When & Then
        mockMvc.perform(get("/test/dictionary-item-disabled/USER_TYPE/ADMIN"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://registry.errors.com/reg-1403"))
            .andExpect(jsonPath("$.title").value("REG-1403"))
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.detail").value("Dictionary item is disabled: typeCode=USER_TYPE, itemCode=ADMIN"))
            .andExpect(jsonPath("$.code").value("REG-1403"))
            .andExpect(jsonPath("$.path").value("/test/dictionary-item-disabled/USER_TYPE/ADMIN"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
    
    /**
     * Test generic RuntimeException is converted to fallback error code.
     */
    @Test
    void shouldConvertGenericExceptionToFallbackErrorCode() throws Exception {
        // When & Then
        mockMvc.perform(get("/test/generic-error"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://registry.errors.com/reg-0500"))
            .andExpect(jsonPath("$.title").value("REG-0500"))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.detail").value("Generic runtime exception"))
            .andExpect(jsonPath("$.code").value("REG-0500"))
            .andExpect(jsonPath("$.path").value("/test/generic-error"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
    
    /**
     * Test ApplicationException with specific error code is handled correctly.
     */
    @Test
    void shouldHandleApplicationExceptionWithSpecificErrorCode() throws Exception {
        // When & Then
        mockMvc.perform(get("/test/application-error"))
            .andExpect(status().isConflict())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://registry.errors.com/reg-0409"))
            .andExpect(jsonPath("$.title").value("REG-0409"))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail").value("Application level conflict"))
            .andExpect(jsonPath("$.code").value("REG-0409"))
            .andExpect(jsonPath("$.path").value("/test/application-error"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
    
    /**
     * Test that sensitive data is masked in error responses.
     */
    @Test
    void shouldMaskSensitiveDataInErrorResponses() throws Exception {
        // When & Then
        mockMvc.perform(get("/test/sensitive-error"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Error with password=*** and token=***"));
    }
    
    /**
     * Test controller for integration testing purposes.
     */
    @RestController
    @RequestMapping("/test")
    static class TestController {
        
        @GetMapping("/dictionary-not-found/{typeCode}")
        public String dictionaryNotFound(@PathVariable String typeCode) {
            throw new DictionaryNotFoundException(typeCode);
        }
        
        @GetMapping("/dictionary-item-disabled/{typeCode}/{itemCode}")
        public String dictionaryItemDisabled(@PathVariable String typeCode, @PathVariable String itemCode) {
            throw new DictionaryItemDisabled(typeCode, itemCode);
        }
        
        @GetMapping("/generic-error")
        public String genericError() {
            throw new RuntimeException("Generic runtime exception");
        }
        
        @GetMapping("/application-error")
        public String applicationError() {
            throw new com.patra.common.error.ApplicationException(
                RegistryErrorCode.REG_0409, 
                "Application level conflict"
            );
        }
        
        @GetMapping("/sensitive-error")
        public String sensitiveError() {
            throw new RuntimeException("Error with password=secret123 and token=abc456def");
        }
    }
}