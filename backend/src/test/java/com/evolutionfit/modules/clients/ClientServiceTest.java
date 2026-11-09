package com.evolutionfit.modules.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.clients.dto.ClientRequest;
import com.evolutionfit.modules.clients.dto.ClientResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private AuditService auditService;

    private ClientService service;

    @BeforeEach
    void setUp() {
        service = new ClientService(clientRepository, auditService);
    }

    private static ClientRequest request(String type, String number) {
        return new ClientRequest(type, number, " Ana ", " Gómez ", " ", "", null, "  ", null);
    }

    private static Client existing(Long id, boolean active) {
        Client client = new Client();
        client.setId(id);
        client.setDocumentType("CC");
        client.setDocumentNumber("1234567");
        client.setFirstName("Ana");
        client.setLastName("Gómez");
        client.setActive(active);
        return client;
    }

    private void saveReturnsArgument() {
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createNormalizesDataAndStartsActive() {
        when(clientRepository.existsByDocumentNumber("1234567")).thenReturn(false);
        saveReturnsArgument();

        ClientResponse response = service.create(request("CC", " 1.234-567 "));

        assertThat(response.documentNumber()).isEqualTo("1234567");
        assertThat(response.firstName()).isEqualTo("Ana");
        assertThat(response.lastName()).isEqualTo("Gómez");
        assertThat(response.email()).isNull();
        assertThat(response.phone()).isNull();
        assertThat(response.address()).isNull();
        assertThat(response.active()).isTrue();
    }

    @Test
    void createRejectsDuplicateComparingTheNormalizedNumber() {
        when(clientRepository.existsByDocumentNumber("1234567")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("CC", "1.234.567")))
                .isInstanceOf(ConflictException.class);

        verify(clientRepository, never()).save(any());
    }

    @Test
    void numericTypesRejectLetters() {
        assertThatThrownBy(() -> service.create(request("CC", "TEST-001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dígitos");
        assertThatThrownBy(() -> service.create(request("TI", "12A")))
                .isInstanceOf(BusinessException.class);

        verify(clientRepository, never()).save(any());
    }

    @Test
    void passportAcceptsLettersAndIsStoredInUpperCase() {
        when(clientRepository.existsByDocumentNumber("AB123")).thenReturn(false);
        saveReturnsArgument();

        assertThat(service.create(request("PA", "ab-123")).documentNumber()).isEqualTo("AB123");
    }

    @Test
    void numberMadeOnlyOfSeparatorsIsRejected() {
        assertThatThrownBy(() -> service.create(request("PA", "- . -")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateAllowsKeepingItsOwnDocument() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing(1L, true)));
        when(clientRepository.existsByDocumentNumberAndIdNot("1234567", 1L)).thenReturn(false);
        saveReturnsArgument();

        assertThat(service.update(1L, request("CC", "1234567")).documentNumber()).isEqualTo("1234567");
    }

    @Test
    void updateRejectsTheDocumentOfAnotherClient() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing(1L, true)));
        when(clientRepository.existsByDocumentNumberAndIdNot("999", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("CC", "999")))
                .isInstanceOf(ConflictException.class);

        verify(clientRepository, never()).save(any());
    }

    @Test
    void unknownClientIsNotFound() {
        when(clientRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivatingRecordsOnlyTheStatusChangeWithItsAuthor() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing(1L, true)));
        saveReturnsArgument();

        assertThat(service.setActive(1L, false, 7L).active()).isFalse();

        verify(auditService).record("CLIENT", 1L, AuditService.ACTION_STATUS_CHANGE,
                Map.of("active", true), Map.of("active", false), 7L);
    }

    @Test
    void settingTheCurrentStatusChangesNothing() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(existing(1L, true)));

        assertThat(service.setActive(1L, true, 7L).active()).isTrue();

        verify(clientRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void searchNormalizesTheDocumentTermAndLimitsThePageSize() {
        when(clientRepository.search(eq("1.234"), eq("1234"), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(existing(1L, true))));

        assertThat(service.list(" 1.234 ", true, -3, 5000).getContent()).hasSize(1);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(clientRepository).search(eq("1.234"), eq("1234"), eq(true), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void searchMadeOnlyOfSeparatorsDoesNotBecomeAnEmptyDocumentTerm() {
        when(clientRepository.search(eq("-"), eq("-"), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.list("-", null, 0, 10).getContent()).isEmpty();
    }
}
