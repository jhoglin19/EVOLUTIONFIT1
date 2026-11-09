package com.evolutionfit.modules.users;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.roles.Role;
import com.evolutionfit.modules.roles.RoleRepository;
import com.evolutionfit.modules.users.dto.CreateUserRequest;
import com.evolutionfit.modules.users.dto.UpdateUserRequest;
import com.evolutionfit.modules.users.dto.UserResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(String query, int page, int size) {
        String term = query == null ? "" : query.trim();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by("username").ascending());
        return userRepository.search(term, pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(findOrThrow(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = request.username().trim();
        String email = normalizeEmail(request.email());

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("El nombre de usuario ya está en uso");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new ConflictException("El correo ya está registrado");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setRole(findRole(request.role()));
        user.setActive(true);

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, Long currentUserId) {
        User user = findOrThrow(id);
        String email = normalizeEmail(request.email());

        if (email != null && !email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new ConflictException("El correo ya está registrado");
        }

        Role newRole = findRole(request.role());
        boolean roleChanged = !newRole.getId().equals(user.getRole().getId());
        if (roleChanged && id.equals(currentUserId)) {
            // Evita que el único administrador se quite el rol y deje el sistema sin administración.
            throw new BusinessException("No puedes cambiar tu propio rol");
        }

        user.setEmail(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setRole(newRole);

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse setActive(Long id, boolean active, Long currentUserId) {
        User user = findOrThrow(id);
        if (!active && id.equals(currentUserId)) {
            throw new BusinessException("No puedes desactivar tu propio usuario");
        }
        user.setActive(active);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = findOrThrow(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    // ---- helpers ----

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private Role findRole(String name) {
        return roleRepository.findByName(name.trim())
                .orElseThrow(() -> new BusinessException("El rol '" + name.trim() + "' no existe"));
    }

    /** Una cadena vacía se guarda como NULL para no chocar con la restricción UNIQUE del correo. */
    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim();
    }
}
