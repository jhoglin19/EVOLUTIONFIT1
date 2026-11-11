package com.evolutionfit.modules.cash;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara el cierre automático al arrancar (para ponerse al día si el backend estuvo apagado a las 10 p.m.)
 * y cada minuto. Si ya está al día no hace nada, así el cierre ocurre a lo sumo un minuto después del corte
 * aunque se cambie la hora en app.cash.closing-time, y un fallo se reintenta solo.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.cash", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
class CashClosingJob {

    private final CashClosingService closingService;

    CashClosingJob(CashClosingService closingService) {
        this.closingService = closingService;
    }

    @Scheduled(cron = "5 * * * * *", zone = "America/Bogota")
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        try {
            int closed = closingService.closeDueDays();
            if (closed > 0) {
                log.info("Cierre automático de caja: {} día(s) cerrado(s)", closed);
            }
        } catch (RuntimeException e) {
            log.error("No se pudo hacer el cierre automático de caja; se reintenta en un minuto", e);
        }
    }
}
