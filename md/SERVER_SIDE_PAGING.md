# 서버 사이드 페이징 구현 가이드

## 개요

게시글 목록 조회 및 검색 기능에 서버 사이드 페이징을 적용하여 성능을 최적화했습니다. 클라이언트에서 모든 데이터를 한 번에 로드하는 방식에서 서버에서 페이지 단위로 데이터를 제공하는 방식으로 변경했습니다.

## 목적

- **초기 로딩 속도 개선**: 초기 20개만 로드하여 첫 화면 렌더링 속도 향상
- **메모리 사용량 감소**: 필요한 만큼만 데이터를 로드하여 브라우저 메모리 사용량 감소
- **네트워크 트래픽 감소**: 전체 데이터 대신 페이지 단위로 전송하여 네트워크 부하 감소
- **확장성 향상**: 게시글이 많아져도 성능 저하 최소화

## 백엔드 구현

### 1. DTO 생성

**파일**: `backend/main/java/com/linkup/Petory/domain/board/dto/BoardPageResponseDTO.java`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardPageResponseDTO {
    private List<BoardDTO> boards;
    private long totalCount;
    private int totalPages;
    private int currentPage;
    private int pageSize;
    private boolean hasNext;
    private boolean hasPrevious;
}
```

**필드 설명**:
- `boards`: 현재 페이지의 게시글 목록
- `totalCount`: 전체 게시글 개수
- `totalPages`: 전체 페이지 수
- `currentPage`: 현재 페이지 번호 (0부터 시작)
- `pageSize`: 페이지당 게시글 수
- `hasNext`: 다음 페이지 존재 여부
- `hasPrevious`: 이전 페이지 존재 여부

### 2. Repository 수정

**파일**: `backend/main/java/com/linkup/Petory/domain/board/repository/BoardRepository.java`

**추가된 메서드**:
```java
// 전체 게시글 조회 (페이징)
Page<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

// 카테고리별 게시글 조회 (페이징)
Page<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(String category, Pageable pageable);

// 제목으로 검색 (페이징)
Page<Board> findByTitleContainingAndIsDeletedFalseOrderByCreatedAtDesc(String title, Pageable pageable);

// 내용으로 검색 (페이징)
Page<Board> findByContentContainingAndIsDeletedFalseOrderByCreatedAtDesc(String content, Pageable pageable);

// FULLTEXT 검색 (페이징)
@Query(value = "SELECT b.*, MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) as relevance "
        + "FROM board b WHERE b.is_deleted = false "
        + "AND MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) "
        + "ORDER BY relevance DESC, b.created_at DESC", 
        countQuery = "SELECT COUNT(*) FROM board b WHERE b.is_deleted = false "
        + "AND MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE)",
        nativeQuery = true)
