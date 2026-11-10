package com.evolutionfit.modules.memberships;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = MembershipController.class,
        properties = "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-seguridad")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(AppProperties.class)
class MembershipControllerSecurityTest {

    private static final String SALE = """
            {"clientId":1,"planId":2}
            """;
    private static final String MIGRATION = """
            {"clientId":1,"planId":2,"startDate":"2026-10-15","endDate":"2026-11-25","migrationNote":"ID 9"}
            """;
    private static final String REASON = """
            {"reason":"Cambio de plan"}
            """;
    private static final String FREEZE = """
            {"startDate":"2026-11-09","days":5,"reason":"Viaje"}
            """;
    private static final String EXTEND = """
            {"totalDays":10,"reason":"Incapacidad"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MembershipService membershipService;
    @MockBean
    private MembershipFreezeService freezeService;
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

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/memberships")).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/memberships"), SALE)).andExpect(status().isUnauthorized());
    }

    @Test
    void receptionCanQuerySellAndRegisterFreezes() throws Exception {
        when(membershipService.list(any(), anyBoolean(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/memberships").param("state", "ACTIVE").param("expiringSoon", "true")
                .with(receptionist())).andExpect(status().isOk());
        mvc.perform(get("/api/clients/1/memberships").with(receptionist())).andExpect(status().isOk());
        mvc.perform(get("/api/clients/1/memberships/current").with(receptionist())).andExpect(status().isOk());
        mvc.perform(json(post("/api/memberships"), SALE).with(receptionist())).andExpect(status().isCreated());
        mvc.perform(json(post("/api/memberships/10/freezes"), FREEZE).with(receptionist()))
                .andExpect(status().isCreated());

        verify(membershipService).create(any(), eq(8L));
        verify(freezeService).create(eq(10L), any(), eq(8L));
    }

    @Test
    void receptionCannotCancelMigrateExtendOrCancelFreezes() throws Exception {
        mvc.perform(json(post("/api/memberships/migrated"), MIGRATION).with(receptionist()))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/memberships/10/cancel"), REASON).with(receptionist()))
                .andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/freezes/3/extend"), EXTEND).with(receptionist()))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/freezes/3/cancel"), REASON).with(receptionist()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(membershipService, freezeService);
    }

    @Test
    void adminCanDoEverythingAndIsIdentifiedForTheAudit() throws Exception {
        mvc.perform(json(post("/api/memberships/migrated"), MIGRATION).with(admin())).andExpect(status().isCreated());
        mvc.perform(json(post("/api/memberships/10/cancel"), REASON).with(admin())).andExpect(status().isOk());
        mvc.perform(json(patch("/api/freezes/3/extend"), EXTEND).with(admin())).andExpect(status().isOk());
        mvc.perform(json(post("/api/freezes/3/cancel"), REASON).with(admin())).andExpect(status().isOk());

        verify(membershipService).createMigrated(any(), eq(7L));
        verify(membershipService).cancel(10L, "Cambio de plan", 7L);
        verify(freezeService).extend(eq(3L), any(), eq(7L));
        verify(freezeService).cancel(3L, "Cambio de plan", 7L);
    }

    @Test
    void reasonIsValidatedBeforeReachingTheService() throws Exception {
        mvc.perform(json(post("/api/memberships/10/cancel"), "{\"reason\":\" \"}").with(admin()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(membershipService);
    }
}
