package com.evolutionfit.modules.clients;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import com.evolutionfit.modules.clients.dto.ClientRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClientRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(ClientRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static ClientRequest withDocumentType(String type) {
        return new ClientRequest(type, "123", "Ana", "Gómez", null, null, null, null, null);
    }

    private static ClientRequest withPhone(String phone) {
        return new ClientRequest("CC", "123", "Ana", "Gómez", null, phone, null, null, null);
    }

    @Test
    void completeRequestIsValid() {
        ClientRequest request = new ClientRequest("CC", "123", "Ana", "Gómez", "ana@correo.com",
                "+57 (300) 123-4567", LocalDate.now(), "Calle 1", "Nota");

        assertThat(invalidFields(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CC", "TI", "CE", "PA", "PPT"})
    void acceptedDocumentTypes(String type) {
        assertThat(invalidFields(withDocumentType(type))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NIT", "cc", "CC ", ""})
    void rejectedDocumentTypes(String type) {
        assertThat(invalidFields(withDocumentType(type))).contains("documentType");
    }

    @Test
    void requiredFieldsAreEnforced() {
        ClientRequest request = new ClientRequest(null, " ", "", null, null, null, null, null, null);

        assertThat(invalidFields(request))
                .contains("documentType", "documentNumber", "firstName", "lastName");
    }

    @Test
    void lengthLimitsMatchTheColumns() {
        ClientRequest request = new ClientRequest("CC", "1".repeat(31), "a".repeat(81), "a".repeat(81),
                "a".repeat(150) + "@correo.com", null, null, "a".repeat(201), "a".repeat(2001));

        assertThat(invalidFields(request)).containsExactlyInAnyOrder(
                "documentNumber", "firstName", "lastName", "email", "address", "notes");
    }

    @Test
    void birthDateCannotBeInTheFuture() {
        ClientRequest request = new ClientRequest("CC", "123", "Ana", "Gómez", null, null,
                LocalDate.now().plusDays(1), null, null);

        assertThat(invalidFields(request)).containsExactly("birthDate");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "abc1234", "300#1234567"})
    void rejectedPhones(String phone) {
        assertThat(invalidFields(withPhone(phone))).containsExactly("phone");
    }

    @Test
    void emptyPhoneIsAllowedBecauseItIsOptional() {
        assertThat(invalidFields(withPhone(""))).isEmpty();
    }
}
