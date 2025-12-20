# Board 도메인 코드 중복 및 일관성 문제

## 📋 요약

**문제**: 게시글 매핑 로직에서 중복 코드와 일관성 부족

**문제점**:
- 단일 조회와 배치 조회 로직 중복
- Object[] 파싱 로직 중복
- 반응 카운트/첨부파일 설정 로직 중복
- 관리자 조회에서 비효율적인 메모리 필터링

**해결 방안**:
- 공통 로직 추출
- 단일 조회를 배치 조회로 통합
- DB 레벨 필터링으로 성능 개선

**상태**: ✅ 부분 완료 (공통 로직 추출 및 단일/배치 통합 완료, DB 레벨 필터링은 미완료)

---

## 1. 문제 상황

### 1.1 코드 중복: Object[] 파싱 로직

**위치**: `BoardService.mapReactionCounts()`, `BoardService.getReactionCountsBatch()`

**문제 코드**:
```java
// mapReactionCounts() - line 441-450
for (Object[] result : results) {
    ReactionType reactionType = (ReactionType) result[1];
    Long count = ((Number) result[2]).longValue();
    
    if (reactionType == ReactionType.LIKE) {
        likeCount = count;
    } else if (reactionType == ReactionType.DISLIKE) {
        dislikeCount = count;
    }
}

// getReactionCountsBatch() - line 529-536 (동일한 로직!)
for (Object[] result : results) {
    Long boardId = ((Number) result[0]).longValue();
    ReactionType reactionType = (ReactionType) result[1];
    Long count = ((Number) result[2]).longValue();
    
    countsMap.computeIfAbsent(boardId, k -> new HashMap<>())
            .put(reactionType, count);
}
```

**문제점**:
- Object[] 파싱 로직이 두 메서드에 중복
- `result[1]`, `result[2]` 같은 인덱스 접근이 반복됨
- 새로운 ReactionType 추가 시 두 곳 모두 수정 필요

**영향**:
- 코드 유지보수성 저하
- 버그 발생 가능성 증가 (한 곳만 수정 시)
- 가독성 저하

### 1.2 코드 중복: 반응 카운트 설정 로직

**위치**: `BoardService.mapReactionCounts()`, `BoardService.mapBoardsWithReactionsBatch()`

**문제 코드**:
```java
// mapReactionCounts() - line 452-453
dto.setLikes(Math.toIntExact(likeCount));
dto.setDislikes(Math.toIntExact(dislikeCount));

// mapBoardsWithReactionsBatch() - line 493-494 (동일한 로직!)
dto.setLikes(Math.toIntExact(counts.getOrDefault(ReactionType.LIKE, 0L)));
dto.setDislikes(Math.toIntExact(counts.getOrDefault(ReactionType.DISLIKE, 0L)));
```

**문제점**:
- 반응 카운트를 DTO에 설정하는 로직이 중복
- 형변환 로직 (`Math.toIntExact`) 중복
- 기본값 처리 방식이 다름 (단일: 0으로 초기화, 배치: getOrDefault)

**영향**:
- 로직 변경 시 여러 곳 수정 필요
- 불일치 발생 가능성

### 1.3 코드 중복: 첨부파일 설정 로직

**위치**: `BoardService.mapAttachmentInfo()`, `BoardService.mapBoardsWithReactionsBatch()`

**문제 코드**:
```java
// mapAttachmentInfo() - line 460-462
List<FileDTO> attachments = attachmentFileService.getAttachments(FileTargetType.BOARD, boardId);
dto.setAttachments(attachments);
dto.setBoardFilePath(extractPrimaryFileUrl(attachments));

// mapBoardsWithReactionsBatch() - line 497-500 (동일한 로직!)
List<FileDTO> attachments = attachmentsMap.getOrDefault(
        board.getIdx(), new ArrayList<>());
dto.setAttachments(attachments);
dto.setBoardFilePath(extractPrimaryFileUrl(attachments));
```

**문제점**:
- 첨부파일 조회 및 설정 로직이 중복
- 단일 조회는 직접 조회, 배치 조회는 Map에서 조회하는 방식만 다름

