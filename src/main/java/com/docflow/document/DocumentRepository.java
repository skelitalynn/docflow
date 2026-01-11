package com.docflow.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

// Document persistence with ACL-filtered list/search and optimistic updates.
public interface DocumentRepository extends JpaRepository<Document, Long> {
    Optional<Document> findByIdAndDeletedFalse(Long id);

    // ACL-filtered list with optional folder/tag filters.
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

    // Title keyword search using LIKE.
    @Query("""
            select d from Document d
            where d.deleted = false
              and (:keyword is null
                or d.title like concat('%', :keyword, '%'))
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

    // Title keyword search with ACL filter (native query).
    @Query(value = """
            select distinct d.* from t_document d
            join t_doc_member m on m.doc_id = d.doc_id and m.user_id = :userId
            left join t_doc_tag dt on dt.doc_id = d.doc_id
            where d.is_deleted = 0
              and (:keyword is null or d.title like concat('%', :keyword, '%'))
              and (:authorId is null or d.creator_id = :authorId)
              and (:fromTime is null or d.updated_at >= :fromTime)
              and (:toTime is null or d.updated_at <= :toTime)
              and (:tagId is null or dt.tag_id = :tagId)
            -- #pageable
            """,
            countQuery = """
            select count(distinct d.doc_id) from t_document d
            join t_doc_member m on m.doc_id = d.doc_id and m.user_id = :userId
            left join t_doc_tag dt on dt.doc_id = d.doc_id
            where d.is_deleted = 0
              and (:keyword is null or d.title like concat('%', :keyword, '%'))
              and (:authorId is null or d.creator_id = :authorId)
              and (:fromTime is null or d.updated_at >= :fromTime)
              and (:toTime is null or d.updated_at <= :toTime)
              and (:tagId is null or dt.tag_id = :tagId)
            """,
            nativeQuery = true)
    Page<Document> searchAccessibleDocumentsFullText(@Param("userId") Long userId,
                                                     @Param("keyword") String keyword,
                                                     @Param("authorId") Long authorId,
                                                     @Param("fromTime") LocalDateTime from,
                                                     @Param("toTime") LocalDateTime to,
                                                     @Param("tagId") Long tagId,
                                                     Pageable pageable);

    // Optimistic save: update only when baseVersion matches.
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

    // Folder delete guard: prevent removing non-empty folder.
    long countByFolderIdAndDeletedFalse(Long folderId);
}
