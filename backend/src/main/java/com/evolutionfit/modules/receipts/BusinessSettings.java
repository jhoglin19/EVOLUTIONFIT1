package com.evolutionfit.modules.receipts;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.UpdateTimestamp;

/**
 * Datos del gimnasio para el encabezado del comprobante. Una sola fila (id = 1), creada vacía por V4 y
 * editada por el ADMIN desde la pantalla de configuración. Ningún dato real va en el código.
 */
@Entity
@Table(name = "business_settings")
@Getter
@Setter
@NoArgsConstructor
public class BusinessSettings {

    public static final short SINGLE_ID = 1;

    @Id
    private Short id;

    /** Nombre comercial. */
    @Column(name = "trade_name", length = 150)
    private String tradeName;

    /** Nombre del titular o razón social. */
    @Column(name = "legal_name", length = 150)
    private String legalName;

    /** NIT como texto libre, con su dígito de verificación. */
    @Column(name = "tax_id", length = 30)
    private String taxId;

    /** Matrícula mercantil. */
    @Column(name = "commercial_registration", length = 50)
    private String commercialRegistration;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "logo", columnDefinition = "mediumblob")
    private byte[] logo;

    @Column(name = "logo_content_type", length = 30)
    private String logoContentType;

    @Column(name = "receipt_prefix", nullable = false, length = 10)
    private String receiptPrefix;

    @Column(name = "updated_by")
    private Long updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
