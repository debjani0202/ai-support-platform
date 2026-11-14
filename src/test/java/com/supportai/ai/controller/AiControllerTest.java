package com.supportai.ai.controller;

import com.supportai.ai.TicketCategoryClassifier;
import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.security.SecurityConfig;
import com.supportai.ticket.entity.TicketCategory;
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
        private JwtDecoder jwtDecoder;

        @Nested
        @DisplayName("Successful Classification")
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
                                        "NullPointerException in payment gateway."))
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
                                                            "description": "NullPointerException in payment gateway."
                                                        }
                                                        """))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.category").value("BUG_REPORT"));

                        verify(categoryClassifier, times(1))
                                        .classify("App crashes on checkout",
                                                        "NullPointerException in payment gateway.");
                }

                @Test
                @DisplayName("Returns HTTP 200 with null category when classifier returns null")
                void returnsOkWithNullCategoryWhenUnclassified() throws Exception {
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
        @DisplayName("Security & Role Authorization")
        class SecurityTests {

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

                @Test
                @DisplayName("Multiple authorities containing FACTOR_BEARER and ROLE_CUSTOMER are accepted")
                void acceptsMultipleAuthoritiesWithFactorBearer() throws Exception {
                        when(categoryClassifier.classify("Slow dashboard", "Takes 40 seconds to load."))
                                        .thenReturn(TicketCategory.TECHNICAL_ISSUE);

                        mockMvc.perform(post("/api/ai/classify")
                                        .with(jwt().jwt(jwt -> jwt
                                                        .subject("1")
                                                        .claim("role", "CUSTOMER"))
                                                        .authorities(
                                                                        new SimpleGrantedAuthority("FACTOR_BEARER"),
                                                                        new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("""
                                                        {
                                                            "title": "Slow dashboard",
                                                            "description": "Takes 40 seconds to load."
                                                        }
                                                        """))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.category").value("TECHNICAL_ISSUE"));

                        verify(categoryClassifier, times(1))
                                        .classify("Slow dashboard", "Takes 40 seconds to load.");
                }
        }

        @Nested
        @DisplayName("Validation Rules")
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
                        String longTitle = "A".repeat(256);

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
}