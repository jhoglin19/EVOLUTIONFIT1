package com.evolutionfit.modules.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.evolutionfit.config.AppProperties;
import com.evolutionfit.modules.memberships.ExpirationAlertKind;
import com.evolutionfit.modules.memberships.MembershipAlertService;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Recepción ve el panel operativo y las alertas; los ingresos del panel son solo del ADMIN. */
@WebMvcTest(controllers = {DashboardController.class, AlertController.class},
        properties = "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-seguridad")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(AppProperties.class)
class DashboardControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DashboardService dashboardService;
    @MockBean
    private MembershipAlertService alertService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private AuthUserDetailsService userDetailsService;

    private static RequestPostProcessor admin() {
        return user(new AuthUserDetails(7L, "admin", "x", "ADMIN", true));
    }

    private static RequestPostProcessor receptionist() {
        return user(new AuthUserDetails(8L, "recepcion", "x", "RECEPTIONIST", true));
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/alerts/count")).andExpect(status().isUnauthorized());
        verifyNoInteractions(dashboardService, alertService);
    }

    @Test
    void receptionSeesTheDashboardAndAlertsButNotIncome() throws Exception {
        when(alertService.list(any(), any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/dashboard").with(receptionist())).andExpect(status().isOk());
        mvc.perform(get("/api/alerts/count").with(receptionist())).andExpect(status().isOk());
        mvc.perform(get("/api/alerts/expirations?kind=EXPIRING&q=ana").with(receptionist())).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/admin").with(receptionist())).andExpect(status().isForbidden());

        verify(alertService).list(eq(ExpirationAlertKind.EXPIRING), eq("ana"), eq(0), eq(20));
        verify(dashboardService, org.mockito.Mockito.never()).admin();
    }

    @Test
    void adminSeesIncome() throws Exception {
        mvc.perform(get("/api/dashboard").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/admin").with(admin())).andExpect(status().isOk());
        verify(dashboardService).admin();
    }

    @Test
    void anUnknownAlertKindIsABadRequest() throws Exception {
        mvc.perform(get("/api/alerts/expirations?kind=OTHER").with(receptionist())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/alerts/expirations").with(receptionist())).andExpect(status().isBadRequest());
    }
}
