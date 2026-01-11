package com.docflow.meeting;

import com.docflow.common.MeetingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
    Optional<Meeting> findFirstByDocumentIdAndStatusOrderByCreatedAtDesc(Long docId, MeetingStatus status);

    Page<Meeting> findByDocumentIdOrderByCreatedAtDesc(Long docId, Pageable pageable);
}
