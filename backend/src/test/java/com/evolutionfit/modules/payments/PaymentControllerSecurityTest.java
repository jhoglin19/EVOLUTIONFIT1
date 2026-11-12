package com.evolutionfit.modules.payments;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.evolutionfit.config.AppProperties;
import com.evolutionfit.modules.receipts.ReceiptService.ReceiptPdf;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = PaymentController.class,
        properties = "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-seguridad")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(AppProperties.class)
class PaymentControllerSecurityTest {

    private static final String PAYMENT = """
            {"requestId":"3f1c2a9e-1b2c-4d5e-8f90-123456789abc","method":"CASH","expectedAmount":90000}
            """;
    private static final String ENROLLMENT = """
            {"clientId":1,"planId":2,"payment":{"requestId":"3f1c2a9e-1b2c-4d5e-8f90-123456789abc",
             "method":"NEQUI","reference":"123","expectedAmount":90000}}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private PaymentCommands commands;
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
        mvc.perform(json(post("/api/enrollments"), ENROLLMENT)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/payments/1/receipt.pdf")).andExpect(status().isUnauthorized());
    }

    @Test
    void receptionCanEnrollChargeQueryAndPrint() throws Exception {
        when(paymentService.list(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));
        when(paymentService.receiptPdf(5L)).thenReturn(new ReceiptPdf("EF-000001", new byte[] {'%', 'P', 'D', 'F'}));

        mvc.perform(json(post("/api/enrollments"), ENROLLMENT).with(receptionist())).andExpect(status().isCreated());
        mvc.perform(json(post("/api/memberships/10/payments"), PAYMENT).with(receptionist()))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/payments").param("method", "CASH").param("from", "2026-11-01").with(receptionist()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/clients/1/payments").with(receptionist())).andExpect(status().isOk());
        mvc.perform(get("/api/payments/5/receipt.pdf").with(receptionist()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"EF-000001.pdf\""));

        verify(commands).enroll(any(), org.mockito.ArgumentMatchers.argThat(u -> u.getId().equals(8L)));
        verify(commands).pay(eq(10L), any(), org.mockito.ArgumentMatchers.argThat(u -> u.getId().equals(8L)));
    }

    @Test
    void receptionCannotVoid() throws Exception {
        mvc.perform(json(post("/api/payments/5/void"), "{\"reason\":\"Error\"}").with(receptionist()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test
    void adminVoidsWithAMandatoryReason() throws Exception {
        mvc.perform(json(post("/api/payments/5/void"), "{\"reason\":\" \"}").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").value("El motivo es obligatorio"));
        mvc.perform(json(post("/api/payments/5/void"), "{\"reason\":\"Error\"}").with(admin()))
                .andExpect(status().isOk());
        verify(paymentService).voidPayment(5L, "Error", 7L);
    }

    @Test
    void invalidPaymentDataIsRejectedBeforeTheService() throws Exception {
        mvc.perform(json(post("/api/memberships/10/payments"), """
                {"requestId":"no-es-uuid","method":"BITCOIN","expectedAmount":-1}
                """).with(receptionist())).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/memberships/10/payments"), """
                {"requestId":"3f1c2a9e-1b2c-4d5e-8f90-123456789abc","expectedAmount":-1}
                """).with(receptionist()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.method").exists())
                .andExpect(jsonPath("$.fieldErrors.expectedAmount").exists());
        verifyNoInteractions(commands);
    }

    @Test
    void theWizardRequiresAMobileOnlyForANewClient() throws Exception {
        String newClientWithoutPhone = """
                {"client":{"documentType":"CC","documentNumber":"1001","firstName":"Ana","lastName":"Pérez"},"planId":2,
                 "payment":{"requestId":"3f1c2a9e-1b2c-4d5e-8f90-123456789abc","method":"CASH","expectedAmount":90000}}
                """;
        mvc.perform(json(post("/api/enrollments"), newClientWithoutPhone).with(receptionist()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['client.phone']").value("El celular es obligatorio"));
        verifyNoInteractions(commands);

        // Un cliente existente (aunque no tenga teléfono) se inscribe por su id, sin enviar sus datos.
        mvc.perform(json(post("/api/enrollments"), ENROLLMENT).with(receptionist())).andExpect(status().isCreated());
    }
}
