package com.docflow.folder;

import com.docflow.audit.AuditService;
import com.docflow.common.BadRequestException;
import com.docflow.common.NotFoundException;
import com.docflow.document.DocumentRepository;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class FolderService {
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public FolderService(FolderRepository folderRepository,
                         DocumentRepository documentRepository,
                         UserRepository userRepository,
                         AuditService auditService) {
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public List<Folder> list(Long userId) {
        return folderRepository.findByOwnerIdAndDeletedFalse(userId);
    }

    @Transactional
    public Folder create(Long userId, String name, Long parentId, String ip) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Folder name required");
        }
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Folder parent = null;
        if (parentId != null) {
            parent = folderRepository.findByIdAndOwnerIdAndDeletedFalse(parentId, userId)
                    .orElseThrow(() -> new BadRequestException("Parent folder not found"));
        }
        Folder folder = Folder.builder()
                .owner(owner)
                .parent(parent)
                .name(name.trim())
                .deleted(false)
                .build();
        Folder saved = folderRepository.save(folder);
        auditService.record(owner, "folder_create", "folder", saved.getId(), null, true, null, ip, null);
        return saved;
    }

    @Transactional
    public Folder rename(Long userId, Long folderId, String name, String ip) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Folder name required");
        }
        Folder folder = folderRepository.findByIdAndOwnerIdAndDeletedFalse(folderId, userId)
                .orElseThrow(() -> new NotFoundException("Folder not found"));
        folder.setName(name.trim());
        Folder saved = folderRepository.save(folder);
        auditService.record(folder.getOwner(), "folder_rename", "folder", saved.getId(), null, true, null, ip,
                Map.of("name", saved.getName()));
        return saved;
    }

    @Transactional
    public void delete(Long userId, Long folderId, String ip) {
        Folder folder = folderRepository.findByIdAndOwnerIdAndDeletedFalse(folderId, userId)
                .orElseThrow(() -> new NotFoundException("Folder not found"));
        long docCount = documentRepository.countByFolderIdAndDeletedFalse(folderId);
        if (docCount > 0) {
            throw new BadRequestException("Folder not empty");
        }
        folder.setDeleted(true);
        folderRepository.save(folder);
        auditService.record(folder.getOwner(), "folder_delete", "folder", folderId, null, true, null, ip, null);
    }
}
