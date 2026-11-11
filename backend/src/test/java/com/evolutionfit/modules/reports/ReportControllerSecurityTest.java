package com.evolutionfit.modules.reports;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Los reportes y su exportación son solo del ADMIN. */
@WebMvcTest(controllers = ReportController.class,
        properties = "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-seguridad")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(AppProperties.class)
class ReportControllerSecurityTest {

    private static final String RANGE = "?from=2026-11-01&to=2026-11-10";
    private static final String[] REPORTS = {"income", "new-clients", "plans", "voided-payments", "discounts", "expirations"};

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ReportService reportService;
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
        mvc.perform(get("/api/reports/income" + RANGE)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports/income/export" + RANGE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reportService);
    }

    @Test
    void receptionCannotSeeNorExportAnyReport() throws Exception {
        for (String report : REPORTS) {
            mvc.perform(get("/api/reports/" + report + RANGE).with(receptionist())).andExpect(status().isForbidden());
            mvc.perform(get("/api/reports/" + report + "/export" + RANGE).with(receptionist()))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(reportService);
    }

    @Test
    void adminSeesEveryReport() throws Exception {
        for (String report : REPORTS) {
            mvc.perform(get("/api/reports/" + report + RANGE).with(admin())).andExpect(status().isOk());
        }
        verify(reportService).income(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10), null);
    }

    @Test
    void adminExportsCsvAsAnAttachment() throws Exception {
        when(reportService.export(eq("plans"), any(), any(), any(), eq(7L)))
                .thenReturn(new ReportService.Export("plans_2026-11-01_2026-11-10.csv",
                        "﻿Plan;Vendidas\r\n".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(get("/api/reports/plans/export" + RANGE).with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"plans_2026-11-01_2026-11-10.csv\""))
                .andExpect(content().bytes("﻿Plan;Vendidas\r\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void missingDatesAreABadRequest() throws Exception {
        mvc.perform(get("/api/reports/income").with(admin())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/income?from=2026-13-01&to=2026-11-10").with(admin()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reportService);
    }
}
