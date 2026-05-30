package com.linkup.Petory.domain.board.converter;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardPopularitySnapshot;
import com.linkup.Petory.domain.board.entity.PopularityPeriodType;
import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BoardPopularitySnapshotConverter N+1 회귀 테스트
 *
 * 문제: toDTOList() 내부에서 각 스냅샷마다 getAttachments()를 단건 호출 → N+1 쿼리
 * 원인: resolvePrimaryFileUrl()이 getAttachments(targetType, boardId)를 직접 호출
 * 해결: toDTOList()에서 getAttachmentsBatch()로 선조회 후 Map 전달
 */
@ExtendWith(MockitoExtension.class)
class BoardPopularitySnapshotConverterTest {

    @Mock
    AttachmentFileService attachmentFileService;

    @InjectMocks
    BoardPopularitySnapshotConverter converter;

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    private Board board(long id) {
        return Board.builder().idx(id).title("title-" + id).category("CAT").build();
    }

    private BoardPopularitySnapshot snapshot(long id, Board board) {
        return BoardPopularitySnapshot.builder()
                .snapshotId(id)
                .board(board)
                .periodType(PopularityPeriodType.WEEKLY)
                .periodStartDate(LocalDate.of(2026, 5, 19))
                .periodEndDate(LocalDate.of(2026, 5, 25))
                .ranking((int) id)
                .popularityScore(100)
                .likeCount(10)
                .commentCount(5)
                .viewCount(200)
                .build();
    }

    private FileDTO fileDto(long boardId, String url) {
        return FileDTO.builder()
                .idx(boardId * 10)
                .targetType(FileTargetType.BOARD)
                .targetIdx(boardId)
                .filePath("/uploads/img-" + boardId + ".jpg")
                .downloadUrl(url)
                .build();
    }

    // ──────────────────────────────────────────────
    // N+1 회귀 테스트 (핵심)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("[N+1 방지] toDTOList는 getAttachments를 단 한 번도 호출하지 않는다")
    void toDTOList_neverCallsSingleGetAttachments() {
        // given
        List<BoardPopularitySnapshot> snapshots = List.of(
                snapshot(1L, board(1L)),
                snapshot(2L, board(2L)),
                snapshot(3L, board(3L))
        );
        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Map.of());

        // when
        converter.toDTOList(snapshots);

