package com.evolutionfit.modules.clients;

import java.util.Map;

import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.clients.dto.ClientRequest;
import com.evolutionfit.modules.clients.dto.ClientResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String AUDIT_ENTITY = "CLIENT";
    private static final String DUPLICATE_DOCUMENT = "Ya existe un cliente con ese número de documento";

    private final ClientRepository clientRepository;
    private final AuditService auditService;

    public ClientService(ClientRepository clientRepository, AuditService auditService) {
        this.clientRepository = clientRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<ClientResponse> list(String query, Boolean active, int page, int size) {
        String term = query == null ? "" : query.trim();
        String normalized = ClientDocumentRules.normalize(term);
        // Un término hecho solo de separadores quedaría vacío y coincidiría con todos los documentos.
        String document = normalized.isEmpty() ? term : normalized;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort sort = Sort.by("lastName").ascending().and(Sort.by("firstName").ascending());
        return clientRepository.search(term, document, active, PageRequest.of(safePage, safeSize, sort))
                .map(ClientResponse::from);
    }

    @Transactional(readOnly = true)
    public ClientResponse get(Long id) {
        return ClientResponse.from(findOrThrow(id));
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        String number = validatedNumber(request);
        if (clientRepository.existsByDocumentNumber(number)) {
            throw new ConflictException(DUPLICATE_DOCUMENT);
        }

        Client client = new Client();
        client.setActive(true);
        apply(client, request, number);
        return ClientResponse.from(clientRepository.save(client));
    }

    @Transactional
    public ClientResponse update(Long id, ClientRequest request) {
        Client client = findOrThrow(id);
        String number = validatedNumber(request);
        if (clientRepository.existsByDocumentNumberAndIdNot(number, id)) {
            throw new ConflictException(DUPLICATE_DOCUMENT);
        }

        apply(client, request, number);
        return ClientResponse.from(clientRepository.save(client));
    }

    @Transactional
    public ClientResponse setActive(Long id, boolean active, Long currentUserId) {
        Client client = findOrThrow(id);
        if (client.isActive() == active) {
            return ClientResponse.from(client);
        }

        client.setActive(active);
        Client saved = clientRepository.save(client);
        auditService.record(AUDIT_ENTITY, id, AuditService.ACTION_STATUS_CHANGE,
                Map.of("active", !active), Map.of("active", active), currentUserId);
        return ClientResponse.from(saved);
    }

    // ---- helpers ----

    private Client findOrThrow(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
    }

    private static String validatedNumber(ClientRequest request) {
        String number = ClientDocumentRules.normalize(request.documentNumber());
        ClientDocumentRules.requireValidFormat(request.documentType(), number);
        return number;
    }

    private static void apply(Client client, ClientRequest request, String number) {
        client.setDocumentType(request.documentType());
        client.setDocumentNumber(number);
        client.setFirstName(request.firstName().trim());
        client.setLastName(request.lastName().trim());
        client.setEmail(blankToNull(request.email()));
        client.setPhone(blankToNull(request.phone()));
        client.setBirthDate(request.birthDate());
        client.setAddress(blankToNull(request.address()));
        client.setNotes(blankToNull(request.notes()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
