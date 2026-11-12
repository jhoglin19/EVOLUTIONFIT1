package com.evolutionfit.e2e;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;

import com.evolutionfit.config.AppProperties;
import com.evolutionfit.security.AuthUserDetails;
import com.evolutionfit.security.AuthUserDetailsService;
import com.evolutionfit.security.JwtService;
import com.evolutionfit.security.RestAccessDeniedHandler;
import com.evolutionfit.security.RestAuthenticationEntryPoint;
import com.evolutionfit.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Con el perfil e2e el endpoint existe, pero solo para el ADMIN. */
@WebMvcTest(controllers = E2eClockController.class,
        properties = "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-seguridad")
@ActiveProfiles("e2e")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(AppProperties.class)
class E2eClockControllerWithProfileTest {

    private static final String ADVANCE = "{\"to\":\"2030-01-01T22:01:00\"}";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private E2eClock clock;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthUserDetailsService userDetailsService;

    @Test
    void onlyTheAdminReadsAndAdvancesTheClock() throws Exception {
        when(clock.offset()).thenReturn(Duration.ZERO);
        when(clock.instant()).thenReturn(java.time.Instant.parse("2030-01-02T03:01:00Z"));
        when(clock.getZone()).thenReturn(com.evolutionfit.config.TimeConfig.GYM_ZONE);
        var admin = user(new AuthUserDetails(7L, "admin", "x", "ADMIN", true));
        var reception = user(new AuthUserDetails(8L, "recepcion", "x", "RECEPTIONIST", true));

        mvc.perform(get("/api/e2e/clock")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/e2e/clock").with(reception)).andExpect(status().isForbidden());
        mvc.perform(post("/api/e2e/clock/advance").with(reception).contentType(MediaType.APPLICATION_JSON).content(ADVANCE))
                .andExpect(status().isForbidden());
        verify(clock, never()).advanceTo(any());

        mvc.perform(get("/api/e2e/clock").with(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/e2e/clock/advance").with(admin).contentType(MediaType.APPLICATION_JSON).content(ADVANCE))
                .andExpect(status().isOk());
        verify(clock).advanceTo(LocalDateTime.parse("2030-01-01T22:01:00"));
    }
}
