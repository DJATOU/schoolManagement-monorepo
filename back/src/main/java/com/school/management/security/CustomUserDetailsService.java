package com.school.management.security;

import com.school.management.persistance.AdministratorEntity;
import com.school.management.persistance.RoleEntity;
import com.school.management.repository.AdministratorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AdministratorRepository administratorRepository;

    @Autowired
    public CustomUserDetailsService(AdministratorRepository administratorRepository) {
        this.administratorRepository = administratorRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdministratorEntity administrator = administratorRepository.findByUsername(username);

        if (administrator == null) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }

        if (administrator.getActive() == null || !administrator.getActive()) {
            throw new UsernameNotFoundException("User account is disabled: " + username);
        }

        Collection<? extends GrantedAuthority> authorities = mapRolesToAuthorities(administrator.getRoles());

        return User.builder()
                .username(administrator.getUsername())
                .password(administrator.getPassword())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

    private Collection<? extends GrantedAuthority> mapRolesToAuthorities(Collection<RoleEntity> roles) {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());
    }
}
