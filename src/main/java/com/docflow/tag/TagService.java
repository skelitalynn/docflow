package com.docflow.tag;

import com.docflow.audit.AuditService;
import com.docflow.common.BadRequestException;
import com.docflow.common.NotFoundException;
import com.docflow.user.User;
import com.docflow.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

// Tag domain: personal tag CRUD + audit.
@Service
public class TagService {
    private final TagRepository tagRepository;
    private final DocTagRepository docTagRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TagService(TagRepository tagRepository,
                      DocTagRepository docTagRepository,
                      UserRepository userRepository,
                      AuditService auditService) {
        this.tagRepository = tagRepository;
        this.docTagRepository = docTagRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    // List tags owned by current user (sorted).
    public List<Tag> list(Long userId) {
        return tagRepository.findByOwnerIdOrderByNameAsc(userId);
    }

    // Create a unique tag under current user.
    @Transactional
    public Tag create(Long userId, String name, String ip) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Tag name required");
        }
        String trimmed = name.trim();
        if (tagRepository.existsByOwnerIdAndName(userId, trimmed)) {
            throw new BadRequestException("Tag already exists");
        }
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Tag tag = Tag.builder()
                .owner(owner)
                .name(trimmed)
                .build();
        Tag saved = tagRepository.save(tag);
        auditService.record(owner, "tag_create", "tag", saved.getId(), null, true, null, ip, null);
        return saved;
    }

    // Rename a tag with uniqueness check.
    @Transactional
    public Tag rename(Long userId, Long tagId, String name, String ip) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Tag name required");
        }
        Tag tag = tagRepository.findByIdAndOwnerId(tagId, userId)
                .orElseThrow(() -> new NotFoundException("Tag not found"));
        String trimmed = name.trim();
        if (!trimmed.equals(tag.getName()) && tagRepository.existsByOwnerIdAndName(userId, trimmed)) {
            throw new BadRequestException("Tag already exists");
        }
        tag.setName(trimmed);
        Tag saved = tagRepository.save(tag);
        auditService.record(tag.getOwner(), "tag_rename", "tag", saved.getId(), null, true, null, ip,
                Map.of("name", trimmed));
        return saved;
    }

    // Delete a tag and its doc mappings.
    @Transactional
    public void delete(Long userId, Long tagId, String ip) {
        Tag tag = tagRepository.findByIdAndOwnerId(tagId, userId)
                .orElseThrow(() -> new NotFoundException("Tag not found"));
        docTagRepository.deleteByTagId(tagId);
        tagRepository.delete(tag);
        auditService.record(tag.getOwner(), "tag_delete", "tag", tagId, null, true, null, ip, null);
    }
}
