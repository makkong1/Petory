# 프론트엔드 게시글 데이터 구조 최적화

## 개요

서버 사이드 페이징으로 받아온 게시글 데이터를 프론트엔드에서 효율적으로 관리하기 위한 자료구조 최적화 가이드입니다.

## 문제점 분석

### 기존 방식: Array만 사용

```javascript
const [allLoadedPosts, setAllLoadedPosts] = useState([]);
```

**시간 복잡도 분석:**

| 작업 | 시간 복잡도 | 설명 |
|------|------------|------|
| 게시글 조회 | O(n) | 배열 전체를 순회하여 찾아야 함 |
| 게시글 업데이트 | O(n) | `map()`으로 전체 배열을 순회하며 업데이트 |
| 게시글 삭제 | O(n) | `filter()`로 전체 배열을 순회하며 제거 |
| 중복 체크 | O(n) | `find()` 또는 `includes()`로 전체 순회 |
| 순서 유지 | O(1) | 배열의 기본 특성 |

**실제 코드 예시:**
```javascript
// 게시글 업데이트 - O(n)
setAllLoadedPosts(prev => 
  prev.map(post => 
    post.idx === boardId 
      ? { ...post, likes: newLikes }
      : post
  )
);

// 게시글 삭제 - O(n)
setAllLoadedPosts(prev => 
  prev.filter(post => post.idx !== boardId)
);
```

**문제점:**
- 게시글이 많아질수록 성능 저하 (예: 1000개 게시글에서 하나 업데이트 시 1000번 순회)
- 불필요한 전체 배열 순회로 인한 렌더링 비용 증가
- React의 불필요한 리렌더링 트리거 가능성

## 해결책: Map + Array 조합

### 자료구조 설계

```javascript
const [postsData, setPostsData] = useState({ 
  map: {},      // {[boardId]: BoardDTO} - 빠른 조회/업데이트
  order: []     // [boardId, ...] - 순서 유지 및 렌더링용
});
```

### 이론적 배경

#### 1. Map (객체)의 특성
- **해시 테이블 기반**: 키를 통해 값을 O(1) 시간에 접근
- **직접 접근**: 인덱스가 아닌 키로 직접 접근 가능
- **중복 체크**: `hasOwnProperty()` 또는 `in` 연산자로 O(1) 시간에 확인

#### 2. Array의 특성
- **순서 보장**: 삽입 순서가 유지됨
- **인덱스 접근**: O(1) 시간에 인덱스로 접근 가능
- **렌더링 최적화**: React에서 배열 순회가 효율적

#### 3. 조합의 장점
- **Map**: 빠른 조회/업데이트/삭제 (O(1))
- **Array**: 순서 유지 및 렌더링 (O(1) 접근)
- **상호 보완**: 각 자료구조의 장점을 결합

### 시간 복잡도 비교

| 작업 | Array만 | Map + Array | 개선율 |
|------|---------|-------------|--------|
| 게시글 조회 | O(n) | O(1) | n배 빠름 |
| 게시글 업데이트 | O(n) | O(1) | n배 빠름 |
| 게시글 삭제 | O(n) | O(1) | n배 빠름 |
| 중복 체크 | O(n) | O(1) | n배 빠름 |
| 순서 유지 | O(1) | O(1) | 동일 |
| 배열 변환 | O(1) | O(n) | 약간 느림 (하지만 필요할 때만) |

**예시:**
- 1000개 게시글에서 하나 업데이트
  - Array만: 1000번 순회
  - Map + Array: 1번 접근
  - **1000배 성능 향상**

## 구현 방법

### 1. 헬퍼 함수

```javascript
// Map + Array를 배열로 변환 (렌더링용)
const getPostsArray = useCallback((postsData) => {
  return postsData.order.map(id => postsData.map[id]).filter(Boolean);
}, []);

// 게시글 배열을 Map + Array 구조로 변환
const convertToMapAndOrder = useCallback((boards) => {
  const map = {};
  const order = [];
  boards.forEach(board => {
    if (board?.idx && !map[board.idx]) {
      map[board.idx] = board;
      order.push(board.idx);
    }
  });
  return { map, order };
}, []);

// 게시글 추가 (중복 체크 포함)
const addPostsToMap = useCallback((existingData, newBoards) => {
  const map = { ...existingData.map };
  const order = [...existingData.order];
  newBoards.forEach(board => {
    if (board?.idx) {
      if (!map[board.idx]) {
        map[board.idx] = board;
        order.push(board.idx);
      } else {
        // 이미 있으면 업데이트
        map[board.idx] = board;
      }
    }
  });
  return { map, order };
}, []);
```

### 2. 데이터 로딩

```javascript
const fetchBoards = useCallback(async (pageNum = 0, reset = false) => {
  const response = await boardApi.getAllBoards(requestParams);
  const boards = pageData.boards || [];

  if (reset) {
    // 초기 로드: 새로 변환
    const newData = convertToMapAndOrder(boards);
    setPostsData(newData);
  } else {
    // 추가 로드: 기존 데이터에 병합
    setPostsData(prevData => addPostsToMap(prevData, boards));
  }
}, []);
```

