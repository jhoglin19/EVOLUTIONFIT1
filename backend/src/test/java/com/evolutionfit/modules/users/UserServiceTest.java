package com.evolutionfit.modules.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.modules.roles.Role;
import com.evolutionfit.modules.roles.RoleRepository;
import com.evolutionfit.modules.users.dto.CreateUserRequest;
import com.evolutionfit.modules.users.dto.UpdateUserRequest;
import com.evolutionfit.modules.users.dto.UserResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService service;

    private Role adminRole;
    private Role receptionistRole;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, roleRepository, passwordEncoder);

        adminRole = new Role("ADMIN", "Administrador");
        adminRole.setId(1L);
        receptionistRole = new Role("RECEPTIONIST", "Recepción");
        receptionistRole.setId(2L);
    }

    private User existingUser(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername("usuario" + id);
        user.setFirstName("Nombre");
        user.setLastName("Apellido");
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    @Test
    void createStoresEncodedPasswordTrimmedUsernameAndNullEmailWhenBlank() {
        when(userRepository.existsByUsername("maria")).thenReturn(false);
        when(roleRepository.findByName("RECEPTIONIST")).thenReturn(Optional.of(receptionistRole));
        when(passwordEncoder.encode("Clave1234")).thenReturn("HASH");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.create(
                new CreateUserRequest(" maria ", "  ", "Clave1234", "María", "Pérez", "RECEPTIONIST"));

        assertThat(response.username()).isEqualTo("maria");
        assertThat(response.email()).isNull();
        assertThat(response.role()).isEqualTo("RECEPTIONIST");
        assertThat(response.active()).isTrue();
        verify(passwordEncoder).encode("Clave1234");
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("maria")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("maria", null, "Clave1234", "María", "Pérez", "RECEPTIONIST")))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(userRepository.existsByUsername("maria")).thenReturn(false);
        when(userRepository.existsByEmail("maria@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("maria", "maria@mail.com", "Clave1234", "María", "Pérez", "RECEPTIONIST")))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createRejectsUnknownRole() {
        when(userRepository.existsByUsername("maria")).thenReturn(false);
        when(roleRepository.findByName("SUPERMAN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("maria", null, "Clave1234", "María", "Pérez", "SUPERMAN")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SUPERMAN");
    }

    @Test
    void adminCannotDeactivateThemselves() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(existingUser(5L, adminRole)));

        assertThatThrownBy(() -> service.setActive(5L, false, 5L))
                .isInstanceOf(BusinessException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void adminCanDeactivateAnotherUser() {
        User other = existingUser(6L, receptionistRole);
        when(userRepository.findById(6L)).thenReturn(Optional.of(other));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.setActive(6L, false, 5L);

        assertThat(response.active()).isFalse();
    }

    @Test
    void adminCannotChangeOwnRole() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(existingUser(5L, adminRole)));
        when(roleRepository.findByName("RECEPTIONIST")).thenReturn(Optional.of(receptionistRole));

        assertThatThrownBy(() -> service.update(5L,
                new UpdateUserRequest(null, "Nombre", "Apellido", "RECEPTIONIST"), 5L))
                .isInstanceOf(BusinessException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void adminCanChangeAnotherUsersRole() {
        User other = existingUser(6L, receptionistRole);
        when(userRepository.findById(6L)).thenReturn(Optional.of(other));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = service.update(6L,
                new UpdateUserRequest(null, "Nuevo", "Nombre", "ADMIN"), 5L);

        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.firstName()).isEqualTo("Nuevo");
    }
}
