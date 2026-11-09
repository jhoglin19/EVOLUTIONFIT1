package com.evolutionfit.modules.clients.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.evolutionfit.modules.clients.Client;

public record ClientResponse(Long id,
                             String documentType,
                             String documentNumber,
                             String firstName,
                             String lastName,
                             String email,
                             String phone,
                             LocalDate birthDate,
                             String address,
                             String notes,
                             boolean active,
                             LocalDateTime createdAt) {

    public static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getDocumentType(),
                client.getDocumentNumber(),
                client.getFirstName(),
                client.getLastName(),
                client.getEmail(),
                client.getPhone(),
                client.getBirthDate(),
                client.getAddress(),
                client.getNotes(),
                client.isActive(),
                client.getCreatedAt());
    }
}