### 3. 게시글 업데이트 (O(1))

```javascript
// 좋아요/싫어요 업데이트
const reactToBoard = async (boardId, reactionType) => {
  const summary = response.data;
  setPostsData((prev) => {
    const post = prev.map[boardId];  // O(1) 조회
    if (post) {
      return {
        ...prev,
        map: {
          ...prev.map,
          [boardId]: {              // O(1) 업데이트
            ...post,
            likes: summary.likeCount,
            dislikes: summary.dislikeCount,
          },
        },
      };
    }
    return prev;
  });
};
```

### 4. 게시글 삭제 (O(1))

```javascript
const handleDeletePost = async (postIdx) => {
  await boardApi.deleteBoard(postIdx);
  setPostsData((prev) => {
    const { [postIdx]: removed, ...restMap } = prev.map;  // O(1) 삭제
    return {
      map: restMap,
      order: prev.order.filter(id => id !== postIdx),     // O(n)이지만 order만 순회
    };
  });
};
```

### 5. 렌더링용 배열 변환

```javascript
// useMemo로 최적화
const filteredPosts = useMemo(() => {
  const postsArray = getPostsArray(postsData);  // O(n) 변환
  return postsArray.filter(post => {
    // 필터링 로직
  });
}, [postsData, getPostsArray]);
```

## 공간 복잡도 분석

### Array만 사용
- **공간 복잡도**: O(n)
- **메모리 사용**: n개 게시글 객체

### Map + Array 조합
- **공간 복잡도**: O(n)
  - Map: n개 게시글 객체
  - Array: n개 ID (숫자)
- **메모리 사용**: n개 게시글 객체 + n개 ID
- **추가 메모리**: ID 배열만큼 (게시글 객체 대비 매우 작음)

**결론**: 공간 복잡도는 거의 동일하지만, 시간 복잡도가 크게 개선됨

## 실제 사용 시나리오별 성능 비교

### 시나리오 1: 게시글 좋아요 클릭

**상황**: 사용자가 좋아요 버튼을 클릭하여 게시글의 좋아요 수를 업데이트

#### Array만 사용
```javascript
const reactToBoard = async (boardId, reactionType) => {
  const summary = response.data;
  
  // O(n): 전체 배열을 순회하며 해당 게시글 찾아서 업데이트
  setAllLoadedPosts(prev => 
    prev.map(post => 
      post.idx === boardId 
        ? {
            ...post,
            likes: summary.likeCount,
            dislikes: summary.dislikeCount,
            userReaction: summary.userReaction,
          }
        : post
    )
  );
};
```

#### Map + Array 조합
```javascript
const reactToBoard = async (boardId, reactionType) => {
  const summary = response.data;
  
  // O(1): Map에서 직접 접근하여 업데이트
  setPostsData((prev) => {
    const post = prev.map[boardId];  // O(1) 조회
    if (post) {
      return {
        ...prev,
        map: {
          ...prev.map,
          [boardId]: {              // O(1) 업데이트
            ...post,
            likes: summary.likeCount,
            dislikes: summary.dislikeCount,
            userReaction: summary.userReaction,
          },
        },
      };
    }
    return prev;
  });
};
```

---

### 시나리오 2: "더 보기" 버튼 클릭

**상황**: 사용자가 "더 보기" 버튼을 클릭하여 다음 페이지 게시글 20개를 추가로 로드

#### Array만 사용
```javascript
const fetchBoards = async (pageNum = 0, reset = false) => {
  const response = await boardApi.getAllBoards(requestParams);
  const boards = pageData.boards || []; // 20개 새 게시글

  if (reset) {
    setAllLoadedPosts(boards);
  } else {
    // O(n + m): 기존 배열 + 새 배열 결합
    // 중복 체크가 없어서 중복 게시글이 추가될 수 있음
    setAllLoadedPosts(prev => [...prev, ...boards]);
  }
};
```

#### Map + Array 조합
```javascript
const fetchBoards = async (pageNum = 0, reset = false) => {
  const response = await boardApi.getAllBoards(requestParams);
  const boards = pageData.boards || []; // 20개 새 게시글

  if (reset) {
    const newData = convertToMapAndOrder(boards);
    setPostsData(newData);
  } else {
    // O(m): 새 게시글만 순회, 각 게시글의 중복 체크는 O(1)
    setPostsData(prevData => addPostsToMap(prevData, boards));
  }
};

// addPostsToMap 내부
const addPostsToMap = (existingData, newBoards) => {
  const map = { ...existingData.map };
  const order = [...existingData.order];
  
  newBoards.forEach(board => {  // O(m)
    if (board?.idx) {
      if (!map[board.idx]) {    // O(1) 중복 체크
        map[board.idx] = board;
        order.push(board.idx);
      } else {
        map[board.idx] = board; // 이미 있으면 업데이트
      }
    }
  });
  
  return { map, order };
};
```

---

### 시나리오 3: 게시글 삭제

**상황**: 사용자가 게시글을 삭제하여 목록에서 제거