**영향**:
- 로직 변경 시 두 곳 모두 수정 필요
- 불일치 발생 가능성

### 1.4 일관성 부족: 단일/배치 조회 분리

**위치**: `BoardService.mapBoardWithDetails()`, `BoardService.mapBoardsWithReactionsBatch()`

**문제 상황**:
```java
// 단일 조회: mapBoardWithDetails()
private BoardDTO mapBoardWithDetails(Board board) {
    BoardDTO dto = boardConverter.toDTO(board);
    mapReactionCounts(dto, board.getIdx());  // 단일 조회용 메서드
    mapAttachmentInfo(dto, board.getIdx());  // 단일 조회용 메서드
    return dto;
}

// 배치 조회: mapBoardsWithReactionsBatch()
private List<BoardDTO> mapBoardsWithReactionsBatch(List<Board> boards) {
    // 배치 조회로 최적화
    Map<Long, Map<ReactionType, Long>> reactionCountsMap = getReactionCountsBatch(boardIds);
    Map<Long, List<FileDTO>> attachmentsMap = attachmentFileService.getAttachmentsBatch(...);
    
    // 스트림으로 처리
    return boards.stream().map(board -> {
        // 내부에서 직접 처리
    }).collect(Collectors.toList());
}
```

**문제점**:
- 단일 조회와 배치 조회가 완전히 다른 방식으로 구현
- 단일 조회도 배치 메서드를 활용할 수 있음 (`List.of(boardId)`)
- 배치 조회의 최적화 혜택을 단일 조회가 받지 못함

**영향**:
- 코드 일관성 부족
- 단일 조회 시 불필요한 쿼리 발생 (최적화 미적용)
- 유지보수 복잡도 증가

### 1.5 성능 이슈: 메모리 필터링

**위치**: `BoardService.getAdminBoardsWithPaging()` (line 77-167)

**문제 코드**:
```java
// 1000개를 한 번에 조회
Pageable largePageable = PageRequest.of(0, 1000);
Page<Board> boardPage = boardRepository.findAll(largePageable);

// 메모리에서 필터링 (line 95-127)
List<Board> filteredBoards = boardPage.getContent().stream()
        .filter(board -> {
            // 카테고리 필터
            if (category != null && !category.equals("ALL") ...) return false;
            // 상태 필터
            if (status != null && !status.equals("ALL") ...) return false;
            // 삭제 여부 필터
            if (deleted != null ...) return false;
            // 검색어 필터
            if (q != null && !q.isBlank() ...) return false;
            return true;
        })
        .collect(Collectors.toList());

// 메모리에서 페이징 (line 130-134)
int start = page * size;
int end = Math.min(start + size, filteredBoards.size());
List<Board> pagedBoards = start < filteredBoards.size()
        ? filteredBoards.subList(start, end)
        : new ArrayList<>();
```

**문제점**:
- DB에서 1000개를 조회한 후 메모리에서 필터링
- 페이징도 메모리에서 처리
- DB 레벨 필터링에 비해 매우 비효율적

**영향**:
- 불필요한 데이터 조회로 인한 네트워크/메모리 사용량 증가
- 필터링 결과가 적을수록 더 비효율적
- 게시글이 많아질수록 성능 저하
- DB 인덱스 활용 불가

---

## 2. 원인 분석

### 2.1 리팩토링 미흡

- 기능 추가 시 기존 코드 재사용보다 복사-붙여넣기
- 공통 로직 추출이 되지 않음

### 2.2 단일/배치 로직 분리

- 단일 조회와 배치 조회를 별도로 구현
- 단일 조회도 배치 조회의 최적화를 활용할 수 있음

### 2.3 성능 고려 부족

- `getAdminBoardsWithPaging`에서 빠른 구현을 위해 메모리 필터링 선택
- DB 쿼리 최적화를 고려하지 않음

---

## 3. 해결 방안

### 3.1 공통 로직 추출

#### 3.1.1 Object[] 파싱 공통화

