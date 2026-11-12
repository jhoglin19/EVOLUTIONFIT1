package com.evolutionfit.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Regla de CLAUDE.md: la seguridad se valida siempre en el backend con @PreAuthorize. Falla si algún endpoint de un
 * @RestController no tiene @PreAuthorize en el método ni en la clase.
 *
 * Única excepción: el inicio de sesión, que es público por definición (y tiene su propio límite de intentos).
 */
class EndpointAuthorizationTest {

    private static final Set<String> PUBLIC_ENDPOINTS = Set.of("AuthController#login");

    @Test
    void everyEndpointDeclaresWhoCanCallIt() throws ClassNotFoundException {
        List<Class<?>> controllers = controllers("com.evolutionfit");
        List<String> endpoints = new ArrayList<>();
        List<String> unprotected = new ArrayList<>();
        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                String name = controller.getSimpleName() + "#" + method.getName();
                endpoints.add(name);
                if (!isProtected(controller, method) && !PUBLIC_ENDPOINTS.contains(name)) {
                    unprotected.add(name);
                }
            }
        }

        // El recorrido sí encuentra los controladores (si el escaneo fallara, la prueba pasaría sin revisar nada).
        assertThat(controllers).hasSizeGreaterThanOrEqualTo(14); // 14 al 12/11/2026
        assertThat(endpoints).contains("AuthController#login", "ClientController#create", "UserController#resetPassword",
                "CashController#today", "E2eClockController#advance");
        assertThat(unprotected).as("endpoints sin @PreAuthorize").isEmpty();
    }

    @Test
    void theCheckDetectsAnUnprotectedEndpoint() {
        assertThat(Arrays.stream(Unprotected.class.getDeclaredMethods())
                .anyMatch(m -> AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class)
                        && !isProtected(Unprotected.class, m))).isTrue();
    }

    private static boolean isProtected(Class<?> controller, Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(controller, PreAuthorize.class);
    }

    private static List<Class<?>> controllers(String basePackage) throws ClassNotFoundException {
        // Con el perfil e2e activo para que también se revise E2eClockController (@Profile("e2e")).
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("e2e");
        var scanner = new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(basePackage)) {
            Class<?> type = Class.forName(bd.getBeanClassName());
            if (type.getName().contains("Test") || type.getEnclosingClass() != null) {
                continue; // controladores de prueba (como Unprotected) no cuentan
            }
            result.add(type);
        }
        return result;
    }

    /** Controlador de control: debe detectarse como desprotegido. */
    @RestController
    static class Unprotected {
        @GetMapping("/sin-proteccion")
        String open() {
            return "ok";
        }
    }
}
