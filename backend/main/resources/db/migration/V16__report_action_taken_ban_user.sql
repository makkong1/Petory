-- 신고 처리 조치에 영구 차단(BAN_USER)을 추가한다.
--
-- UserSanctionService.addBan()은 이미 있었지만 신고 처리 흐름(ReportActionType) 어디에도
-- 연결돼 있지 않아 실제로는 호출할 방법이 없는 죽은 코드였다. 조치 옵션에 추가한다.
ALTER TABLE `report`
    MODIFY COLUMN `action_taken` enum('NONE','DELETE_CONTENT','SUSPEND_USER','WARN_USER','BAN_USER','OTHER') DEFAULT 'NONE';