Page<Board> searchByKeywordWithPaging(@Param("kw") String keyword, Pageable pageable);
```

### 3. Service 수정

**파일**: `backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java`

**메서드**: `getAllBoardsWithPaging()`

```java
public BoardPageResponseDTO getAllBoardsWithPaging(String category, int page, int size) {
    Pageable pageable = PageRequest.of(page, size);
    Page<Board> boardPage;

    if (category != null && !category.equals("ALL")) {
        boardPage = boardRepository.findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(category, pageable);
    } else {
        boardPage = boardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc(pageable);
    }

    if (boardPage.isEmpty()) {
        return BoardPageResponseDTO.builder()
                .boards(new ArrayList<>())
                .totalCount(0)
                .totalPages(0)
                .currentPage(page)
                .pageSize(size)
                .hasNext(false)
                .hasPrevious(false)
                .build();
    }

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

**검색 메서드**: `searchBoardsWithPaging()`

```java
public BoardPageResponseDTO searchBoardsWithPaging(String keyword, String searchType, int page, int size) {
    // 검색 타입별 페이징 처리
    // - ID: 단일 결과를 PageImpl로 변환
    // - TITLE/CONTENT: 페이징 쿼리 사용
    // - TITLE_CONTENT: FULLTEXT 페이징 쿼리 사용
}
```

### 4. Controller 수정

**파일**: `backend/main/java/com/linkup/Petory/domain/board/controller/BoardController.java`

**기존 메서드**:
```java
@GetMapping
public ResponseEntity<List<BoardDTO>> getAllBoards(
        @RequestParam(required = false) String category)
```

**변경된 메서드**:
```java
@GetMapping
public ResponseEntity<BoardPageResponseDTO> getAllBoards(
        @RequestParam(required = false) String category,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size)
```

**검색 API**:
```java
@GetMapping("/search")
public ResponseEntity<BoardPageResponseDTO> searchBoards(
        @RequestParam String keyword,
        @RequestParam(required = false, defaultValue = "TITLE_CONTENT") String searchType,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size)
```

## 프론트엔드 구현

### 1. API 호출 수정

**파일**: `frontend/src/api/boardApi.js`

```javascript
getAllBoards: (params = {}) => {
  const { page = 0, size = 20, ...otherParams } = params;
  const requestParams = { 
    page, 
    size, 
    ...otherParams, 
    _t: Date.now() 
  };
  return api.get('', {
    params: requestParams,
    headers: { 'Cache-Control': 'no-cache' }
  });
},

searchBoards: (keyword, searchType = 'TITLE_CONTENT', page = 0, size = 20) => {
  return api.get('/search', { 
    params: { keyword, searchType, page, size } 
  });
}
```

### 2. 상태 관리

**파일**: `frontend/src/components/Community/CommunityBoard.js`

**페이징 상태**:
```javascript
const [page, setPage] = useState(0);
const [pageSize, setPageSize] = useState(20);
const [totalCount, setTotalCount] = useState(0);
const [hasNext, setHasNext] = useState(false);
const [allLoadedPosts, setAllLoadedPosts] = useState([]); // 누적된 게시글 목록
```

**검색 페이징 상태**:
```javascript
const [searchPage, setSearchPage] = useState(0);
const [searchTotalCount, setSearchTotalCount] = useState(0);
const [searchHasNext, setSearchHasNext] = useState(false);
const [searchAllLoadedResults, setSearchAllLoadedResults] = useState([]);
```

### 3. 데이터 로딩 로직

**게시글 로드**:
```javascript
const fetchBoards = useCallback(async (pageNum = 0, reset = false) => {
  const requestParams = {
    category: activeCategory === 'ALL' ? null : activeCategory,
    page: pageNum,
    size: pageSize
  };
  
  const response = await boardApi.getAllBoards(requestParams);
  const pageData = response.data || {};
  const boards = pageData.boards || [];
  
  if (reset) {
    setAllLoadedPosts(boards);
  } else {
    setAllLoadedPosts(prev => [...prev, ...boards]);
  }
  
  setTotalCount(pageData.totalCount || 0);
  setHasNext(pageData.hasNext || false);
  setPage(pageNum);
}, [activeCategory, pageSize]);
```

**검색 로드**:
```javascript
const handleSearch = useCallback(async (pageNum = 0, reset = false) => {
  const response = await boardApi.searchBoards(searchKeyword.trim(), searchType, pageNum, pageSize);
  const pageData = response.data || {};
  const results = pageData.boards || [];
  
  if (reset) {
    setSearchAllLoadedResults(results);
  } else {
    setSearchAllLoadedResults(prev => [...prev, ...results]);
  }
  
  setSearchTotalCount(pageData.totalCount || 0);
  setSearchHasNext(pageData.hasNext || false);
  setSearchPage(pageNum);
}, [searchKeyword, searchType, pageSize]);
```

### 4. UI 컴포넌트

**페이지 크기 선택**:
```javascript
<PageSizeSelector>
  <PageSizeLabel>페이지당 게시글 수:</PageSizeLabel>
  <PageSizeButtons>
    <PageSizeButton active={pageSize === 20} onClick={() => handlePageSizeChange(20)}>
      20
    </PageSizeButton>
    <PageSizeButton active={pageSize === 50} onClick={() => handlePageSizeChange(50)}>
      50
    </PageSizeButton>
    <PageSizeButton active={pageSize === 100} onClick={() => handlePageSizeChange(100)}>
      100
    </PageSizeButton>
  </PageSizeButtons>
</PageSizeSelector>
```

**더 보기 버튼**:
```javascript
{(isSearchMode ? searchHasNext : hasNext) && (
  <LoadMoreContainer>
    <LoadMoreButton 
      onClick={isSearchMode ? handleSearchLoadMore : handleLoadMore} 
      disabled={isSearchMode ? searchLoading : loading}
    >
      {loading ? '로딩 중...' : 
        `더 보기 (${filteredPosts.length} / ${isSearchMode ? searchTotalCount : totalCount})`}
    </LoadMoreButton>
  </LoadMoreContainer>
)}
```

## API 엔드포인트

### 게시글 목록 조회

**요청**:
```
GET /api/boards?category=ALL&page=0&size=20
```

**응답**:
```json
{
  "boards": [...],
  "totalCount": 1500,
  "totalPages": 75,
  "currentPage": 0,
  "pageSize": 20,
  "hasNext": true,
  "hasPrevious": false
}
```

### 게시글 검색

**요청**:
```
GET /api/boards/search?keyword=검색어&searchType=TITLE_CONTENT&page=0&size=20
```

**검색 타입**:
- `ID`: 게시글 ID로 검색
- `TITLE`: 제목으로 검색
- `CONTENT`: 내용으로 검색
- `TITLE_CONTENT`: 제목+내용으로 검색 (기본값)

**응답**: 게시글 목록 조회와 동일한 형식

## 성능 개선 효과

### 이전 방식 (클라이언트 사이드 페이징)
- 초기 로딩: 전체 게시글 로드 (예: 1500개)
- 네트워크: 대용량 데이터 전송
- 렌더링: 모든 게시글 DOM 생성 후 필터링
- 메모리: 모든 데이터를 메모리에 유지

### 현재 방식 (서버 사이드 페이징)
- 초기 로딩: 20개만 로드
- 네트워크: 페이지 단위로 전송
- 렌더링: 필요한 만큼만 DOM 생성
- 메모리: 로드된 페이지만 유지

**예상 성능 개선**:
- 초기 로딩 시간: 약 70-80% 감소
- 네트워크 트래픽: 약 90% 감소 (20개 vs 1500개)
- 메모리 사용량: 약 90% 감소

## 주요 특징

1. **누적 로딩 방식**: "더 보기" 버튼 클릭 시 기존 데이터에 추가로 로드
2. **페이지 크기 선택**: 사용자가 20/50/100개 중 선택 가능
3. **검색 페이징**: 검색 결과도 페이징으로 처리
4. **카테고리 필터링**: 카테고리 변경 시 첫 페이지부터 다시 로드
5. **검색 모드 전환**: 검색 모드와 일반 모드 구분

## 주의사항

1. **페이징 파라미터**: `page`는 0부터 시작 (Spring Data JPA 표준)
2. **기본 페이지 크기**: 20개 (변경 가능)
3. **검색 타입**: 선택하지 않으면 기본값 "TITLE_CONTENT" 사용
4. **검색어 검증**: 검색어 없이 검색 시 "검색어를 입력하세요" 알림

## 적용 사례

### 1. 커뮤니티 게시판 (CommunityBoard)
- 초기 로딩: 20개만 로드
- 서버 사이드 페이징 + Map + Array 조합 적용
- 성능 개선: 초기 로딩 시간 대폭 감소

### 2. 내 활동 페이지 (ActivityPage)
- 초기 로딩: 20개만 로드
- 서버 사이드 페이징 + Map + Array 조합 적용
- 필터링도 서버에서 처리

### 3. 관리자 사용자 관리 (UserList)
- 초기 로딩: 20개만 로드
- 서버 사이드 페이징 + Map + Array 조합 적용
- 성능 개선: **1.23초 → 56ms (약 22배 개선)**
- 사용자 수정/삭제: O(n) → O(1)

### 4. 관리자 커뮤니티 관리 (CommunityManagementSection)
- 초기 로딩: 20개만 로드
- 서버 사이드 페이징 + Map + Array 조합 적용
- 성능 개선: **1.91초 (4800KB) → 예상 50-100ms (약 20-40배 개선)**
- 게시글 블라인드/삭제/복구: O(n) → O(1)
- 필터링도 서버에서 처리 (상태, 삭제여부, 카테고리, 검색어)

## 향후 개선 사항

1. 무한 스크롤 구현 고려
2. 페이지 번호 네비게이션 추가
3. 검색 결과 하이라이트 기능
4. 검색 히스토리 기능
5. 즐겨찾는 검색어 기능

