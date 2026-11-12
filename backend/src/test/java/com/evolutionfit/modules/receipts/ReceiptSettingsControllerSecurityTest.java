package com.evolutionfit.modules.receipts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = ReceiptSettingsController.class,
        properties = "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-seguridad")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(AppProperties.class)
class ReceiptSettingsControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReceiptSettingsService service;
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

    private static MockMultipartFile file(int size) {
        byte[] content = new byte[size];
        content[0] = (byte) 0x89;
        return new MockMultipartFile("file", "logo.png", "image/png", content);
    }

    @Test
    void receptionCannotSeeOrChangeTheSettings() throws Exception {
        mvc.perform(get("/api/settings/receipt").with(receptionist())).andExpect(status().isForbidden());
        mvc.perform(put("/api/settings/receipt").with(receptionist())
                .contentType(MediaType.APPLICATION_JSON).content("{\"receiptPrefix\":\"EF-\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(HttpMethod.PUT, "/api/settings/receipt/logo").file(file(10)).with(receptionist()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/settings/receipt/logo").with(receptionist())).andExpect(status().isForbidden());
        mvc.perform(get("/api/settings/receipt/preview.pdf").with(receptionist())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void adminUpdatesTheSettings() throws Exception {
        mvc.perform(put("/api/settings/receipt").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tradeName\":\"Gimnasio Ficticio\",\"receiptPrefix\":\"EF-\",\"initialNumber\":1}"))
                .andExpect(status().isOk());
        verify(service).update(any(), eq(7L));
    }

    @Test
    void invalidPrefixOrInitialNumberIsRejected() throws Exception {
        mvc.perform(put("/api/settings/receipt").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiptPrefix\":\"PREFIJO MUY LARGO\",\"initialNumber\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.receiptPrefix").exists())
                .andExpect(jsonPath("$.fieldErrors.initialNumber").exists());
    }

    @Test
    void logoOver500KbIsRejectedWithAClearMessage() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/settings/receipt/logo").file(file(600 * 1024)).with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El logo supera el tamaño máximo permitido de 500 KB (el archivo pesa 600 KB)"));
        verifyNoInteractions(service);
    }

    @Test
    void adminCanUploadALogoAndPreview() throws Exception {
        when(service.previewPdf()).thenReturn(new byte[] {'%', 'P', 'D', 'F'});
        mvc.perform(multipart(HttpMethod.PUT, "/api/settings/receipt/logo").file(file(1000)).with(admin()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/settings/receipt/preview.pdf").with(admin())).andExpect(status().isOk());
        verify(service).uploadLogo(any(), eq(7L));
    }
}
