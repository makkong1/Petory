
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `admin_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_audit_log` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `admin_idx` bigint NOT NULL,
  `action` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_idx` bigint DEFAULT NULL,
  `detail` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`idx`),
  KEY `idx_audit_admin_created` (`admin_idx`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `board`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `board` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `user_idx` bigint NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `content` longtext,
  `status` enum('ACTIVE','BLINDED','DELETED') NOT NULL DEFAULT 'ACTIVE',
  `category` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `view_count` int DEFAULT '0',
  `like_count` int DEFAULT '0',
  `comment_count` int DEFAULT '0',
  `last_reaction_at` datetime DEFAULT NULL,
  `is_deleted` tinyint(1) DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `dislike_count` int DEFAULT '0',
  PRIMARY KEY (`idx`),
  KEY `idx_board_status` (`status`),
  KEY `idx_board_user_deleted_created` (`user_idx`,`is_deleted`,`created_at` DESC),
  KEY `idx_board_category_deleted_created` (`category`,`is_deleted`,`created_at` DESC),
  KEY `idx_board_deleted_created` (`is_deleted`,`created_at` DESC),
  KEY `idx_board_created_at_desc` (`created_at` DESC),
  FULLTEXT KEY `idx_board_title_content` (`title`,`content`) /*!50100 WITH PARSER `ngram` */ ,
  CONSTRAINT `board_ibfk_1` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=28763 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `board_popularity_snapshot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `board_popularity_snapshot` (
  `snapshot_id` bigint NOT NULL AUTO_INCREMENT,
  `comment_count` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'BaseTimeEntity (@LastModifiedDate)',
  `like_count` int NOT NULL,
  `period_end_date` date NOT NULL,
  `period_start_date` date NOT NULL,
  `period_type` enum('MONTHLY','WEEKLY') NOT NULL,
  `popularity_score` int NOT NULL,
  `ranking` int NOT NULL,
  `view_count` int NOT NULL,
  `board_id` bigint NOT NULL,
  PRIMARY KEY (`snapshot_id`),
  KEY `idx_snapshot_recent` (`period_type`,`period_end_date` DESC,`ranking`),
  KEY `idx_snapshot_range` (`period_type`,`period_start_date`,`period_end_date`),
  KEY `idx_snapshot_board_id` (`board_id`),
  CONSTRAINT `FKigqdyke28m9pvmo8jecehoh3d` FOREIGN KEY (`board_id`) REFERENCES `board` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `board_reaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `board_reaction` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `reaction_type` enum('DISLIKE','LIKE') NOT NULL,
  `board_idx` bigint NOT NULL,
  `user_idx` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`idx`),
  UNIQUE KEY `UKaymqx4hghgrqitkbplgp553u0` (`board_idx`,`user_idx`),
  KEY `FKag3ixpa53bjp1p5s79myoscpr` (`user_idx`),
  CONSTRAINT `FKag3ixpa53bjp1p5s79myoscpr` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`),
  CONSTRAINT `FKgjhpjoxw7tt1kyfimlomv872y` FOREIGN KEY (`board_idx`) REFERENCES `board` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=88505 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `board_view_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `board_view_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `viewed_at` datetime(6) NOT NULL,
  `board_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_board_view_log_board_user` (`board_id`,`user_id`),
  KEY `FKemjj96yrflacv5mtek2nipy22` (`user_id`),
  CONSTRAINT `FKdlfgog8qjrr26l9qupeytyik0` FOREIGN KEY (`board_id`) REFERENCES `board` (`idx`),
  CONSTRAINT `FKemjj96yrflacv5mtek2nipy22` FOREIGN KEY (`user_id`) REFERENCES `users` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `careapplication`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `careapplication` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `care_request_idx` bigint NOT NULL,
  `provider_idx` bigint NOT NULL,
  `status` enum('PENDING','ACCEPTED','REJECTED') DEFAULT 'PENDING',
  `message` longtext,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`idx`),
  KEY `care_request_idx` (`care_request_idx`),
  KEY `provider_idx` (`provider_idx`),
  CONSTRAINT `careapplication_ibfk_1` FOREIGN KEY (`care_request_idx`) REFERENCES `carerequest` (`idx`),
  CONSTRAINT `careapplication_ibfk_2` FOREIGN KEY (`provider_idx`) REFERENCES `users` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=44 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `carerequest`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `carerequest` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `user_idx` bigint NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `description` longtext,
  `date` datetime DEFAULT NULL,
  `schedule_mode` varchar(32) NOT NULL DEFAULT 'FIXED' COMMENT 'FIXED | FLEXIBLE_CHAT',
  `estimated_duration_minutes` int DEFAULT NULL COMMENT '예상 돌봄 소요(분)',
  `status` enum('OPEN','IN_PROGRESS','COMPLETED','CANCELLED') DEFAULT 'OPEN',
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `is_deleted` tinyint(1) DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `pet_idx` bigint DEFAULT NULL,
  `offered_coins` int DEFAULT NULL COMMENT '제시한 코인 가격 (요청자가 설정)',
  `completed_at` datetime DEFAULT NULL,
  `latitude` double DEFAULT NULL COMMENT '위도 (케어 요청 장소)',
  `longitude` double DEFAULT NULL COMMENT '경도 (케어 요청 장소)',
  `address` varchar(255) DEFAULT NULL COMMENT '주소 (클라이언트 geocoding 후 저장)',
  PRIMARY KEY (`idx`),
  KEY `user_idx` (`user_idx`),
  KEY `fk_carerequest_pet` (`pet_idx`),
  CONSTRAINT `carerequest_ibfk_1` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`),
  CONSTRAINT `fk_carerequest_pet` FOREIGN KEY (`pet_idx`) REFERENCES `pets` (`idx`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=1053 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `carerequest_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `carerequest_comment` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `care_request_idx` bigint NOT NULL,
  `user_idx` bigint NOT NULL,
  `content` longtext,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`idx`),
  KEY `fk_care_request_comment_request` (`care_request_idx`),
  KEY `fk_care_request_comment_user` (`user_idx`),
  CONSTRAINT `fk_care_request_comment_request` FOREIGN KEY (`care_request_idx`) REFERENCES `carerequest` (`idx`) ON DELETE CASCADE,
  CONSTRAINT `fk_care_request_comment_user` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `carereview`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `carereview` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `care_application_idx` bigint NOT NULL,
  `reviewer_idx` bigint NOT NULL,
  `reviewee_idx` bigint NOT NULL,
  `rating` int NOT NULL,
  `comment` longtext,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`idx`),
  KEY `care_application_idx` (`care_application_idx`),
  KEY `reviewer_idx` (`reviewer_idx`),
  KEY `reviewee_idx` (`reviewee_idx`),
  CONSTRAINT `carereview_ibfk_1` FOREIGN KEY (`care_application_idx`) REFERENCES `careapplication` (`idx`),
  CONSTRAINT `carereview_ibfk_2` FOREIGN KEY (`reviewer_idx`) REFERENCES `users` (`idx`),
  CONSTRAINT `carereview_ibfk_3` FOREIGN KEY (`reviewee_idx`) REFERENCES `users` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `chatmessage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chatmessage` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `conversation_idx` bigint NOT NULL,
  `sender_idx` bigint NOT NULL,
  `message_type` enum('TEXT','IMAGE','FILE','SYSTEM','NOTICE') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'TEXT',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `reply_to_message_idx` bigint DEFAULT NULL,
  `is_deleted` tinyint(1) DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`idx`),
  KEY `reply_to_message_idx` (`reply_to_message_idx`),
  KEY `idx_chat_message_conversation_created` (`conversation_idx`,`created_at` DESC),
  KEY `idx_chat_message_sender` (`sender_idx`,`created_at` DESC),
  KEY `idx_chat_message_type` (`message_type`,`created_at` DESC),
  KEY `idx_chat_message_deleted` (`is_deleted`,`deleted_at`),
  FULLTEXT KEY `idx_chat_message_content` (`content`),
  CONSTRAINT `chatmessage_ibfk_1` FOREIGN KEY (`conversation_idx`) REFERENCES `conversation` (`idx`) ON DELETE CASCADE,
  CONSTRAINT `chatmessage_ibfk_2` FOREIGN KEY (`sender_idx`) REFERENCES `users` (`idx`),
  CONSTRAINT `chatmessage_ibfk_3` FOREIGN KEY (`reply_to_message_idx`) REFERENCES `chatmessage` (`idx`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=470625 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comment` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `board_idx` bigint NOT NULL,
  `user_idx` bigint NOT NULL,
  `content` longtext,
  `status` enum('ACTIVE','BLINDED','DELETED') NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`idx`),
  KEY `board_idx` (`board_idx`),
  KEY `user_idx` (`user_idx`),
  KEY `idx_comment_status` (`status`),
  CONSTRAINT `comment_ibfk_1` FOREIGN KEY (`board_idx`) REFERENCES `board` (`idx`),
  CONSTRAINT `comment_ibfk_2` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=33754 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `comment_reaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comment_reaction` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `reaction_type` enum('DISLIKE','LIKE') NOT NULL,
  `comment_idx` bigint NOT NULL,
  `user_idx` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`idx`),
  UNIQUE KEY `UKbes4ghhrkss5cdpx28ugh86gh` (`comment_idx`,`user_idx`),
  KEY `FK24cjwe1ksjmeujkgoa6f2pya` (`user_idx`),
  CONSTRAINT `FK24cjwe1ksjmeujkgoa6f2pya` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`),
  CONSTRAINT `FKsdwdiwq8fqnux32g3tbns36tr` FOREIGN KEY (`comment_idx`) REFERENCES `comment` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `conversation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversation` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `conversation_type` enum('DIRECT','GROUP','CARE_REQUEST','MISSING_PET','MEETUP','ADMIN_SUPPORT') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `related_type` enum('CARE_REQUEST','CARE_APPLICATION','MISSING_PET_BOARD','MEETUP','USER') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `related_idx` bigint DEFAULT NULL,
  `status` enum('ACTIVE','CLOSED','ARCHIVED') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE',
  `last_message_at` datetime DEFAULT NULL,
  `last_message_preview` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_deleted` tinyint(1) DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`idx`),
  KEY `idx_conversation_type_status` (`conversation_type`,`status`,`last_message_at` DESC),
  KEY `idx_conversation_related` (`related_type`,`related_idx`),
  KEY `idx_conversation_deleted` (`is_deleted`,`deleted_at`),
  CONSTRAINT `chk_related_fields` CHECK ((((`related_type` is null) and (`related_idx` is null)) or ((`related_type` is not null) and (`related_idx` is not null))))
) ENGINE=InnoDB AUTO_INCREMENT=1061 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `conversationparticipant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversationparticipant` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `conversation_idx` bigint NOT NULL,
  `user_idx` bigint NOT NULL,
  `role` enum('MEMBER','ADMIN','MODERATOR') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'MEMBER',
  `unread_count` int DEFAULT '0',
  `last_read_message_idx` bigint DEFAULT NULL,
  `last_read_at` datetime DEFAULT NULL,
  `status` enum('ACTIVE','LEFT','KICKED','MUTED') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'ACTIVE',
  `joined_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `left_at` datetime DEFAULT NULL,
  `is_deleted` tinyint(1) DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deal_confirmed` tinyint(1) NOT NULL DEFAULT '0' COMMENT '거래 확정 여부',
  `deal_confirmed_at` datetime DEFAULT NULL COMMENT '거래 확정 시간',
  PRIMARY KEY (`idx`),
  UNIQUE KEY `uk_participant_conversation_user` (`conversation_idx`,`user_idx`),
  KEY `last_read_message_idx` (`last_read_message_idx`),
  KEY `idx_participant_user_status` (`user_idx`,`status`,`unread_count` DESC),
  KEY `idx_participant_conversation` (`conversation_idx`,`status`),
  KEY `idx_participant_unread` (`user_idx`,`unread_count`),
  CONSTRAINT `conversationparticipant_ibfk_1` FOREIGN KEY (`conversation_idx`) REFERENCES `conversation` (`idx`) ON DELETE CASCADE,
  CONSTRAINT `conversationparticipant_ibfk_2` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`),
  CONSTRAINT `conversationparticipant_ibfk_3` FOREIGN KEY (`last_read_message_idx`) REFERENCES `chatmessage` (`idx`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=2022 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `dailystatistics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dailystatistics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stat_date` date NOT NULL,
  `new_users` bigint NOT NULL DEFAULT '0' COMMENT '신규 가입자',
  `new_posts` bigint NOT NULL DEFAULT '0' COMMENT '신규 게시글',
  `new_care_requests` bigint NOT NULL DEFAULT '0' COMMENT '케어 요청 수',
  `completed_cares` bigint NOT NULL DEFAULT '0' COMMENT '케어 완료 수',
  `cancelled_cares` bigint NOT NULL DEFAULT '0' COMMENT '케어 취소 수',
  `care_completion_rate` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '케어 완료율 pct',
  `total_revenue` decimal(15,2) DEFAULT '0.00',
  `transaction_count` bigint NOT NULL DEFAULT '0' COMMENT '결제 건수',
  `avg_transaction` decimal(15,2) NOT NULL DEFAULT '0.00' COMMENT '평균 거래금액',
  `active_users` bigint NOT NULL DEFAULT '0' COMMENT 'DAU',
  `new_providers` bigint NOT NULL DEFAULT '0' COMMENT '신규 서비스 제공자',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `new_meetups` bigint NOT NULL DEFAULT '0' COMMENT '신규 모임',
  `meetup_participants` bigint NOT NULL DEFAULT '0' COMMENT '모임 참여자 수',
  `new_reports` bigint NOT NULL DEFAULT '0' COMMENT '신고 접수',
  `resolved_reports` bigint NOT NULL DEFAULT '0' COMMENT '신고 처리 수',
  PRIMARY KEY (`id`),
  UNIQUE KEY `stat_date` (`stat_date`)
) ENGINE=InnoDB AUTO_INCREMENT=137 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `fcm_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fcm_token` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `user_idx` bigint NOT NULL,
  `token` varchar(512) NOT NULL,
  `device_type` varchar(10) NOT NULL COMMENT 'ANDROID | IOS',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`idx`),
  UNIQUE KEY `uk_fcm_token_token` (`token`),
  KEY `fk_fcm_token_user` (`user_idx`),
  CONSTRAINT `fk_fcm_token_user` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `target_type` enum('BOARD','COMMENT','CARE_COMMENT','MISSING_PET','MISSING_PET_COMMENT','PET') NOT NULL,
  `target_idx` bigint NOT NULL,
  `file_path` varchar(255) NOT NULL,
  `file_type` varchar(50) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'BaseTimeEntity (@LastModifiedDate)',
  PRIMARY KEY (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=368 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `locationservice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `locationservice` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `location` point NOT NULL /*!80003 SRID 4326 */,
  `rating` double DEFAULT '0',
  `description` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `website` varchar(500) DEFAULT NULL,
  `pet_friendly` tinyint(1) DEFAULT '0',
  `coordinates` point DEFAULT NULL,
  `category1` varchar(100) DEFAULT NULL,
  `category2` varchar(100) DEFAULT NULL,
  `category3` varchar(100) DEFAULT NULL,
  `sido` varchar(50) DEFAULT NULL,
  `sigungu` varchar(50) DEFAULT NULL,
  `eupmyeondong` varchar(50) DEFAULT NULL,
  `road_name` varchar(100) DEFAULT NULL,
  `zip_code` varchar(10) DEFAULT NULL,
  `closed_day` varchar(255) DEFAULT NULL,
  `operating_hours` varchar(255) DEFAULT NULL,
  `parking_available` tinyint(1) DEFAULT '0',
  `price_info` varchar(255) DEFAULT NULL,
  `is_pet_only` tinyint(1) DEFAULT NULL,
  `pet_size` varchar(100) DEFAULT NULL,
  `pet_restrictions` varchar(255) DEFAULT NULL,
  `pet_extra_fee` varchar(255) DEFAULT NULL,
  `indoor` tinyint(1) DEFAULT NULL,
  `outdoor` tinyint(1) DEFAULT NULL,
  `last_updated` date DEFAULT NULL,
  `data_source` varchar(50) DEFAULT 'PUBLIC',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `review_count` int NOT NULL DEFAULT '0' COMMENT '활성 리뷰 수 캐시 (soft delete 제외)',
  `score` double DEFAULT '0',
  `tags` json DEFAULT NULL COMMENT '반려생활 의도 태그 목록',
  PRIMARY KEY (`idx`),
  KEY `idx_name_address` (`name`,`address`),
  KEY `idx_locationservice_deleted_rating` (`is_deleted`,`rating` DESC),
  KEY `idx_category3_deleted_rating` (`category3`,`is_deleted`,`rating` DESC),
  KEY `idx_locationservice_sido_deleted_rating` (`sido`,`is_deleted`,`rating` DESC),
  KEY `idx_locationservice_sigungu_deleted_rating` (`sigungu`,`is_deleted`,`rating` DESC),
  KEY `idx_locationservice_eupmyeondong_deleted_rating` (`eupmyeondong`,`is_deleted`,`rating` DESC),
  KEY `idx_road_name_deleted_rating` (`road_name`,`is_deleted`,`rating` DESC),
  SPATIAL KEY `idx_locationservice_location_spatial` (`location`),
  FULLTEXT KEY `ft_search` (`name`,`description`,`category1`,`category2`,`category3`)
) ENGINE=InnoDB AUTO_INCREMENT=57454 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `trg_locationservice_set_location` BEFORE INSERT ON `locationservice` FOR EACH ROW SET NEW.location = IF(
      NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL,
      ST_GeomFromText(CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'), 4326),
      ST_GeomFromText('POINT(0 0)', 4326)
  ) */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `trg_locationservice_set_location_update` BEFORE UPDATE ON `locationservice` FOR EACH ROW SET NEW.location = IF(
    NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL,
    ST_GeomFromText(CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'), 4326),
    ST_GeomFromText('POINT(0 0)', 4326)
) */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `locationservicereview`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `locationservicereview` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `service_idx` bigint NOT NULL,
  `user_idx` bigint NOT NULL,
  `rating` int NOT NULL,
  `comment` longtext,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`idx`),
  KEY `service_idx` (`service_idx`),
  KEY `user_idx` (`user_idx`),
  KEY `idx_locationservicereview_service_deleted` (`service_idx`,`is_deleted`),
  KEY `idx_locationservicereview_user_deleted` (`user_idx`,`is_deleted`),
  CONSTRAINT `locationservicereview_ibfk_1` FOREIGN KEY (`service_idx`) REFERENCES `locationservice` (`idx`),
  CONSTRAINT `locationservicereview_ibfk_2` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `login_events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `login_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `login_at` datetime(6) NOT NULL,
  `login_method` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'LOCAL / GOOGLE / NAVER / KAKAO',
  PRIMARY KEY (`id`),
  KEY `idx_login_events_user_login_at` (`user_id`,`login_at`),
  KEY `idx_login_events_login_at` (`login_at`),
  CONSTRAINT `fk_login_events_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`idx`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `meetup`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `meetup` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL,
  `description` longtext,
  `location` varchar(255) DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `date` datetime NOT NULL,
  `organizer_idx` bigint NOT NULL,
  `max_participants` int DEFAULT '10',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `status` varchar(20) DEFAULT 'RECRUITING' COMMENT '모임 상태: RECRUITING(모집중), CLOSED(마감), COMPLETED(종료)',
  `current_participants` int DEFAULT '0' COMMENT '현재 참가자 수',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `geo_point` point NOT NULL /*!80003 SRID 4326 */,
  PRIMARY KEY (`idx`),
  KEY `organizer_idx` (`organizer_idx`),
  KEY `idx_meetup_status` (`status`),
  KEY `idx_meetup_date` (`date`),
  KEY `idx_meetup_location` (`latitude`,`longitude`),
  KEY `idx_meetup_date_status` (`date`,`status`),
  SPATIAL KEY `idx_meetup_geo_point_spatial` (`geo_point`),
  FULLTEXT KEY `idx_meetup_title_description` (`title`,`description`) /*!50100 WITH PARSER `ngram` */ ,
  CONSTRAINT `meetup_ibfk_1` FOREIGN KEY (`organizer_idx`) REFERENCES `users` (`idx`),
  CONSTRAINT `chk_participants` CHECK ((`current_participants` <= `max_participants`))
) ENGINE=InnoDB AUTO_INCREMENT=47464 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `trg_meetup_set_geo_point_insert` BEFORE INSERT ON `meetup` FOR EACH ROW SET NEW.geo_point = IF(
    NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL,
    ST_GeomFromText(CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'), 4326),
    ST_GeomFromText('POINT(0 0)', 4326)
) */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`localhost`*/ /*!50003 TRIGGER `trg_meetup_set_geo_point_update` BEFORE UPDATE ON `meetup` FOR EACH ROW SET NEW.geo_point = IF(
    NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL,
    ST_GeomFromText(CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'), 4326),
    ST_GeomFromText('POINT(0 0)', 4326)
) */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `meetupparticipants`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `meetupparticipants` (
  `meetup_idx` bigint NOT NULL,
  `user_idx` bigint NOT NULL,
  `joined_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `liked` tinyint(1) NOT NULL DEFAULT '0' COMMENT '사용자 모임 기록 좋아요 여부',
  PRIMARY KEY (`meetup_idx`,`user_idx`),
  KEY `user_idx` (`user_idx`),
  KEY `idx_meetupparticipants_user_liked_joined` (`user_idx`,`liked`,`joined_at`),
  CONSTRAINT `meetupparticipants_ibfk_1` FOREIGN KEY (`meetup_idx`) REFERENCES `meetup` (`idx`),
  CONSTRAINT `meetupparticipants_ibfk_2` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `missing_pet_board`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `missing_pet_board` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `age` varchar(30) DEFAULT NULL,
  `breed` varchar(50) DEFAULT NULL,
  `color` varchar(50) DEFAULT NULL,
  `content` longtext,
  `created_at` datetime(6) DEFAULT NULL,
  `gender` enum('F','M') DEFAULT NULL,
  `latitude` decimal(15,12) DEFAULT NULL,
  `longitude` decimal(15,12) DEFAULT NULL,
  `lost_date` date DEFAULT NULL,
  `lost_location` varchar(255) DEFAULT NULL,
  `pet_name` varchar(50) DEFAULT NULL,
  `species` varchar(50) DEFAULT NULL,
  `status` enum('FOUND','MISSING','RESOLVED') DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_idx` bigint NOT NULL,
  `is_deleted` tinyint(1) DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`idx`),
  KEY `FKrid0u1qvm8e07etghggxnu1b1` (`user_idx`),
  KEY `idx_missing_pet_status` (`status`,`is_deleted`,`created_at` DESC),
  KEY `idx_missing_pet_location` (`latitude`,`longitude`),
  KEY `idx_missing_pet_user` (`user_idx`,`is_deleted`,`created_at` DESC),
  CONSTRAINT `FKrid0u1qvm8e07etghggxnu1b1` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=405 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `missing_pet_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `missing_pet_comment` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `content` longtext,
  `created_at` datetime(6) DEFAULT NULL,
  `board_idx` bigint NOT NULL,
  `user_idx` bigint NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `latitude` decimal(15,12) DEFAULT NULL,
  `longitude` decimal(15,12) DEFAULT NULL,
  `is_deleted` tinyint(1) DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`idx`),
  KEY `FKpodx5stuchr73mrjgffir72ii` (`board_idx`),
  KEY `FKe3sca61815j9cxi608oxmrfjt` (`user_idx`),
  KEY `idx_missing_pet_comment_board_is_deleted` (`board_idx`,`is_deleted`),
  CONSTRAINT `FKe3sca61815j9cxi608oxmrfjt` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`),
  CONSTRAINT `FKpodx5stuchr73mrjgffir72ii` FOREIGN KEY (`board_idx`) REFERENCES `missing_pet_board` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=3041 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `monthly_statistics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `monthly_statistics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `year` int NOT NULL COMMENT '연도',
  `month` int NOT NULL COMMENT '월 (1~12)',
  `new_users` bigint NOT NULL DEFAULT '0',
  `active_users` bigint NOT NULL DEFAULT '0' COMMENT 'MAU',
  `new_providers` bigint NOT NULL DEFAULT '0',
  `monthly_retention_rate` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '월간 재방문율',
  `churn_rate` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '이탈율',
  `new_care_requests` bigint NOT NULL DEFAULT '0',
  `completed_cares` bigint NOT NULL DEFAULT '0',
  `cancelled_cares` bigint NOT NULL DEFAULT '0',
  `care_completion_rate` decimal(5,2) NOT NULL DEFAULT '0.00',
  `total_revenue` decimal(15,2) NOT NULL DEFAULT '0.00',
  `transaction_count` bigint NOT NULL DEFAULT '0',
  `avg_transaction` decimal(15,2) NOT NULL DEFAULT '0.00',
  `new_posts` bigint NOT NULL DEFAULT '0',
  `new_meetups` bigint NOT NULL DEFAULT '0',
  `meetup_participants` bigint NOT NULL DEFAULT '0',
  `new_reports` bigint NOT NULL DEFAULT '0',
  `resolved_reports` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_monthly_year_month` (`year`,`month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='월간 통계 (무기한 보관)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `user_idx` bigint NOT NULL,
  `type` varchar(50) NOT NULL,
  `title` varchar(255) NOT NULL,
  `content` varchar(500) DEFAULT NULL,
  `related_id` bigint DEFAULT NULL,
  `related_type` varchar(50) DEFAULT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`idx`),
  KEY `fk_notifications_user` (`user_idx`),
  CONSTRAINT `fk_notifications_user` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pet_coin_escrow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pet_coin_escrow` (
  `idx` bigint NOT NULL AUTO_INCREMENT COMMENT '에스크로 ID',
  `care_request_idx` bigint NOT NULL COMMENT '펫케어 요청 ID',
  `care_application_idx` bigint DEFAULT NULL COMMENT '펫케어 지원 ID (거래 확정 시 생성)',
  `requester_idx` bigint NOT NULL COMMENT '요청자 ID',
  `provider_idx` bigint NOT NULL COMMENT '제공자 ID',
  `amount` int NOT NULL COMMENT '에스크로 금액 (코인 단위)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'HOLD' COMMENT '에스크로 상태 (HOLD: 보관중, RELEASED: 지급완료, REFUNDED: 환불완료)',
  `released_at` datetime DEFAULT NULL COMMENT '지급 시간',
  `refunded_at` datetime DEFAULT NULL COMMENT '환불 시간',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
  PRIMARY KEY (`idx`),
  UNIQUE KEY `uk_care_request` (`care_request_idx`),
  UNIQUE KEY `uk_escrow_care_request` (`care_request_idx`),
  KEY `idx_requester` (`requester_idx`),
  KEY `idx_provider` (`provider_idx`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`),
  KEY `care_application_idx` (`care_application_idx`),
  CONSTRAINT `pet_coin_escrow_ibfk_1` FOREIGN KEY (`care_request_idx`) REFERENCES `carerequest` (`idx`) ON DELETE CASCADE,
  CONSTRAINT `pet_coin_escrow_ibfk_2` FOREIGN KEY (`care_application_idx`) REFERENCES `careapplication` (`idx`) ON DELETE SET NULL,
  CONSTRAINT `pet_coin_escrow_ibfk_3` FOREIGN KEY (`requester_idx`) REFERENCES `users` (`idx`) ON DELETE CASCADE,
  CONSTRAINT `pet_coin_escrow_ibfk_4` FOREIGN KEY (`provider_idx`) REFERENCES `users` (`idx`) ON DELETE CASCADE,
  CONSTRAINT `chk_escrow_amount_positive` CHECK ((`amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='펫코인 에스크로 (거래 확정 시 임시 보관)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pet_coin_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pet_coin_transaction` (
  `idx` bigint NOT NULL AUTO_INCREMENT COMMENT '거래 내역 ID',
  `user_idx` bigint NOT NULL COMMENT '사용자 ID',
  `transaction_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '거래 타입 (CHARGE: 충전, DEDUCT: 차감, PAYOUT: 지급, REFUND: 환불)',
  `amount` int NOT NULL COMMENT '거래 금액 (코인 단위)',
  `balance_before` int NOT NULL COMMENT '거래 전 잔액',
  `balance_after` int NOT NULL COMMENT '거래 후 잔액',
  `related_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '관련 엔티티 타입 (CARE_REQUEST 등)',
  `related_idx` bigint DEFAULT NULL COMMENT '관련 엔티티 ID',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '거래 설명',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPLETED' COMMENT '거래 상태 (PENDING, COMPLETED, FAILED, CANCELLED)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
  PRIMARY KEY (`idx`),
  KEY `idx_user_idx` (`user_idx`),
  KEY `idx_transaction_type` (`transaction_type`),
  KEY `idx_related` (`related_type`,`related_idx`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_status` (`status`),
  CONSTRAINT `pet_coin_transaction_ibfk_1` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`) ON DELETE CASCADE,
  CONSTRAINT `chk_amount_positive` CHECK ((`amount` > 0)),
  CONSTRAINT `chk_balance_after` CHECK ((`balance_after` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=544 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='펫코인 거래 내역';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pet_vaccinations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pet_vaccinations` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `pet_idx` bigint NOT NULL,
  `vaccine_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `vaccinated_at` date DEFAULT NULL,
  `next_due` date DEFAULT NULL,
  `notes` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_deleted` tinyint(1) DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`idx`),
  KEY `idx_pet_vaccine_pet_idx` (`pet_idx`),
  KEY `idx_pet_vaccine_deleted` (`is_deleted`),
  CONSTRAINT `fk_pet_vaccine_pet` FOREIGN KEY (`pet_idx`) REFERENCES `pets` (`idx`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pets` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `user_idx` bigint NOT NULL,
  `pet_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `pet_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `breed` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gender` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `age` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `color` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `weight` decimal(5,2) DEFAULT NULL,
  `birth_date` date DEFAULT NULL,
  `is_neutered` tinyint(1) DEFAULT '0',
  `health_info` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `special_notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `profile_image_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_deleted` tinyint(1) DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`idx`),
  KEY `idx_pets_user` (`user_idx`),
  KEY `idx_pets_deleted` (`is_deleted`),
  KEY `idx_pets_type` (`pet_type`),
  KEY `idx_pets_breed` (`breed`),
  CONSTRAINT `fk_pets_user` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `place_interaction_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `place_interaction_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_idx` bigint DEFAULT NULL,
  `location_idx` bigint NOT NULL,
  `interaction_type` varchar(20) NOT NULL COMMENT 'VIEW | NAVIGATE | FAVORITE',
  `created_at` datetime NOT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_place_interaction` (`location_idx`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='장소 행동 로그 — popularity_score 계산용';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `target_type` enum('BOARD','COMMENT','MISSING_PET','PET_CARE_PROVIDER') NOT NULL,
  `target_idx` bigint NOT NULL,
  `reporter_idx` bigint NOT NULL,
  `reason` text NOT NULL,
  `status` enum('PENDING','RESOLVED','REJECTED') DEFAULT 'PENDING',
  `handled_by` bigint DEFAULT NULL,
  `handled_at` datetime DEFAULT NULL,
  `action_taken` enum('NONE','DELETE_CONTENT','SUSPEND_USER','WARN_USER','OTHER') DEFAULT 'NONE',
  `admin_note` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`idx`),
  UNIQUE KEY `target_type` (`target_type`,`target_idx`,`reporter_idx`),
  KEY `reporter_idx` (`reporter_idx`),
  KEY `handled_by` (`handled_by`),
  CONSTRAINT `report_ibfk_1` FOREIGN KEY (`reporter_idx`) REFERENCES `users` (`idx`),
  CONSTRAINT `report_ibfk_2` FOREIGN KEY (`handled_by`) REFERENCES `users` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `signal_interaction_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `signal_interaction_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_idx` bigint NOT NULL,
  `signal_id` bigint NOT NULL COMMENT 'user_pet_intent_signal.id',
  `intent_domain` varchar(50) NOT NULL,
  `target_tab` varchar(30) DEFAULT NULL COMMENT 'location | care | meetup | missingPet',
  `target_category` varchar(100) DEFAULT NULL,
  `interaction_type` varchar(20) NOT NULL COMMENT 'CLICKED | DISMISSED | CONVERTED',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_signal_log_user` (`user_idx`,`created_at`),
  KEY `idx_signal_log_signal` (`signal_id`),
  KEY `idx_signal_log_domain` (`intent_domain`,`interaction_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='추천 카드 상호작용 로그 — threshold 튜닝 및 카드 문구 개선 근거';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `socialuser`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `socialuser` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `users_idx` bigint NOT NULL,
  `provider` varchar(25) NOT NULL,
  `provider_id` varchar(255) NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `provider_data` text COMMENT 'Provider별 원본 데이터 JSON (모든 OAuth2 응답 데이터 저장)',
  `provider_profile_image` varchar(500) DEFAULT NULL COMMENT 'Provider별 프로필 이미지 URL',
  `provider_name` varchar(100) DEFAULT NULL COMMENT 'Provider별 이름 (구글: given_name + family_name, 네이버: name)',
  `provider_phone` varchar(50) DEFAULT NULL COMMENT 'Provider별 전화번호 (네이버: mobile 또는 mobile_e164)',
  `provider_age_range` varchar(20) DEFAULT NULL COMMENT 'Provider별 나이대 (네이버: 20-29 형식)',
  PRIMARY KEY (`idx`),
  UNIQUE KEY `uk_socialuser_provider_providerid` (`provider`,`provider_id`),
  KEY `users_idx` (`users_idx`),
  CONSTRAINT `socialuser_ibfk_1` FOREIGN KEY (`users_idx`) REFERENCES `users` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=225 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_config` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_value` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`idx`),
  UNIQUE KEY `idx_system_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_pet_intent_signal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_pet_intent_signal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_idx` bigint NOT NULL,
  `source_type` varchar(30) NOT NULL COMMENT 'COMMUNITY | CARE | LOCATION_SEARCH',
  `source_id` bigint DEFAULT NULL,
  `intent_domain` varchar(50) NOT NULL,
  `intent` varchar(50) NOT NULL,
  `recommended_categories` json DEFAULT NULL,
  `confidence` double NOT NULL,
  `urgency` varchar(10) DEFAULT NULL COMMENT 'HIGH | NORMAL | LOW (NLP urgency_rules 결과)',
  `intent_tags` json DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'BaseTimeEntity (@LastModifiedDate)',
  `expires_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_signal_active` (`user_idx`,`expires_at`,`created_at`),
  KEY `idx_signal_source` (`source_type`,`source_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사용자 반려생활 의도 signal (원문 저장 없음, TTL 7일)';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_sanctions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_sanctions` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `user_idx` bigint NOT NULL,
  `sanction_type` varchar(20) NOT NULL,
  `reason` varchar(500) NOT NULL,
  `duration_days` int DEFAULT NULL,
  `starts_at` datetime NOT NULL,
  `ends_at` datetime DEFAULT NULL,
  `admin_idx` bigint DEFAULT NULL,
  `report_idx` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`idx`),
  KEY `admin_idx` (`admin_idx`),
  KEY `idx_user_idx` (`user_idx`),
  KEY `idx_ends_at` (`ends_at`),
  CONSTRAINT `user_sanctions_ibfk_1` FOREIGN KEY (`user_idx`) REFERENCES `users` (`idx`),
  CONSTRAINT `user_sanctions_ibfk_2` FOREIGN KEY (`admin_idx`) REFERENCES `users` (`idx`)
) ENGINE=InnoDB AUTO_INCREMENT=280 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `idx` bigint NOT NULL AUTO_INCREMENT,
  `id` varchar(255) NOT NULL,
  `username` varchar(255) NOT NULL,
  `email` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `role` enum('USER','SERVICE_PROVIDER','ADMIN','MASTER') DEFAULT 'USER',
  `location` varchar(255) DEFAULT NULL,
  `pet_info` longtext,
  `last_login_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `refresh_token` varchar(255) DEFAULT NULL,
  `refresh_expiration` datetime DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `warning_count` int NOT NULL DEFAULT '0',
  `pet_coin_balance` int NOT NULL DEFAULT '0' COMMENT '펫코인 잔액',
  `suspended_until` datetime DEFAULT NULL,
  `is_deleted` tinyint(1) DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `is_dormant` tinyint(1) DEFAULT '0',
  `dormant_at` datetime DEFAULT NULL,
  `profile_image` varchar(500) DEFAULT NULL COMMENT '프로필 이미지 URL (구글 picture, 네이버 profile_image)',
  `birth_date` varchar(20) DEFAULT NULL COMMENT '생년월일 (네이버: birthyear + birthday 조합, 형식: YYYY-MM-DD)',
  `gender` varchar(10) DEFAULT NULL COMMENT '성별 (네이버: M/F, 구글: 제공 안 함)',
  `email_verified` tinyint(1) DEFAULT '0' COMMENT '이메일 인증 여부 (구글: email_verified, 네이버: 기본 true)',
  `nickname` varchar(50) DEFAULT NULL COMMENT '닉네임 (소셜 로그인 사용자 필수 설정)',
  PRIMARY KEY (`idx`),
  UNIQUE KEY `id` (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `uk_users_nickname` (`nickname`),
  CONSTRAINT `chk_pet_coin_balance` CHECK ((`pet_coin_balance` >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=17882 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `weekly_statistics`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `weekly_statistics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `year` int NOT NULL COMMENT 'ISO 연도',
  `week_number` int NOT NULL COMMENT 'ISO 주차 (1~53)',
  `start_date` date NOT NULL COMMENT '해당 주 월요일',
  `end_date` date NOT NULL COMMENT '해당 주 일요일',
  `new_users` bigint NOT NULL DEFAULT '0',
  `active_users` bigint NOT NULL DEFAULT '0' COMMENT 'WAU',
  `new_providers` bigint NOT NULL DEFAULT '0',
  `weekly_retention_rate` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '주간 재방문율',
  `new_care_requests` bigint NOT NULL DEFAULT '0',
  `completed_cares` bigint NOT NULL DEFAULT '0',
  `cancelled_cares` bigint NOT NULL DEFAULT '0',
  `care_completion_rate` decimal(5,2) NOT NULL DEFAULT '0.00',
  `total_revenue` decimal(15,2) NOT NULL DEFAULT '0.00',
  `transaction_count` bigint NOT NULL DEFAULT '0',
  `avg_transaction` decimal(15,2) NOT NULL DEFAULT '0.00',
  `new_posts` bigint NOT NULL DEFAULT '0',
  `new_meetups` bigint NOT NULL DEFAULT '0',
  `meetup_participants` bigint NOT NULL DEFAULT '0',
  `new_reports` bigint NOT NULL DEFAULT '0',
  `resolved_reports` bigint NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_weekly_year_week` (`year`,`week_number`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='주간 통계 (무기한 보관)';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

