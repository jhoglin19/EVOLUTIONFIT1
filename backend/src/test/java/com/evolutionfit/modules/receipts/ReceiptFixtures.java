package com.evolutionfit.modules.receipts;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Datos ficticios: ningún dato real del gimnasio va en el código ni en las pruebas. */
final class ReceiptFixtures {

    private ReceiptFixtures() {
    }

    static BusinessSettings settings(String prefix) {
        BusinessSettings s = new BusinessSettings();
        s.setId(BusinessSettings.SINGLE_ID);
        s.setTradeName("Gimnasio Ficticio");
        s.setLegalName("Empresa Ficticia S.A.S.");
        s.setTaxId("000.000.000-0");
        s.setCommercialRegistration("00000-00");
        s.setAddress("Calle Ficticia 1 # 2-3");
        s.setCity("Ciudad Ficticia");
        s.setPhone("000 000 0000");
        s.setEmail("contacto@ejemplo.invalid");
        s.setReceiptPrefix(prefix);
        return s;
    }

    static ReceiptContent content(String discount) {
        BigDecimal d = new BigDecimal(discount);
        BigDecimal list = new BigDecimal("90000");
        return new ReceiptContent("Cliente Ficticio", "CC", "0000000001",
                "Membresía plan Mensual (30 días) del 10/11/2026 al 09/12/2026", "Mensual",
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 9), list, d,
                d.signum() > 0 ? "Convenio ficticio" : null, list.subtract(d), "Nequi", "REF-0001",
                "Usuario Ficticio", null);
    }
}
