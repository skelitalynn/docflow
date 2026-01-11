package com.docflow.folder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Folder persistence (owner-scoped).
public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByOwnerIdAndDeletedFalse(Long ownerId);

    java.util.Optional<Folder> findByIdAndOwnerIdAndDeletedFalse(Long id, Long ownerId);
}
