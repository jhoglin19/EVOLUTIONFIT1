package com.evolutionfit.modules.receipts;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evolutionfit.config.AppProperties;
import com.evolutionfit.modules.receipts.dto.BusinessInfoResponse;
import com.evolutionfit.security.AuthUserDetails;
import com.evolutionfit.security.AuthUserDetailsService;
import com.evolutionfit.security.JwtService;
import com.evolutionfit.security.RestAccessDeniedHandler;
import com.evolutionfit.security.RestAuthenticationEntryPoint;
import com.evolutionfit.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BusinessInfoController.class,
        properties = "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-seguridad")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(AppProperties.class)
class BusinessInfoControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReceiptSettingsService service;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthUserDetailsService userDetailsService;

    @Test
    void receptionSeesOnlyTheTradeName() throws Exception {
        when(service.businessInfo()).thenReturn(new BusinessInfoResponse("Gimnasio Ficticio"));

        mvc.perform(get("/api/business-info").with(user(new AuthUserDetails(8L, "recepcion", "x", "RECEPTIONIST", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeName").value("Gimnasio Ficticio"))
                .andExpect(jsonPath("$.legalName").doesNotExist())
                .andExpect(jsonPath("$.taxId").doesNotExist());
        mvc.perform(get("/api/business-info").with(user(new AuthUserDetails(7L, "admin", "x", "ADMIN", true))))
                .andExpect(status().isOk());
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/business-info")).andExpect(status().isUnauthorized());
    }
}