```java
/**
 * 반응 카운트 조회 결과를 Map으로 변환
 * @param results Repository에서 반환된 Object[] 리스트
 * @return Map<ReactionType, Count>
 */
private Map<ReactionType, Long> parseReactionCountResults(List<Object[]> results) {
    Map<ReactionType, Long> counts = new HashMap<>();
    for (Object[] result : results) {
        ReactionType reactionType = (ReactionType) result[1];
        Long count = ((Number) result[2]).longValue();
        counts.put(reactionType, count);
    }
    return counts;
}

/**
 * 배치 조회 결과를 Map으로 변환
 * @param results Repository에서 반환된 Object[] 리스트
 * @return Map<BoardId, Map<ReactionType, Count>>
 */
private Map<Long, Map<ReactionType, Long>> parseBatchReactionCountResults(List<Object[]> results) {
    Map<Long, Map<ReactionType, Long>> countsMap = new HashMap<>();
    for (Object[] result : results) {
        Long boardId = ((Number) result[0]).longValue();
        ReactionType reactionType = (ReactionType) result[1];
        Long count = ((Number) result[2]).longValue();
        
        countsMap.computeIfAbsent(boardId, k -> new HashMap<>())
                .put(reactionType, count);
    }
    return countsMap;
}
```

#### 3.1.2 반응 카운트 적용 공통화

```java
/**
 * BoardDTO에 반응 카운트 적용
 */
private void applyReactionCounts(BoardDTO dto, Map<ReactionType, Long> counts) {
    dto.setLikes(Math.toIntExact(counts.getOrDefault(ReactionType.LIKE, 0L)));
    dto.setDislikes(Math.toIntExact(counts.getOrDefault(ReactionType.DISLIKE, 0L)));
}
```

#### 3.1.3 첨부파일 적용 공통화

```java
/**
 * BoardDTO에 첨부파일 정보 적용 (배치 조회용)
 */
private void applyAttachmentInfo(BoardDTO dto, Long boardId, Map<Long, List<FileDTO>> attachmentsMap) {
    List<FileDTO> attachments = attachmentsMap.getOrDefault(boardId, new ArrayList<>());
    dto.setAttachments(attachments);
    dto.setBoardFilePath(extractPrimaryFileUrl(attachments));
}
```

### 3.2 단일/배치 조회 통합

```java
/**
 * 단일 게시글에 상세 정보 매핑 (배치 조회 활용)
 */
private BoardDTO mapBoardWithDetails(Board board) {
    List<BoardDTO> results = mapBoardsWithReactionsBatch(List.of(board));
    return results.isEmpty() ? boardConverter.toDTO(board) : results.get(0);
}
```

**효과**:
- 단일 조회도 배치 조회의 최적화 혜택
- 코드 중복 제거
- 일관성 확보

### 3.3 성능 개선: DB 레벨 필터링

#### 3.3.1 Specification 패턴 사용

```java
public BoardPageResponseDTO getAdminBoardsWithPaging(
        String status, Boolean deleted, String category, String q, int page, int size) {
    
    // Specification으로 동적 쿼리 구성
    Specification<Board> spec = Specification.where(null);
    
    if (deleted != null) {
        spec = spec.and((root, query, cb) -> 
            cb.equal(root.get("isDeleted"), deleted));
    }
    
    if (category != null && !category.equals("ALL")) {
        spec = spec.and((root, query, cb) -> 
            cb.equal(root.get("category"), category));
    }
    
    if (status != null && !status.equals("ALL")) {
        spec = spec.and((root, query, cb) -> 
            cb.equal(root.get("status"), ContentStatus.valueOf(status)));
    }
    
    if (q != null && !q.isBlank()) {
        String keyword = "%" + q.toLowerCase() + "%";
        spec = spec.and((root, query, cb) -> 
            cb.or(
                cb.like(cb.lower(root.get("title")), keyword),
                cb.like(cb.lower(root.get("content")), keyword),
                cb.like(cb.lower(root.join("user").get("username")), keyword)
            ));
    }
    
    Pageable pageable = PageRequest.of(page, size);
    Page<Board> boardPage = boardRepository.findAll(spec, pageable);
    
    // 배치 조회로 N+1 문제 해결
    List<BoardDTO> boardDTOs = mapBoardsWithReactionsBatch(boardPage.getContent());
    
    return BoardPageResponseDTO.builder()
            .boards(boardDTOs)
            .totalCount(boardPage.getTotalElements())
            .totalPages(boardPage.getTotalPages())
            .currentPage(page)
            .pageSize(size)
            .hasNext(boardPage.hasNext())
            .hasPrevious(boardPage.hasPrevious())
            .build();
}
```

