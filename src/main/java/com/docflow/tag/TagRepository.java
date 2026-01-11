package com.docflow.tag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Tag persistence (owner-scoped).
public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findByOwnerIdOrderByNameAsc(Long ownerId);

    Optional<Tag> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByOwnerIdAndName(Long ownerId, String name);
}