        // then — 단건 조회 0번. 이게 깨지면 N+1 재발.
        verify(attachmentFileService, never()).getAttachments(any(), any());
    }

    @Test
    @DisplayName("[배치 조회] toDTOList는 getAttachmentsBatch를 정확히 1번 호출한다")
    void toDTOList_callsGetAttachmentsBatchExactlyOnce() {
        // given: 스냅샷 3개 — 조회는 1번이어야 한다
        List<BoardPopularitySnapshot> snapshots = List.of(
                snapshot(1L, board(1L)),
                snapshot(2L, board(2L)),
                snapshot(3L, board(3L))
        );
        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Map.of());

        // when
        converter.toDTOList(snapshots);

        // then
        verify(attachmentFileService, times(1))
                .getAttachmentsBatch(eq(FileTargetType.BOARD), anyList());
    }

    @Test
    @DisplayName("[boardId 전달] 배치 조회에 모든 boardId가 포함된다")
    void toDTOList_passesAllBoardIdsToGetAttachmentsBatch() {
        // given
        List<BoardPopularitySnapshot> snapshots = List.of(
                snapshot(1L, board(10L)),
                snapshot(2L, board(20L)),
                snapshot(3L, board(30L))
        );
        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Map.of());

        // when
        converter.toDTOList(snapshots);

        // then
        verify(attachmentFileService).getAttachmentsBatch(
                eq(FileTargetType.BOARD),
                argThat(ids -> ids.containsAll(List.of(10L, 20L, 30L)) && ids.size() == 3)
        );
    }

    // ──────────────────────────────────────────────
    // URL 추출 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("[URL] 배치 결과에서 첫 번째 파일의 downloadUrl이 boardFilePath에 세팅된다")
    void toDTOList_picksFirstFileUrlFromBatch() {
        // given
        Board b = board(1L);
        FileDTO file = fileDto(1L, "https://cdn.example.com/img-1.jpg");

        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Map.of(1L, List.of(file)));

        // when
        var results = converter.toDTOList(List.of(snapshot(1L, b)));

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).boardFilePath()).isEqualTo("https://cdn.example.com/img-1.jpg");
    }

    @Test
    @DisplayName("[URL fallback] downloadUrl이 없으면 buildDownloadUrl로 fallback된다")
    void toDTOList_fallsBackToBuildDownloadUrl_whenDownloadUrlBlank() {
        // given
        Board b = board(1L);
        FileDTO fileWithNoUrl = FileDTO.builder()
                .targetIdx(1L)
                .filePath("/uploads/img-1.jpg")
                .downloadUrl("")   // 빈 문자열
                .build();

        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Map.of(1L, List.of(fileWithNoUrl)));
        when(attachmentFileService.buildDownloadUrl("/uploads/img-1.jpg"))
                .thenReturn("https://cdn.example.com/uploads/img-1.jpg");

        // when
        var results = converter.toDTOList(List.of(snapshot(1L, b)));

        // then
        assertThat(results.get(0).boardFilePath())
                .isEqualTo("https://cdn.example.com/uploads/img-1.jpg");
    }

    // ──────────────────────────────────────────────
    // 엣지 케이스
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("[빈 리스트] 빈 목록 입력 시 getAttachmentsBatch를 호출하지 않는다")
    void toDTOList_emptyInput_doesNotCallBatch() {
        // when
        var results = converter.toDTOList(List.of());

        // then
        assertThat(results).isEmpty();
        verifyNoInteractions(attachmentFileService);
    }

    @Test
    @DisplayName("[null board] board가 null인 스냅샷은 boardFilePath=null로 정상 변환된다")
    void toDTOList_nullBoardSnapshot_convertsGracefully() {
        // given: board가 null인 스냅샷
        BoardPopularitySnapshot nullBoardSnapshot = BoardPopularitySnapshot.builder()
                .snapshotId(99L)
                .board(null)
                .periodType(PopularityPeriodType.WEEKLY)
                .periodStartDate(LocalDate.of(2026, 5, 19))
                .periodEndDate(LocalDate.of(2026, 5, 25))
                .ranking(1).popularityScore(0).likeCount(0).commentCount(0).viewCount(0)
                .build();

        when(attachmentFileService.getAttachmentsBatch(any(), anyList()))
                .thenReturn(Map.of());

        // when
        var results = converter.toDTOList(List.of(nullBoardSnapshot));

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).boardFilePath()).isNull();
        assertThat(results.get(0).boardId()).isNull();
    }

    @Test
    @DisplayName("[첨부파일 없음] 배치 결과에 해당 boardId 없으면 boardFilePath=null")
    void toDTOList_noFileInBatch_boardFilePathIsNull() {
        // given: 배치 결과에 해당 boardId 없음
        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Map.of()); // 빈 Map

        // when
        var results = converter.toDTOList(List.of(snapshot(1L, board(1L))));

        // then
        assertThat(results.get(0).boardFilePath()).isNull();
    }

    // ── 단건 toDTO 버그 회귀 ──────────────────────────────────────────────────

    @Test
    @DisplayName("단건 toDTO: boardFilePath 항상 null — attachmentFileService 미호출 (버그 확인)")
    void toDTO_단건_boardFilePath_항상null() {
        // given: 첨부파일이 있는 게시글 스냅샷
        Board board = board(10L);
        BoardPopularitySnapshot snap = snapshot(1L, board);

        // when: 단건 오버로드 호출
        var dto = converter.toDTO(snap);

        // then: ★ 버그 — null 하드코딩, 이미지 URL 항상 null
        assertThat(dto.boardFilePath()).isNull();

        // toDTOList는 getAttachmentsBatch를 호출하지만 단건 toDTO는 호출하지 않음
        verify(attachmentFileService, never())
                .getAttachmentsBatch(any(), any());
        verify(attachmentFileService, never())
                .getAttachments(any(), any());
    }

    @Test
    @DisplayName("단건 toDTO vs toDTOList: 동일 스냅샷에서 이미지 URL 불일치 (버그 확인)")
    void toDTO_단건_vs_배치_이미지URL_불일치() {
        // given: 첨부파일 있는 스냅샷
        Board board = board(20L);
        BoardPopularitySnapshot snap = snapshot(2L, board);
        String expectedUrl = "https://cdn.example.com/image.jpg";

        FileDTO file = FileDTO.builder()
                .idx(1L).targetType(FileTargetType.BOARD).targetIdx(20L)
                .filePath(expectedUrl).downloadUrl(expectedUrl).fileType("image/jpeg").build();
        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Map.of(20L, List.of(file)));

        // when
        var singleDto = converter.toDTO(snap);                    // 단건
        var listDto = converter.toDTOList(List.of(snap)).get(0);  // 배치

        // then: 같은 데이터인데 호출 경로에 따라 이미지 유무가 달라짐
        assertThat(singleDto.boardFilePath()).isNull();        // 단건: 항상 null
        assertThat(listDto.boardFilePath()).isNotNull();       // 배치: 정상 조회
    }
}