**효과**:
- DB 레벨에서 필터링 및 페이징 처리
- 불필요한 데이터 조회 제거
- 인덱스 활용 가능
- 성능 대폭 개선

### 3.4 리팩토링 후 구조

```java
// 공통 메서드들
private Map<ReactionType, Long> parseReactionCountResults(List<Object[]> results)
private Map<Long, Map<ReactionType, Long>> parseBatchReactionCountResults(List<Object[]> results)
private void applyReactionCounts(BoardDTO dto, Map<ReactionType, Long> counts)
private void applyAttachmentInfo(BoardDTO dto, Long boardId, Map<Long, List<FileDTO>> attachmentsMap)

// 단일 조회 (배치 조회 활용)
private BoardDTO mapBoardWithDetails(Board board) {
    return mapBoardsWithReactionsBatch(List.of(board)).get(0);
}

// 배치 조회 (공통 메서드 활용)
private List<BoardDTO> mapBoardsWithReactionsBatch(List<Board> boards) {
    // getReactionCountsBatch 내부에서 parseBatchReactionCountResults 사용
    // 반복문에서 applyReactionCounts, applyAttachmentInfo 사용
}
```

---

## 4. 개선 효과

### 4.1 코드 품질 (✅ 완료)

| 항목 | Before | After |
|------|--------|-------|
| **중복 코드** | 약 50줄 중복 | ✅ 제거됨 (공통 메서드 추출) |
| **메서드 구조** | 중복 로직 산재 | ✅ 공통 메서드 4개 추가 |
| **일관성** | 단일/배치 분리 | ✅ 통합 (mapBoardWithDetails → mapBoardsWithReactionsBatch 활용) |
| **유지보수성** | 낮음 (여러 곳 수정 필요) | ✅ 향상 (한 곳만 수정) |
| **코드 라인 수** | 중복 포함 약 150줄 | ✅ 공통화로 약 120줄 (중복 제거) |

**추가된 공통 메서드:**
- `parseReactionCountResults()`: Object[] 파싱 공통화
- `parseBatchReactionCountResults()`: 배치 조회 Object[] 파싱 공통화
- `applyReactionCounts()`: 반응 카운트 설정 공통화
- `applyAttachmentInfo()`: 첨부파일 설정 공통화

### 4.2 성능

| 항목 | Before | After | 상태 |
|------|--------|-------|------|
| **단일 조회** | 별도 쿼리 (mapReactionCounts, mapAttachmentInfo) | ✅ 배치 최적화 활용 | ✅ 완료 |
| **쿼리 일관성** | 단일/배치 다른 방식 | ✅ 통일된 배치 방식 | ✅ 완료 |
| **관리자 조회** | 메모리 필터링 (1000건 조회) | ⚠️ 메모리 필터링 (1000건 조회) | 🔄 미완료 |

**참고:** 관리자 조회(DB 레벨 필터링)는 아직 개선하지 않음 (검색 로직이 완전히 정리된 후 진행 예정)

### 4.3 실제 개선량

- **코드 중복**: ✅ 약 30-40줄 감소
- **공통 메서드**: ✅ 4개 추가 (재사용 가능)
- **단일 조회 최적화**: ✅ 배치 조회 방식으로 통합
- **유지보수성**: ✅ 로직 변경 시 한 곳만 수정하면 됨 (버그 수정 시간 약 50% 감소 예상)
- **관리자 조회 성능**: 🔄 향후 개선 예정 (DB 레벨 필터링)

---

## 5. 성능 테스트 결과

### 5.1 테스트 코드

**파일**: `backend/test/java/com/linkup/Petory/domain/board/service/BoardServiceAdminPagingPerformanceTest.java`

**테스트 시나리오**:
- 500개의 게시글 생성 (다양한 카테고리, 상태, 삭제 여부)
- 필터링 조건: status=ACTIVE, deleted=false, category=FREE
- 페이지 크기: 20

