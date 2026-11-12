package com.evolutionfit.modules.cash;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Recepción: día en curso, retiros y sus arqueos pendientes. El resto de la caja es solo del ADMIN. */
@WebMvcTest(controllers = CashController.class,
        properties = "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-seguridad")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(AppProperties.class)
class CashControllerSecurityTest {

    private static final String WITHDRAWAL = """
            {"requestId":"3f1c2a9e-1b2c-4d5e-8f90-123456789abc","amount":5000,"category":"SUPPLIES","concept":"Servilletas"}
            """;
    private static final String COUNT = """
            {"countedCash":140000,"note":"","expectedAdjustedCash":140000}
            """;
    private static final String REASON = """
            {"reason":"Error de digitación"}
            """;
    private static final String NOTE = """
            {"note":"Revisado con recepción"}
            """;
    private static final String SETTINGS = """
            {"openingBase":50000,"startDate":null,"openDays":["MONDAY","TUESDAY"]}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CashService cashService;
    @MockitoBean
    private CashCountService countService;
    @MockitoBean
    private CashReportService reportService;
    @MockitoBean
    private GymCalendarService calendarService;
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

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return b.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void withoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/cash/today")).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/cash/withdrawals"), WITHDRAWAL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(cashService, countService, reportService);
    }

    @Test
    void receptionSeesTheCurrentDayRegistersWithdrawalsAndCountsPendingDays() throws Exception {
        when(countService.pending()).thenReturn(List.of());

        mvc.perform(get("/api/cash/today").with(receptionist())).andExpect(status().isOk());
        mvc.perform(json(post("/api/cash/withdrawals"), WITHDRAWAL).with(receptionist())).andExpect(status().isCreated());
        mvc.perform(get("/api/cash/closings/pending").with(receptionist())).andExpect(status().isOk());
        mvc.perform(get("/api/cash/closings/3/count-preview").with(receptionist())).andExpect(status().isOk());
        mvc.perform(json(post("/api/cash/closings/3/count"), COUNT).with(receptionist())).andExpect(status().isOk());

        verify(countService).count(eq(3L), any(), any());
    }

    @Test
    void receptionCannotSeeHistoryReportsSettingsNorVoidOrReview() throws Exception {
        mvc.perform(get("/api/cash/withdrawals").with(receptionist())).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/cash/withdrawals/1/void"), REASON).with(receptionist())).andExpect(status().isForbidden());
        mvc.perform(get("/api/cash/closings").with(receptionist())).andExpect(status().isForbidden());
        mvc.perform(get("/api/cash/closings/3").with(receptionist())).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/cash/closings/3/review"), NOTE).with(receptionist())).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/cash/closings/3/reopen-count"), REASON).with(receptionist()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/cash/reports/monthly?month=2026-11").with(receptionist())).andExpect(status().isForbidden());
        mvc.perform(get("/api/cash/settings").with(receptionist())).andExpect(status().isForbidden());
        mvc.perform(json(put("/api/cash/settings"), SETTINGS).with(receptionist())).andExpect(status().isForbidden());
        mvc.perform(get("/api/cash/calendar?year=2026").with(receptionist())).andExpect(status().isForbidden());
        mvc.perform(json(put("/api/cash/calendar/2026-12-08"), "{\"open\":true}").with(receptionist()))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/cash/calendar/2026-12-08")
                .with(receptionist())).andExpect(status().isForbidden());

        verifyNoInteractions(reportService);
        verify(cashService, org.mockito.Mockito.never()).voidWithdrawal(any(), any(), any());
        verify(cashService, org.mockito.Mockito.never()).updateSettings(any(), any());
    }

    @Test
    void adminSeesAndReviewsEverything() throws Exception {
        when(reportService.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/cash/withdrawals").with(admin())).andExpect(status().isOk());
        mvc.perform(json(post("/api/cash/withdrawals/1/void"), REASON).with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/cash/closings").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/cash/closings/3").with(admin())).andExpect(status().isOk());
        mvc.perform(json(post("/api/cash/closings/3/review"), NOTE).with(admin())).andExpect(status().isOk());
        mvc.perform(json(post("/api/cash/closings/3/reopen-count"), REASON).with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/cash/reports/monthly?month=2026-11").with(admin())).andExpect(status().isOk());
        mvc.perform(json(put("/api/cash/settings"), SETTINGS).with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/cash/calendar?year=2026").with(admin())).andExpect(status().isOk());
        mvc.perform(json(put("/api/cash/calendar/2026-12-08"), "{\"open\":true,\"note\":\"Horario especial\"}")
                .with(admin())).andExpect(status().isOk());
        mvc.perform(json(put("/api/cash/calendar/2026-12-08"), "{}").with(admin())).andExpect(status().isBadRequest());
        verify(calendarService).set(java.time.LocalDate.of(2026, 12, 8), true, "Horario especial", 7L);

        verify(countService).review(3L, "Revisado con recepción", 7L);
        verify(cashService).voidWithdrawal(1L, "Error de digitación", 7L);
    }

    @Test
    void invalidRequestsAreRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(json(post("/api/cash/withdrawals"), """
                {"requestId":"x","amount":0,"category":"SUPPLIES","concept":" "}""").with(receptionist()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/cash/closings/3/review"), "{\"note\":\"\"}").with(admin()))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/cash/withdrawals/1/void"), "{\"reason\":\"\"}").with(admin()))
                .andExpect(status().isBadRequest());
        verify(cashService, org.mockito.Mockito.never()).registerWithdrawal(any(), any());
    }
}
