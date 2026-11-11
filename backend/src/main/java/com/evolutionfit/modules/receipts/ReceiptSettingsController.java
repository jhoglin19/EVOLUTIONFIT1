package com.evolutionfit.modules.receipts;

import java.io.IOException;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.modules.receipts.dto.ReceiptSettingsRequest;
import com.evolutionfit.modules.receipts.dto.ReceiptSettingsResponse;
import com.evolutionfit.security.AuthUserDetails;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/settings/receipt")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Configuración del comprobante", description = "Datos del gimnasio, prefijo, número inicial y logo (solo ADMIN)")
public class ReceiptSettingsController {

    private final ReceiptSettingsService service;

    public ReceiptSettingsController(ReceiptSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ReceiptSettingsResponse get() {
        return service.get();
    }

    @PutMapping
    public ReceiptSettingsResponse update(@Valid @RequestBody ReceiptSettingsRequest request,
                                          @AuthenticationPrincipal AuthUserDetails current) {
        return service.update(request, current.getId());
    }

    @PutMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReceiptSettingsResponse uploadLogo(@RequestParam("file") MultipartFile file,
                                              @AuthenticationPrincipal AuthUserDetails current) {
        if (file.getSize() > ReceiptSettingsService.MAX_LOGO_BYTES) {
            throw new BusinessException(ReceiptSettingsService.LOGO_TOO_LARGE
                    + " (el archivo pesa " + (file.getSize() / 1024) + " KB)");
        }
        try {
            return service.uploadLogo(file.getBytes(), current.getId());
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el archivo del logo");
        }
    }

    @DeleteMapping("/logo")
    public ReceiptSettingsResponse deleteLogo(@AuthenticationPrincipal AuthUserDetails current) {
        return service.deleteLogo(current.getId());
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        ReceiptSettingsService.Logo logo = service.logo();
        if (logo == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType())).body(logo.content());
    }

    @GetMapping(value = "/preview.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> preview() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"vista-previa-comprobante.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.previewPdf());
    }
}
