package com.evolutionfit.modules.receipts;

import com.evolutionfit.modules.receipts.dto.BusinessInfoResponse;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo lectura y solo el nombre comercial: el resto de la configuración sigue siendo de ADMIN. */
@RestController
@RequestMapping("/api/business-info")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
@Tag(name = "Datos del gimnasio", description = "Nombre comercial del gimnasio (para el mensaje de WhatsApp)")
public class BusinessInfoController {

    private final ReceiptSettingsService service;

    public BusinessInfoController(ReceiptSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public BusinessInfoResponse get() {
        return service.businessInfo();
    }
}
