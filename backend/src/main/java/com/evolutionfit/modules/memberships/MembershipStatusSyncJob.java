package com.evolutionfit.modules.memberships;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantiene al día la columna status (vencidas, congelaciones que empiezan o terminan, programadas que
 * arrancan). Corre cada día a las 00:01 de Bogotá y al arrancar, para cubrir días con el servidor apagado.
 * Si falla no afecta las reglas: todas calculan el estado con las fechas.
 */
@Slf4j
@Component
class MembershipStatusSyncJob {

    private final MembershipService membershipService;

    MembershipStatusSyncJob(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Scheduled(cron = "0 1 0 * * *", zone = "America/Bogota")
    @EventListener(ApplicationReadyEvent.class)
    public void sync() {
        try {
            int changed = membershipService.syncStoredStatuses();
            log.info("Estados de membresías sincronizados: {} cambios", changed);
        } catch (RuntimeException e) {
            log.error("No se pudieron sincronizar los estados de membresías", e);
        }
    }
}
