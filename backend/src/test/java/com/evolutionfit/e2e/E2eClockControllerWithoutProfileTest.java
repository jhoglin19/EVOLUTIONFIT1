package com.evolutionfit.e2e;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Sin el perfil e2e, el controlador del reloj no se registra: el endpoint no existe (404), ni para el ADMIN. */
@WebMvcTest(controllers = E2eClockController.class,
        properties = "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-seguridad")
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@EnableConfigurationProperties(AppProperties.class)
class E2eClockControllerWithoutProfileTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ApplicationContext context;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private AuthUserDetailsService userDetailsService;

    @Test
    void theClockEndpointDoesNotExist() throws Exception {
        var admin = user(new AuthUserDetails(7L, "admin", "x", "ADMIN", true));
        mvc.perform(get("/api/e2e/clock").with(admin)).andExpect(status().isNotFound());
        mvc.perform(post("/api/e2e/clock/advance").with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"2030-01-01T22:01:00\"}")).andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(context.getBeanNamesForType(E2eClockController.class)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(context.getBeanNamesForType(E2eClock.class)).isEmpty();
    }
}
