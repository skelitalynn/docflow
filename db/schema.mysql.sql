-- Docflow schema (MySQL 8.x)
-- P0 tables based on requirement_docs/数据库表.md

CREATE DATABASE IF NOT EXISTS docflow
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE docflow;

CREATE TABLE IF NOT EXISTS t_user (
  user_id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(128) NULL,
  phone VARCHAR(32) NULL,
  password_hash VARCHAR(255) NOT NULL,
  system_role TINYINT NOT NULL,
  nickname VARCHAR(64) NULL,
  avatar_url VARCHAR(255) NULL,
  status TINYINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_user_email (email),
  UNIQUE KEY uk_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_password_reset (
  reset_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  expires_at DATETIME NOT NULL,
  is_used TINYINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  used_at DATETIME NULL,
  PRIMARY KEY (reset_id),
  UNIQUE KEY uk_password_reset_token (token_hash),
  KEY idx_password_reset_user (user_id),
  CONSTRAINT fk_password_reset_user
    FOREIGN KEY (user_id) REFERENCES t_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_folder (
  folder_id BIGINT NOT NULL AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  name VARCHAR(64) NOT NULL,
  is_deleted TINYINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (folder_id),
  KEY idx_folder_owner (owner_user_id),
  KEY idx_folder_parent (parent_id),
  CONSTRAINT fk_folder_owner
    FOREIGN KEY (owner_user_id) REFERENCES t_user(user_id),
  CONSTRAINT fk_folder_parent
    FOREIGN KEY (parent_id) REFERENCES t_folder(folder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_document (
  doc_id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  content LONGTEXT NOT NULL,
  content_format TINYINT NOT NULL DEFAULT 1,
  creator_id BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  folder_id BIGINT NULL,
  version INT NOT NULL,
  is_deleted TINYINT NOT NULL,
  deleted_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (doc_id),
  KEY idx_document_owner (owner_id),
  KEY idx_document_folder (folder_id),
  KEY idx_document_updated (updated_at),
  CONSTRAINT fk_document_creator
    FOREIGN KEY (creator_id) REFERENCES t_user(user_id),
  CONSTRAINT fk_document_owner
    FOREIGN KEY (owner_id) REFERENCES t_user(user_id),
  CONSTRAINT fk_document_folder
    FOREIGN KEY (folder_id) REFERENCES t_folder(folder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_tag (
  tag_id BIGINT NOT NULL AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (tag_id),
  UNIQUE KEY uk_tag_owner_name (owner_user_id, name),
  KEY idx_tag_owner (owner_user_id),
  CONSTRAINT fk_tag_owner
    FOREIGN KEY (owner_user_id) REFERENCES t_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_doc_tag (
  doc_tag_id BIGINT NOT NULL AUTO_INCREMENT,
  doc_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (doc_tag_id),
  UNIQUE KEY uk_doc_tag_doc_tag (doc_id, tag_id),
  KEY idx_doc_tag_tag (tag_id),
  CONSTRAINT fk_doc_tag_doc
    FOREIGN KEY (doc_id) REFERENCES t_document(doc_id),
  CONSTRAINT fk_doc_tag_tag
    FOREIGN KEY (tag_id) REFERENCES t_tag(tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_doc_template (
  template_id BIGINT NOT NULL AUTO_INCREMENT,
  owner_user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  description VARCHAR(255) NULL,
  content LONGTEXT NOT NULL,
  content_format TINYINT NOT NULL,
  is_public TINYINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (template_id),
  KEY idx_template_owner (owner_user_id),
  KEY idx_template_public (is_public),
  CONSTRAINT fk_template_owner
    FOREIGN KEY (owner_user_id) REFERENCES t_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_doc_member (
  member_id BIGINT NOT NULL AUTO_INCREMENT,
  doc_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  doc_role TINYINT NOT NULL,
  granted_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (member_id),
  UNIQUE KEY uk_doc_member_doc_user (doc_id, user_id),
  KEY idx_doc_member_user (user_id),
  CONSTRAINT fk_doc_member_doc
    FOREIGN KEY (doc_id) REFERENCES t_document(doc_id),
  CONSTRAINT fk_doc_member_user
    FOREIGN KEY (user_id) REFERENCES t_user(user_id),
  CONSTRAINT fk_doc_member_granted_by
    FOREIGN KEY (granted_by) REFERENCES t_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_comment (
  comment_id BIGINT NOT NULL AUTO_INCREMENT,
  doc_id BIGINT NOT NULL,
  thread_id BIGINT NULL,
  parent_id BIGINT NULL,
  author_id BIGINT NOT NULL,
  block_id VARCHAR(64) NULL,
  paragraph_index INT NULL,
  content TEXT NOT NULL,
  status TINYINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (comment_id),
  KEY idx_comment_doc (doc_id),
  KEY idx_comment_thread (thread_id),
  CONSTRAINT fk_comment_doc
    FOREIGN KEY (doc_id) REFERENCES t_document(doc_id),
  CONSTRAINT fk_comment_parent
    FOREIGN KEY (parent_id) REFERENCES t_comment(comment_id),
  CONSTRAINT fk_comment_author
    FOREIGN KEY (author_id) REFERENCES t_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_comment_mention (
  mention_id BIGINT NOT NULL AUTO_INCREMENT,
  comment_id BIGINT NOT NULL,
  mentioned_user_id BIGINT NOT NULL,
  mentioned_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (mention_id),
  UNIQUE KEY uk_comment_mention_unique (comment_id, mentioned_user_id),
  KEY idx_comment_mention_user (mentioned_user_id),
  CONSTRAINT fk_comment_mention_comment
    FOREIGN KEY (comment_id) REFERENCES t_comment(comment_id),
  CONSTRAINT fk_comment_mention_user
    FOREIGN KEY (mentioned_user_id) REFERENCES t_user(user_id),
  CONSTRAINT fk_comment_mention_by
    FOREIGN KEY (mentioned_by) REFERENCES t_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_notification (
  notify_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  notify_type TINYINT NOT NULL,
  doc_id BIGINT NULL,
  comment_id BIGINT NULL,
  is_read TINYINT NOT NULL,
  payload LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at DATETIME NULL,
  PRIMARY KEY (notify_id),
  KEY idx_notification_user (user_id),
  KEY idx_notification_read (user_id, is_read),
  CONSTRAINT fk_notification_user
    FOREIGN KEY (user_id) REFERENCES t_user(user_id),
  CONSTRAINT fk_notification_doc
    FOREIGN KEY (doc_id) REFERENCES t_document(doc_id),
  CONSTRAINT fk_notification_comment
    FOREIGN KEY (comment_id) REFERENCES t_comment(comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_audit_log (
  audit_id BIGINT NOT NULL AUTO_INCREMENT,
  actor_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NULL,
  doc_id BIGINT NULL,
  result TINYINT NOT NULL,
  error_msg VARCHAR(255) NULL,
  ip VARCHAR(45) NULL,
  meta LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (audit_id),
  KEY idx_audit_doc (doc_id),
  KEY idx_audit_actor (actor_id),
  CONSTRAINT fk_audit_actor
    FOREIGN KEY (actor_id) REFERENCES t_user(user_id),
  CONSTRAINT fk_audit_doc
    FOREIGN KEY (doc_id) REFERENCES t_document(doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Optional table (not required for P0)
-- CREATE TABLE IF NOT EXISTS t_login_attempt (
--   attempt_id BIGINT NOT NULL AUTO_INCREMENT,
--   account VARCHAR(128) NOT NULL,
--   fail_count INT NOT NULL,
--   locked_until DATETIME NULL,
--   last_failed_at DATETIME NULL,
--   updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
--   PRIMARY KEY (attempt_id),
--   KEY idx_login_attempt_account (account)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
