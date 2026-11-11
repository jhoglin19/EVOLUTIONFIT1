package com.evolutionfit.e2e;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.Hidden;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo pruebas de punta a punta (perfil e2e, protegido por E2eGuard) y solo ADMIN: consultar y adelantar el
 * reloj, por ejemplo hasta las 10:01 p.m. para probar el cierre automático y el arqueo. Sin el perfil e2e
 * este endpoint no existe (404).
 */
@RestController
@RequestMapping("/api/e2e/clock")
@Profile("e2e")
@PreAuthorize("hasRole('ADMIN')")
@Hidden
public class E2eClockController {

    private final E2eClock clock;

    public E2eClockController(E2eClock clock) {
        this.clock = clock;
    }

    @GetMapping
    public ClockResponse now() {
        return new ClockResponse(LocalDateTime.now(clock), clock.offset().toSeconds());
    }

    @PostMapping("/advance")
    public ClockResponse advance(@Valid @RequestBody AdvanceRequest request) {
        clock.advanceTo(request.to());
        return now();
    }

    public record AdvanceRequest(@NotNull(message = "Indica la hora a la que se adelanta el reloj") LocalDateTime to) {
    }

    public record ClockResponse(LocalDateTime now, long offsetSeconds) {
    }
}
