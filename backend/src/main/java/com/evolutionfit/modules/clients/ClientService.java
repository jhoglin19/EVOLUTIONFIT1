package com.evolutionfit.modules.clients;

import java.util.LinkedHashMap;
import java.util.Map;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.common.validation.ColombianPhones;
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

    /**
     * Borrado permanente (docs/REGLAS_NEGOCIO.md, sección 7): solo si el cliente nunca tuvo membresías
     * (de ningún estado), pagos ni accesos. Con historial solo se desactiva. El cliente se bloquea primero
     * (FOR UPDATE): una inscripción simultánea espera y, si se crea antes, el borrado se rechaza. La
     * auditoría guarda una copia de los datos borrados.
     */
    @Transactional
    public void delete(Long id, Long currentUserId) {
        Client client = clientRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        long memberships = clientRepository.countMemberships(id);
        long payments = clientRepository.countPayments(id);
        long accesses = clientRepository.countAccessLogs(id);
        if (memberships + payments + accesses > 0) {
            throw new ConflictException("No se puede eliminar: el cliente tiene historial (" + memberships
                    + " membresía(s), " + payments + " pago(s), " + accesses + " acceso(s)). Puedes desactivarlo.");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("documentType", client.getDocumentType());
        snapshot.put("documentNumber", client.getDocumentNumber());
        snapshot.put("firstName", client.getFirstName());
        snapshot.put("lastName", client.getLastName());
        snapshot.put("phone", client.getPhone());
        snapshot.put("email", client.getEmail());
        snapshot.put("active", client.isActive());
        snapshot.put("createdAt", client.getCreatedAt() == null ? null : client.getCreatedAt().toString());
        clientRepository.delete(client);
        clientRepository.flush(); // si la base lo rechaza (llave foránea), falla aquí y no queda auditoría
        auditService.record(AUDIT_ENTITY, id, AuditService.ACTION_DELETE, snapshot, null, currentUserId);
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
        // Obligatorio al crear y al editar; se guarda en el formato del sistema (10 dígitos). El controlador
        // ya lo valida, pero el asistente de inscripción también llega aquí.
        if (request.phone() == null || request.phone().isBlank()) {
            throw new BusinessException("El celular es obligatorio");
        }
        String phone = ColombianPhones.normalize(request.phone());
        if (phone == null) {
            throw new BusinessException(ColombianPhones.INVALID_MESSAGE);
        }
        client.setPhone(phone);
        client.setBirthDate(request.birthDate());
        client.setAddress(blankToNull(request.address()));
        client.setNotes(blankToNull(request.notes()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