### 5.2 Before (메모리 필터링) - 현재 상태

**측정 항목**:
- 실행 시간
- 메모리 사용량
- 힙 크기 변화
- DB에서 조회한 게시글 수 vs 반환된 게시글 수

**예상 결과**:
```
┌──────────────────────────────────────────────────────────┐
│ 성능 측정 결과 (현재 상태: 메모리 필터링)                  │
├──────────────────────────────────────────────────────────┤
│ 실행 시간:            XXX ms (X.XXX 초)                   │
│ 메모리 사용량:        XXX bytes (XX.XX MB)                │
│ 힙 크기 변화:         XXX bytes (XX.XX MB)                │
│ 조회된 게시글 수:     1000 건 (DB에서 1000개 조회)         │
│ 반환된 게시글 수:     XX 건 (필터링 후)                    │
└──────────────────────────────────────────────────────────┘
```

**문제점**:
- ⚠️ DB에서 1000개를 조회한 후 메모리에서 필터링
- ⚠️ 불필요한 데이터 조회로 네트워크/메모리 낭비
- ⚠️ 필터링 결과가 적을수록 더 비효율적

### 5.3 After (DB 레벨 필터링) - 개선 후

**측정 항목**:
- 실행 시간
- 메모리 사용량
- 힙 크기 변화
- DB에서 조회한 게시글 수 vs 반환된 게시글 수

**예상 결과**:
```
┌──────────────────────────────────────────────────────────┐
│ 성능 측정 결과 (개선 후: DB 레벨 필터링)                  │
├──────────────────────────────────────────────────────────┤
│ 실행 시간:            XXX ms (X.XXX 초)                   │
│ 메모리 사용량:        XXX bytes (XX.XX MB)                │
│ 힙 크기 변화:         XXX bytes (XX.XX MB)                │
│ 조회된 게시글 수:     XX 건 (DB에서 필터링 후 조회)        │
│ 반환된 게시글 수:     XX 건 (필터링 후)                    │
└──────────────────────────────────────────────────────────┘
```

**개선 효과**:
- ✅ DB 레벨에서 필터링 및 페이징 처리
- ✅ 필요한 데이터만 조회 (불필요한 데이터 제거)
- ✅ 인덱스 활용 가능
- ✅ 메모리 사용량 감소

### 5.4 Before/After 비교

**실제 테스트 실행 후 아래에 결과를 추가**:

```
┌──────────────────────────────────────────────────────────┐
│                    성능 비교                             │
├──────────────────────────────────────────────────────────┤
│                    Before (메모리)    After (DB)     개선율  │
├──────────────────────────────────────────────────────────┤
│ 실행 시간:            XXX ms        XXX ms        XX.X%   │
│ 메모리 사용량:        XX.XX MB      XX.XX MB      XX.X%   │
│ 조회된 게시글 수:     1000 건        XX 건                 │
└──────────────────────────────────────────────────────────┘
```

**테스트 실행 방법**:
```bash
# 특정 테스트만 실행
./gradlew test --tests BoardServiceAdminPagingPerformanceTest.testPerformanceComparison

# 또는 IDE에서 직접 실행
```

**참고**: 
- 실제 환경에서는 게시글이 더 많을수록 성능 차이가 더 커집니다
- 네트워크 지연 시간도 고려해야 합니다

---

## 6. 핵심 포인트

### 개선 우선순위

1. **높음**: 공통 로직 추출 (코드 중복 제거)
2. **높음**: 단일/배치 조회 통합 (일관성 확보)
3. **중간**: DB 레벨 필터링 (성능 개선)
4. **낮음**: 코드 가독성 개선

### 주의사항

- 리팩토링 시 기존 동작 검증 필요
- 단일 조회를 배치로 통합할 때 성능 테스트 필요
- Specification 패턴 적용 시 Repository 수정 필요

---

## 7. 참고 자료

- 관련 파일: `backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java`
- 트러블슈팅 체크리스트: `docs/troubleshooting/도메인별_트러블슈팅_체크리스트.md` (4. Board 도메인)

