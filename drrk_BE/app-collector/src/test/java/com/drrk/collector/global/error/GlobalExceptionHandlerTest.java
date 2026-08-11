package com.drrk.collector.global.error;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drrk.global.error.BusinessException;
import com.drrk.global.error.ErrorCode;
import com.drrk.global.error.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void handlesCollectorBusinessExceptionWithSameResponseContract() throws Exception {
        mockMvc.perform(get("/collector/business")
                        .queryParam("ignored", "query"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("COLLECTOR-503-001"))
                .andExpect(jsonPath("$.message").value("외부 수집 대상에 연결할 수 없습니다."))
                .andExpect(jsonPath("$.path").value("/collector/business"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void handlesCollectorValidationWithoutRejectedValue() throws Exception {
        mockMvc.perform(post("/collector/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON-400-001"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.path").value("/collector/validation"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("source"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("수집 대상은 필수입니다."))
                .andExpect(jsonPath("$.fieldErrors[0].rejectedValue").doesNotExist());
    }

    @Test
    void handlesOnlyFirstCollectorValidationErrorPerField() throws Exception {
        mockMvc.perform(post("/collector/duplicate-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400-001"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("source"))
                .andExpect(jsonPath("$.fieldErrors[0].message", anyOf(
                        is("수집 대상은 필수입니다."),
                        is("수집 대상은 2자 이상이어야 합니다.")
                )));
    }

    @Test
    void handlesCollectorMalformedJson() throws Exception {
        mockMvc.perform(post("/collector/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400-002"))
                .andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다."))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void handlesCollectorTypeMismatch() throws Exception {
        mockMvc.perform(get("/collector/type")
                        .queryParam("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400-003"))
                .andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void handlesCollectorConstraintViolation() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<ConstraintRequest>> violations = validator.validate(new ConstraintRequest(0));
        ConstraintViolationException exception = new ConstraintViolationException(violations);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/collector/constraint");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleConstraintViolationException(exception, request);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COMMON-400-001", response.getBody().code());
        assertEquals("/collector/constraint", response.getBody().path());
        assertEquals(1, response.getBody().fieldErrors().size());
        assertEquals("limit", response.getBody().fieldErrors().getFirst().field());
        assertEquals("1 이상이어야 합니다.", response.getBody().fieldErrors().getFirst().message());
    }

    @Test
    void handlesCollectorNoResourceFound() {
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/collector/missing", "static resource");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/collector/missing");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleNoResourceFoundException(exception, request);

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COMMON-404-001", response.getBody().code());
        assertEquals("요청한 리소스를 찾을 수 없습니다.", response.getBody().message());
        assertEquals("/collector/missing", response.getBody().path());
        assertEquals(0, response.getBody().fieldErrors().size());
    }

    @Test
    void handlesCollectorMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/collector/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("COMMON-405-001"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 요청 방식입니다."))
                .andExpect(jsonPath("$.path").value("/collector/business"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void handlesCollectorMethodNotAllowedDirectly() {
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("POST", List.of("GET"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/collector/business");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleHttpRequestMethodNotSupportedException(exception, request);

        assertEquals(405, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COMMON-405-001", response.getBody().code());
        assertEquals("/collector/business", response.getBody().path());
    }

    @Test
    void handlesCollectorUnexpectedExceptionWithoutLeakingExceptionMessage() throws Exception {
        mockMvc.perform(get("/collector/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON-500-001"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                .andExpect(jsonPath("$.message", not(containsString("vendor token"))));
    }

    @Test
    void handlesCollectorRequestParamValidationWithInvalidRequest() throws Exception {
        Method method = TestController.class.getDeclaredMethod("paramValidation", int.class);
        MethodParameter methodParameter = new MethodParameter(method, 0);
        ParameterValidationResult parameterValidationResult = new ParameterValidationResult(
                methodParameter,
                -1,
                List.of(new DefaultMessageSourceResolvable(new String[] {"page"}, "양수여야 합니다.")),
                null,
                null,
                null,
                (resolvable, type) -> null
        );
        HandlerMethodValidationException exception = new HandlerMethodValidationException(
                MethodValidationResult.create(new TestController(), method, List.of(parameterValidationResult))
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/collector/param-validation");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleHandlerMethodValidationException(exception, request);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COMMON-400-001", response.getBody().code());
        assertEquals("요청 값이 올바르지 않습니다.", response.getBody().message());
        assertEquals("/collector/param-validation", response.getBody().path());
        assertEquals(1, response.getBody().fieldErrors().size());
        assertEquals("page", response.getBody().fieldErrors().getFirst().field());
        assertEquals("양수여야 합니다.", response.getBody().fieldErrors().getFirst().message());
    }

    @Validated
    @RestController
    private static class TestController {

        @GetMapping("/collector/business")
        void business() {
            throw new BusinessException(TestErrorCode.EXTERNAL_SOURCE_UNAVAILABLE);
        }

        @PostMapping("/collector/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }

        @PostMapping("/collector/duplicate-validation")
        void duplicateValidation(@Valid @RequestBody DuplicateValidationRequest request) {
        }

        @GetMapping("/collector/type")
        void type(@RequestParam("limit") int limit) {
        }

        @GetMapping("/collector/param-validation")
        void paramValidation(@RequestParam("page") @Positive int page) {
        }

        @GetMapping("/collector/unexpected")
        void unexpected() {
            throw new IllegalStateException("vendor token leaked");
        }
    }

    private record TestRequest(
            @NotBlank(message = "수집 대상은 필수입니다.")
            String source
    ) {
    }

    private record DuplicateValidationRequest(
            @NotBlank(message = "수집 대상은 필수입니다.")
            @Size(min = 2, message = "수집 대상은 2자 이상이어야 합니다.")
            String source
    ) {
    }

    private record ConstraintRequest(
            @Min(value = 1, message = "1 이상이어야 합니다.")
            int limit
    ) {
    }

    private enum TestErrorCode implements ErrorCode {
        EXTERNAL_SOURCE_UNAVAILABLE(503, "COLLECTOR-503-001", "외부 수집 대상에 연결할 수 없습니다.");

        private final int status;
        private final String code;
        private final String message;

        TestErrorCode(int status, String code, String message) {
            this.status = status;
            this.code = code;
            this.message = message;
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }
}
