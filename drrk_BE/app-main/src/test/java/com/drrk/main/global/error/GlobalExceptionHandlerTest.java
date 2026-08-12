package com.drrk.main.global.error;

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
import org.springframework.core.DefaultParameterNameDiscoverer;
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

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void handlesBusinessExceptionWithErrorCodeContract() throws Exception {
        mockMvc.perform(get("/test/business")
                        .queryParam("ignored", "query"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("MAIN-409-001"))
                .andExpect(jsonPath("$.message").value("메인 비즈니스 요청을 처리할 수 없습니다."))
                .andExpect(jsonPath("$.path").value("/test/business"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void handlesRequestBodyValidationWithoutRejectedValue() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON-400-001"))
                .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.path").value("/test/validation"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("이름은 필수입니다."))
                .andExpect(jsonPath("$.fieldErrors[0].rejectedValue").doesNotExist());
    }

    @Test
    void handlesOnlyFirstValidationErrorPerField() throws Exception {
        mockMvc.perform(post("/test/duplicate-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400-001"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("code"))
                .andExpect(jsonPath("$.fieldErrors[0].message", anyOf(
                        is("코드는 필수입니다."),
                        is("코드는 2자 이상이어야 합니다.")
                )));
    }

    @Test
    void handlesMalformedJson() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400-002"))
                .andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다."))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void handlesTypeMismatch() throws Exception {
        mockMvc.perform(get("/test/type")
                        .queryParam("value", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400-003"))
                .andExpect(jsonPath("$.message").value("요청 값의 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void handlesConstraintViolation() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<ConstraintRequest>> violations = validator.validate(new ConstraintRequest(0));
        ConstraintViolationException exception = new ConstraintViolationException(violations);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/constraint");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleConstraintViolationException(exception, request);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COMMON-400-001", response.getBody().code());
        assertEquals("/test/constraint", response.getBody().path());
        assertEquals(1, response.getBody().fieldErrors().size());
        assertEquals("value", response.getBody().fieldErrors().getFirst().field());
        assertEquals("1 이상이어야 합니다.", response.getBody().fieldErrors().getFirst().message());
    }

    @Test
    void handlesNoResourceFound() {
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/missing", "static resource");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/missing");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleNoResourceFoundException(exception, request);

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COMMON-404-001", response.getBody().code());
        assertEquals("요청한 리소스를 찾을 수 없습니다.", response.getBody().message());
        assertEquals("/missing", response.getBody().path());
        assertEquals(0, response.getBody().fieldErrors().size());
    }

    @Test
    void handlesMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("COMMON-405-001"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 요청 방식입니다."))
                .andExpect(jsonPath("$.path").value("/test/business"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)));
    }

    @Test
    void handlesMethodNotAllowedDirectly() {
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("POST", List.of("GET"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/business");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleHttpRequestMethodNotSupportedException(exception, request);

        assertEquals(405, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COMMON-405-001", response.getBody().code());
        assertEquals("/test/business", response.getBody().path());
    }

    @Test
    void handlesUnexpectedExceptionWithoutLeakingExceptionMessage() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON-500-001"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.fieldErrors", hasSize(0)))
                .andExpect(jsonPath("$.message", not(containsString("database password"))));
    }

    @Test
    void handlesHandlerMethodValidationExceptionForRequestParam() throws Exception {
        Method method = TestController.class.getDeclaredMethod("validatedParam", int.class);
        MethodParameter methodParameter = new MethodParameter(method, 0);
        methodParameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        ParameterValidationResult parameterValidationResult = new ParameterValidationResult(
                methodParameter,
                -1,
                List.of(new DefaultMessageSourceResolvable(new String[] {"count"}, "양수여야 합니다.")),
                null,
                null,
                null,
                (resolvable, type) -> null
        );
        HandlerMethodValidationException exception = new HandlerMethodValidationException(
                MethodValidationResult.create(new TestController(), method, List.of(parameterValidationResult))
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/validated-param");

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleHandlerMethodValidationException(exception, request);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COMMON-400-001", response.getBody().code());
        assertEquals("요청 값이 올바르지 않습니다.", response.getBody().message());
        assertEquals("/test/validated-param", response.getBody().path());
        assertEquals(1, response.getBody().fieldErrors().size());
        assertEquals("count", response.getBody().fieldErrors().getFirst().field());
        assertEquals("양수여야 합니다.", response.getBody().fieldErrors().getFirst().message());
    }

    @Validated
    @RestController
    private static class TestController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(TestErrorCode.MAIN_CONFLICT);
        }

        @PostMapping("/test/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }

        @PostMapping("/test/duplicate-validation")
        void duplicateValidation(@Valid @RequestBody DuplicateValidationRequest request) {
        }

        @GetMapping("/test/type")
        void type(@RequestParam("value") int value) {
        }

        @GetMapping("/test/validated-param")
        void validatedParam(@RequestParam("count") @Positive(message = "양수여야 합니다.") int count) {
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("database password leaked");
        }
    }

    private record TestRequest(
            @NotBlank(message = "이름은 필수입니다.")
            String name
    ) {
    }

    private record DuplicateValidationRequest(
            @NotBlank(message = "코드는 필수입니다.")
            @Size(min = 2, message = "코드는 2자 이상이어야 합니다.")
            String code
    ) {
    }

    private record ConstraintRequest(
            @Min(value = 1, message = "1 이상이어야 합니다.")
            int value
    ) {
    }

    private enum TestErrorCode implements ErrorCode {
        MAIN_CONFLICT(409, "MAIN-409-001", "메인 비즈니스 요청을 처리할 수 없습니다.");

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
