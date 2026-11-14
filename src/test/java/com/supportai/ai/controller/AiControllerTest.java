package com.supportai.ai.controller;

import com.supportai.ai.TicketCategoryClassifier;
import com.supportai.ai.TicketPriorityClassifier;
import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.security.SecurityConfig;
import com.supportai.ticket.entity.TicketCategory;
import com.supportai.ticket.entity.TicketPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class AiControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private TicketCategoryClassifier categoryClassifier;

        @MockitoBean
        private TicketPriorityClassifier priorityClassifier;

        @MockitoBean
        private JwtDecoder jwtDecoder;

        // =====================================================================
        // Category Classification Tests (/api/ai/classify)
        // =====================================================================

        @Nested
        @DisplayName("Successful Category Classification")
        class SuccessfulClassificationTests {

                @Test
                @DisplayName("Valid CUSTOMER request returns HTTP 200 with classified category")
                void customerRequestReturnsOkWithCategory() throws Exception {
                        when(categoryClassifier.classify("Cannot login", "I cannot access my account."))
                                        .thenReturn(TicketCategory.ACCOUNT_ACCESS);

                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Cannot login",
                                                            "description": "I cannot access my account."
                                                        }
                                                        """))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.category").value("ACCOUNT_ACCESS"));

                        verify(categoryClassifier, times(1))
                                        .classify("Cannot login", "I cannot access my account.");
                }

                @Test
                @DisplayName("Valid AGENT request returns HTTP 200 with classified category")
                void agentRequestReturnsOkWithCategory() throws Exception {
                        when(categoryClassifier.classify("Double charge on invoice", "Customer was charged twice."))
                                        .thenReturn(TicketCategory.PAYMENT);

                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("2")
                                                        .claim("role", "AGENT"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_AGENT")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Double charge on invoice",
                                                            "description": "Customer was charged twice."
                                                        }
                                                        """))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.category").value("PAYMENT"));

                        verify(categoryClassifier, times(1))
                                        .classify("Double charge on invoice", "Customer was charged twice.");
                }

                @Test
                @DisplayName("Valid ADMIN request returns HTTP 200 with classified category")
                void adminRequestReturnsOkWithCategory() throws Exception {
                        when(categoryClassifier.classify("App crashes on checkout",
                                        "Application crashes when clicking checkout."))
                                        .thenReturn(TicketCategory.BUG_REPORT);

                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("3")
                                                        .claim("role", "ADMIN"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "App crashes on checkout",
                                                            "description": "Application crashes when clicking checkout."
                                                        }
                                                        """))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.category").value("BUG_REPORT"));

                        verify(categoryClassifier, times(1))
                                        .classify("App crashes on checkout",
                                                        "Application crashes when clicking checkout.");
                }

                @Test
                @DisplayName("Returns HTTP 200 with category null when classifier returns null")
                void unclassifiedRequestReturnsOkWithNullCategory() throws Exception {
                        when(categoryClassifier.classify("Vague title", "Vague description"))
                                        .thenReturn(null);

                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Vague title",
                                                            "description": "Vague description"
                                                        }
                                                        """))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.category").isEmpty());

                        verify(categoryClassifier, times(1))
                                        .classify("Vague title", "Vague description");
                }
        }

        @Nested
        @DisplayName("Category Authentication and Authorization")
        class AuthenticationAndAuthorizationTests {

                @Test
                @DisplayName("Unauthenticated request returns HTTP 401 Unauthorized")
                void unauthenticatedRequestReturnsUnauthorized() throws Exception {
                        mockMvc.perform(post("/api/ai/classify")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Cannot login",
                                                            "description": "I cannot access my account."
                                                        }
                                                        """))
                                        .andExpect(status().isUnauthorized());

                        verifyNoInteractions(categoryClassifier);
                }
        }

        @Nested
        @DisplayName("Category Request Validation")
        class ValidationTests {

                @Test
                @DisplayName("Returns HTTP 400 when title is missing")
                void missingTitleReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "description": "Valid description"
                                                        }
                                                        """))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.title").value("Title is required"));

                        verify(categoryClassifier, never()).classify(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when title is null")
                void nullTitleReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": null,
                                                            "description": "Valid description"
                                                        }
                                                        """))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.title").value("Title is required"));

                        verify(categoryClassifier, never()).classify(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when title is blank")
                void blankTitleReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "   ",
                                                            "description": "Valid description"
                                                        }
                                                        """))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.title").value("Title is required"));

                        verify(categoryClassifier, never()).classify(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when title exceeds 255 characters")
                void titleExceeding255CharsReturnsBadRequest() throws Exception {
                        String longTitle = "T".repeat(256);

                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "%s",
                                                            "description": "Valid description"
                                                        }
                                                        """.formatted(longTitle)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.title")
                                                        .value("Title must not exceed 255 characters"));

                        verify(categoryClassifier, never()).classify(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when description is missing")
                void missingDescriptionReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Valid title"
                                                        }
                                                        """))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.description").value("Description is required"));

                        verify(categoryClassifier, never()).classify(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when description is null")
                void nullDescriptionReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Valid title",
                                                            "description": null
                                                        }
                                                        """))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.description").value("Description is required"));

                        verify(categoryClassifier, never()).classify(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when description is blank")
                void blankDescriptionReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Valid title",
                                                            "description": "   "
                                                        }
                                                        """))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.description").value("Description is required"));

                        verify(categoryClassifier, never()).classify(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when description exceeds 10000 characters")
                void descriptionExceeding10000CharsReturnsBadRequest() throws Exception {
                        String longDescription = "D".repeat(10001);

                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Valid title",
                                                            "description": "%s"
                                                        }
                                                        """.formatted(longDescription)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.description")
                                                        .value("Description must not exceed 10000 characters"));

                        verify(categoryClassifier, never()).classify(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when request body is empty object")
                void emptyRequestBodyReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.title").value("Title is required"))
                                        .andExpect(jsonPath("$.errors.description").value("Description is required"));

                        verify(categoryClassifier, never()).classify(any(), any());
                }
        }

        // =====================================================================
        // Priority Suggestion Tests (/api/ai/suggest-priority)
        // =====================================================================

        @Nested
        @DisplayName("Successful Priority Suggestion")
        class SuccessfulPrioritySuggestionTests {

                @Test
                @DisplayName("Valid CUSTOMER request returns HTTP 200 with suggested priority")
                void customerRequestReturnsOkWithPriority() throws Exception {
                        when(priorityClassifier.classifyPriority("Production down", "All services unreachable."))
                                        .thenReturn(TicketPriority.URGENT);

                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Production down",
                                                            "description": "All services unreachable."
                                                        }
                                                        """))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.priority").value("URGENT"));

                        verify(priorityClassifier, times(1))
                                        .classifyPriority("Production down", "All services unreachable.");
                }

                @Test
                @DisplayName("Valid AGENT request returns HTTP 200 with suggested priority")
                void agentRequestReturnsOkWithPriority() throws Exception {
                        when(priorityClassifier.classifyPriority("Checkout failure", "Credit card gateway down."))
                                        .thenReturn(TicketPriority.HIGH);

                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("2")
                                                        .claim("role", "AGENT"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_AGENT")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Checkout failure",
                                                            "description": "Credit card gateway down."
                                                        }
                                                        """))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.priority").value("HIGH"));

                        verify(priorityClassifier, times(1))
                                        .classifyPriority("Checkout failure", "Credit card gateway down.");
                }

                @Test
                @DisplayName("Valid ADMIN request returns HTTP 200 with suggested priority")
                void adminRequestReturnsOkWithPriority() throws Exception {
                        when(priorityClassifier.classifyPriority("Minor typo in header", "Small typo on home page."))
                                        .thenReturn(TicketPriority.LOW);

                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("3")
                                                        .claim("role", "ADMIN"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Minor typo in header",
                                                            "description": "Small typo on home page."
                                                        }
                                                        """))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.priority").value("LOW"));

                        verify(priorityClassifier, times(1))
                                        .classifyPriority("Minor typo in header", "Small typo on home page.");
                }

                @Test
                @DisplayName("Returns HTTP 200 with priority null when classifier returns null")
                void unclassifiedPriorityReturnsOkWithNull() throws Exception {
                        when(priorityClassifier.classifyPriority("Uncertain issue", "General question."))
                                        .thenReturn(null);

                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Uncertain issue",
                                                            "description": "General question."
                                                        }
                                                        """))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.priority").isEmpty());

                        verify(priorityClassifier, times(1))
                                        .classifyPriority("Uncertain issue", "General question.");
                }
        }

        @Nested
        @DisplayName("Priority Authentication and Authorization")
        class PriorityAuthenticationAndAuthorizationTests {

                @Test
                @DisplayName("Unauthenticated request returns HTTP 401 Unauthorized")
                void unauthenticatedRequestReturnsUnauthorized() throws Exception {
                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Critical issue",
                                                            "description": "Production is down."
                                                        }
                                                        """))
                                        .andExpect(status().isUnauthorized());

                        verifyNoInteractions(priorityClassifier);
                }
        }

        @Nested
        @DisplayName("Priority Request Validation")
        class PriorityValidationTests {

                @Test
                @DisplayName("Returns HTTP 400 when title is missing")
                void missingTitleReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "description": "Valid description"
                                                        }
                                                        """))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.title").value("Title is required"));

                        verify(priorityClassifier, never()).classifyPriority(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when title is null")
                void nullTitleReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": null,
                                                            "description": "Valid description"
                                                        }
                                                        """))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.title").value("Title is required"));

                        verify(priorityClassifier, never()).classifyPriority(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when title is blank")
                void blankTitleReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "   ",
                                                            "description": "Valid description"
                                                        }
                                                        """))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.title").value("Title is required"));

                        verify(priorityClassifier, never()).classifyPriority(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when title exceeds 255 characters")
                void titleExceeding255CharsReturnsBadRequest() throws Exception {
                        String longTitle = "P".repeat(256);

                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "%s",
                                                            "description": "Valid description"
                                                        }
                                                        """.formatted(longTitle)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.title")
                                                        .value("Title must not exceed 255 characters"));

                        verify(priorityClassifier, never()).classifyPriority(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when description is missing")
                void missingDescriptionReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Valid title"
                                                        }
                                                        """))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.description").value("Description is required"));

                        verify(priorityClassifier, never()).classifyPriority(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when description is null")
                void nullDescriptionReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Valid title",
                                                            "description": null
                                                        }
                                                        """))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.description").value("Description is required"));

                        verify(priorityClassifier, never()).classifyPriority(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when description is blank")
                void blankDescriptionReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Valid title",
                                                            "description": "   "
                                                        }
                                                        """))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.description").value("Description is required"));

                        verify(priorityClassifier, never()).classifyPriority(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when description exceeds 10000 characters")
                void descriptionExceeding10000CharsReturnsBadRequest() throws Exception {
                        String longDescription = "D".repeat(10001);

                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Valid title",
                                                            "description": "%s"
                                                        }
                                                        """.formatted(longDescription)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.description")
                                                        .value("Description must not exceed 10000 characters"));

                        verify(priorityClassifier, never()).classifyPriority(any(), any());
                }

                @Test
                @DisplayName("Returns HTTP 400 when request body is empty object")
                void emptyRequestBodyReturnsBadRequest() throws Exception {
                        mockMvc.perform(post("/api/ai/suggest-priority")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.errors.title").value("Title is required"))
                                        .andExpect(jsonPath("$.errors.description").value("Description is required"));

                        verify(priorityClassifier, never()).classifyPriority(any(), any());
                }
        }
}
