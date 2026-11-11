package com.evolutionfit.modules.clients;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.evolutionfit.common.exception.ConflictException;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = ClientController.class,
        properties = "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-seguridad")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(AppProperties.class)
class ClientControllerSecurityTest {

    private static final String VALID_BODY = """
            {"documentType":"CC","documentNumber":"123","firstName":"Ana","lastName":"Gómez","phone":"+57 300 123 4567"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ClientService clientService;
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
        mvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
    }

    @Test
    void receptionistCanListCreateAndUpdate() throws Exception {
        when(clientService.list(any(), any(), eq(0), eq(20))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/clients").with(receptionist())).andExpect(status().isOk());
        mvc.perform(post("/api/clients").with(receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/clients/1").with(receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void receptionistCannotChangeStatus() throws Exception {
        mvc.perform(patch("/api/clients/1/status").with(receptionist())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        verify(clientService, never()).setActive(any(), eq(false), any());
    }

    @Test
    void adminChangesStatusAndIsIdentifiedForTheAudit() throws Exception {
        mvc.perform(patch("/api/clients/1/status").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());

        verify(clientService).setActive(1L, false, 7L);
    }

    @Test
    void invalidBodyReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/clients").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"documentType\":\"NIT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.documentType").exists())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
    }

    @Test
    void creatingOrEditingAClientWithoutAValidMobileIsRejected() throws Exception {
        String withoutPhone = "{\"documentType\":\"CC\",\"documentNumber\":\"123\",\"firstName\":\"Ana\",\"lastName\":\"Gómez\"}";
        String landline = withoutPhone.replace("}", ",\"phone\":\"6015551234\"}");

        mvc.perform(post("/api/clients").with(admin()).contentType(MediaType.APPLICATION_JSON).content(withoutPhone))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").value("El celular es obligatorio"));
        mvc.perform(put("/api/clients/1").with(admin()).contentType(MediaType.APPLICATION_JSON).content(landline))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
        verifyNoInteractions(clientService);
    }

    @Test
    void duplicateDocumentReturnsConflict() throws Exception {
        when(clientService.create(any())).thenThrow(new ConflictException("Ya existe"));

        mvc.perform(post("/api/clients").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe"));
    }

    @Test
    void onlyTheAdminDeletesAClient() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/clients/1")
                .with(receptionist())).andExpect(status().isForbidden());
        org.mockito.Mockito.verify(clientService, org.mockito.Mockito.never())
                .delete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/clients/1")
                .with(admin())).andExpect(status().isNoContent());
        org.mockito.Mockito.verify(clientService).delete(1L, 7L);
    }
}
