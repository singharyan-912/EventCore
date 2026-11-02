package com.eventprocessor.reliable_event_processor.repository;

import com.eventprocessor.reliable_event_processor.entity.AppUser;
import com.eventprocessor.reliable_event_processor.entity.Role;
import com.eventprocessor.reliable_event_processor.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long countByRoleAndStatus(Role role, UserStatus status);

    Page<AppUser> findAll(Pageable pageable);
}

