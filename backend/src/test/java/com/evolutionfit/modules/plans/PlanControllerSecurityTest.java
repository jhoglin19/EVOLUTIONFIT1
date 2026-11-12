package com.evolutionfit.modules.plans;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = PlanController.class,
        properties = "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-seguridad")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(AppProperties.class)
class PlanControllerSecurityTest {

    private static final String VALID_BODY = """
            {"name":"Mensual","durationDays":30,"price":90000}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PlanService planService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AuthUserDetailsService userDetailsService;

    private static RequestPostProcessor admin() {
        return user(new AuthUserDetails(7L, "admin", "x", "ADMIN", true));
    }

    private static RequestPostProcessor receptionist() {
        return user(new AuthUserDetails(8L, "recepcion", "x", "RECEPTIONIST", true));
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/plans")).andExpect(status().isUnauthorized());
    }

    @Test
    void receptionistCanRead() throws Exception {
        when(planService.list(any(), any(), eq(0), eq(20))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/plans").with(receptionist())).andExpect(status().isOk());
        mvc.perform(get("/api/plans/1").with(receptionist())).andExpect(status().isOk());
    }

    @Test
    void receptionistCannotWrite() throws Exception {
        mvc.perform(post("/api/plans").with(receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/plans/1").with(receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/plans/1/status").with(receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planService);
    }

    @Test
    void adminCanWriteAndIsIdentifiedForTheAudit() throws Exception {
        mvc.perform(post("/api/plans").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/plans/1").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/plans/1/status").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());

        verify(planService).create(any(), eq(7L));
        verify(planService).update(eq(1L), any(), eq(7L));
        verify(planService).setActive(1L, false, 7L);
    }
}