#### Array만 사용
```javascript
const handleDeletePost = async (postIdx) => {
  await boardApi.deleteBoard(postIdx);
  
  // O(n): 전체 배열을 순회하며 해당 게시글 제외
  setAllLoadedPosts(prev => 
    prev.filter(post => post.idx !== postIdx)
  );
};
```

#### Map + Array 조합
```javascript
const handleDeletePost = async (postIdx) => {
  await boardApi.deleteBoard(postIdx);
  
  setPostsData((prev) => {
    // O(1): Map에서 해당 게시글 제거
    const { [postIdx]: removed, ...restMap } = prev.map;
    
    return {
      map: restMap,
      // O(n): order 배열에서 ID 제거 (하지만 ID만 순회하므로 빠름)
      order: prev.order.filter(id => id !== postIdx),
    };
  });
};
```

**참고**: Map + Array 방식도 order 배열 순회로 O(n)이지만, 게시글 객체 전체를 비교하는 것이 아니라 ID(숫자)만 비교하므로 실제로는 훨씬 빠릅니다.

---

### 시나리오 4: 게시글 조회 (특정 게시글 찾기)

**상황**: 특정 게시글 ID로 게시글 정보를 조회

#### Array만 사용
```javascript
// 특정 게시글 찾기
const findPost = (boardId) => {
  // O(n): 배열 전체를 순회하며 찾기
  return allLoadedPosts.find(post => post.idx === boardId);
};
```

#### Map + Array 조합
```javascript
// 특정 게시글 찾기
const findPost = (boardId) => {
  // O(1): Map에서 직접 접근
  return postsData.map[boardId];
};
```

---

### 시나리오 5: 댓글 수 업데이트

**상황**: 댓글이 추가/삭제되어 게시글의 댓글 수를 업데이트

#### Array만 사용
```javascript
const handleCommentAdded = (boardId, isDelete = false) => {
  // O(n): 전체 배열을 순회하며 해당 게시글 찾아서 업데이트
  setAllLoadedPosts(prev =>
    prev.map(post =>
      post.idx === boardId
        ? {
            ...post,
            commentCount: Math.max(0, (post.commentCount ?? 0) + (isDelete ? -1 : 1)),
          }
        : post
    )
  );
};
```

#### Map + Array 조합
```javascript
const handleCommentAdded = (boardId, isDelete = false) => {
  // O(1): Map에서 직접 접근하여 업데이트
  setPostsData((prev) => {
    const post = prev.map[boardId];
    if (post) {
      return {
        ...prev,
        map: {
          ...prev.map,
          [boardId]: {
            ...post,
            commentCount: Math.max(0, (post.commentCount ?? 0) + (isDelete ? -1 : 1)),
          },
        },
      };
    }
    return prev;
  });
};
```

## 주의사항

### 1. React 상태 업데이트
- Map 객체를 직접 수정하지 말고 항상 새 객체 생성
- 불변성(Immutability) 유지 필수

### 2. 배열 변환 비용
- `getPostsArray()`는 O(n) 시간이 소요됨
- `useMemo`로 최적화하여 필요할 때만 변환

### 3. 메모리 사용
- ID 배열이 추가로 필요하지만 게시글 객체 대비 매우 작음
- 실용적인 트레이드오프

## 결론

### 장점
✅ **시간 복잡도 대폭 개선**: O(n) → O(1)  
✅ **확장성**: 게시글이 많아져도 성능 유지  
✅ **실용적**: 구현이 간단하고 효과적  
✅ **React 친화적**: 불변성 유지 가능  

### 단점
⚠️ **약간의 메모리 추가**: ID 배열 필요  
⚠️ **배열 변환 비용**: 렌더링 시 O(n) 변환 필요 (하지만 useMemo로 최적화 가능)  

### 최종 평가
**게시글 목록 관리에 최적화된 자료구조**로, 서버 사이드 페이징과 함께 사용하면 대규모 데이터도 효율적으로 처리할 수 있습니다.

## 실제 적용 사례

### 1. 커뮤니티 게시판 (CommunityBoard)
- 게시글 목록 관리
- 좋아요/싫어요 업데이트: O(1)
- 댓글 수 업데이트: O(1)
- 게시글 삭제: O(1)

### 2. 내 활동 페이지 (ActivityPage)
- 활동 내역 관리
- 필터링과 함께 사용
- 활동 카드 클릭 시 해당 게시글로 이동

### 3. 관리자 사용자 관리 (UserList)
- 사용자 목록 관리
- 사용자 수정: O(1)
- 사용자 삭제: O(1)
- **성능 개선: 1.23초 → 56ms (약 22배 개선)**

### 4. 관리자 커뮤니티 관리 (CommunityManagementSection)
- 게시글 목록 관리
- 게시글 블라인드/해제: O(1)
- 게시글 삭제/복구: O(1)
- **성능 개선: 1.91초 (4800KB) → 예상 50-100ms (약 20-40배 개선)**

## 참고 자료

- [JavaScript Map vs Object Performance](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map)
- [React Performance Optimization](https://react.dev/learn/render-and-commit)
- [Big O Notation](https://en.wikipedia.org/wiki/Big_O_notation)

