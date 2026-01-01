package com.docflow.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    Optional<Document> findByIdAndDeletedFalse(Long id);

    @Query("""
            select d from Document d
            where d.deleted = false
              and (:folderId is null or d.folder.id = :folderId)
              and (:tagId is null or exists (
                select 1 from DocTag dt
                where dt.document = d and dt.tag.id = :tagId
              ))
              and exists (
                select 1 from DocMember m
                where m.document = d and m.user.id = :userId
              )
            """)
    Page<Document> findAccessibleDocuments(@Param("userId") Long userId,
                                           @Param("folderId") Long folderId,
                                           @Param("tagId") Long tagId,
                                           Pageable pageable);

    @Query("""
            select d from Document d
            where d.deleted = false
              and (:keyword is null
                or d.title like concat('%', :keyword, '%')
                or d.content like concat('%', :keyword, '%'))
              and (:authorId is null or d.creator.id = :authorId)
              and (:fromTime is null or d.updatedAt >= :fromTime)
              and (:toTime is null or d.updatedAt <= :toTime)
              and (:tagId is null or exists (
                select 1 from DocTag dt
                where dt.document = d and dt.tag.id = :tagId
              ))
              and exists (
                select 1 from DocMember m
                where m.document = d and m.user.id = :userId
              )
            """)
    Page<Document> searchAccessibleDocuments(@Param("userId") Long userId,
                                             @Param("keyword") String keyword,
                                             @Param("authorId") Long authorId,
                                             @Param("fromTime") LocalDateTime from,
                                             @Param("toTime") LocalDateTime to,
                                             @Param("tagId") Long tagId,
                                             Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Document d
            set d.content = :content,
                d.contentFormat = :format,
                d.version = d.version + 1,
                d.updatedAt = current_timestamp
            where d.id = :docId
              and d.version = :baseVersion
              and d.deleted = false
            """)
    int updateContentIfVersionMatches(@Param("docId") Long docId,
                                      @Param("content") String content,
                                      @Param("baseVersion") int baseVersion,
                                      @Param("format") DocFormat format);

    long countByFolderIdAndDeletedFalse(Long folderId);
}
