package com.distribuidora.backend.security;

import com.distribuidora.backend.model.User;
import com.distribuidora.backend.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.TreeSet;

// Authorities = "ROLE_<perfil>" + codigo de cada permissao. Carregadas do
// banco a cada requisicao, entao mudar o perfil de alguem vale na hora,
// sem esperar o token expirar.
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final AccessResolver accessResolver;

    public CustomUserDetailsService(UserRepository userRepository, AccessResolver accessResolver) {
        this.userRepository = userRepository;
        this.accessResolver = accessResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findWithAccessByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado: " + username));

        Set<String> authorities = new TreeSet<>(accessResolver.permissionCodes(user));
        user.getRoles().forEach(role -> authorities.add("ROLE_" + role.getCode()));

        return new AppUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getFullName(),
                user.isActive(),
                authorities.stream().map(SimpleGrantedAuthority::new).toList());
    }
}
