// Petory 디자인 시스템 v2 — 따뜻한 테라코타 & 청록 슬레이트 브랜드
export const lightTheme = {
  colors: {
    // ── Primary: 테라코타 피치 ────────────────────────────────
    primary: '#E8714A',       // 더 차분한 테라코타 (기존 #FF7E36)
    primaryDark: '#C9573A',   // hover/active (기존 #E86B2A)
    primaryLight: '#F0926E',  // 배경 강조용 (기존 #FF9558)
    primarySoft: '#FDF0EB',   // 라이트 배경 [신규]

    // ── Secondary: 청록 슬레이트 ─────────────────────────────
    secondary: '#3D8B7A',     // 청록 (기존 #4A90E2)
    secondaryDark: '#2E6B5E', // hover [신규]
    secondaryLight: '#5BA898',// 밝은 버전 [신규]
    secondarySoft: '#EBF5F3', // 라이트 배경 [신규]

    // ── Neutral: 따뜻한 그레이 배경 ──────────────────────────
    background: '#FAFAF8',    // 따뜻한 오프화이트 (기존 #FFFFFF)
    surface: '#F5F4F1',       // 카드 배경 (기존 #F8F9FA)
    surfaceSoft: '#EFEDE9',   // 섹션 배경 (기존 #F0F2F5)
    surfaceElevated: '#FFFFFF',  // 모달/팝업 — 순백 유지 (기존 #F8F9FA)
    surfaceHover: '#EAE8E4',  // hover 배경 (기존 #F1F3F4)

    // ── 텍스트 (WCAG AA 보장) ─────────────────────────────────
    text: '#1C1917',          // 18.1:1 (기존 #212121)
    textSecondary: '#6B7280', // 5.1:1 (기존 #757575)
    textLight: '#6B7280',     // 5.1:1 — 기존 #9E9E9E(2.85:1) → 개선
    textInverse: '#FFFFFF',   // 흰 텍스트 [신규]
    textMuted: '#9CA3AF',     // 비활성, placeholder [신규]

    // ── Border ───────────────────────────────────────────────
    border: '#E2DDD8',        // (기존 #E0E0E0)
    borderLight: '#F0EDE8',   // (기존 #F5F5F5)
    borderDark: '#B5AFA8',    // (기존 #BDBDBD)
    borderFocus: '#E8714A',   // focus ring [신규]

    // ── 시맨틱 ───────────────────────────────────────────────
    success: '#22C55E',       // (기존 #4CAF50)
    successDark: '#16A34A',   // (기존 #388E3C)
    successSoft: '#F0FDF4',   // 배경용 [신규]
    warning: '#F59E0B',       // 앰버 (기존 #FF9800)
    warningDark: '#D97706',   // (기존 #F57C00)
    warningSoft: '#FFFBEB',   // 배경용 [신규]
    error: '#EF4444',         // (기존 #F44336)
    errorDark: '#DC2626',     // (기존 #D32F2F)
    errorSoft: '#FEF2F2',     // 배경용 [신규]
    info: '#3B82F6',          // (기존 #2196F3)
    infoDark: '#2563EB',      // (기존 #1565C0)
    infoSoft: '#EFF6FF',      // 배경용 [신규]

    // ── 기타 ─────────────────────────────────────────────────
    shadow: 'rgba(28,25,23,0.08)',      // (기존 rgba(0,0,0,0.1))
    shadowHover: 'rgba(28,25,23,0.14)', // (기존 rgba(0,0,0,0.15))
    gradient: 'linear-gradient(135deg, #E8714A 0%, #F0926E 100%)',
    overlay: 'rgba(28,25,23,0.6)',      // (기존 rgba(0,0,0,0.5))

    // ── 도메인(탐색 탭) 색상 ─────────────────────────────────
    domain: {
      location: '#3B82F6',    // 파랑 (기존 #4A90D9)
      meetup: '#10B981',      // 에메랄드 (기존 #52C41A)
      care: '#F59E0B',        // 앰버 (기존 #FAAD14)
      community: '#8B5CF6',   // 보라 (커뮤니티) [신규]
      missing: '#EF4444',     // 빨강 (실종동물) [신규]
    },

    // ── 커뮤니티 카테고리 색상 [신규 — CommunityBoard.js 하드코딩 이동] ──
    category: {
      all: '#6366F1',         // 전체 (인디고)
      daily: '#EC4899',       // 일상 (핑크)
      pride: '#F472B6',       // 자랑 (로즈)
      question: '#3B82F6',    // 질문 (블루)
      info: '#10B981',        // 정보 (에메랄드)
      review: '#8B5CF6',      // 후기 (퍼플)
      meetup: '#F59E0B',      // 모임 (앰버)
      notice: '#EF4444',      // 공지 (레드)
      missing: '#EF4444',     // 실종 (레드)
      adoption: '#8B5CF6',    // 입양 (퍼플)
      free: '#6366F1',        // 나눔 (인디고)
    },

    // ── OAuth 브랜드 색상 [신규 — 브랜드 정책상 변경 불가] ───
    oauth: {
      google: '#4285F4',
      naver: '#03C75A',
      kakao: '#FEE500',
      kakaoText: '#3C1E1E',
    },

    // ── AI 추천 색상 (기존 유지) ──────────────────────────────
    ai: {
      accent: '#F5A623',
      bg: '#FFF8EC',
      text: '#c47d00',
    },

    // ── 상태 배지 색상 (기존 유지, 일부 업데이트) ────────────
    status: {
      missing: '#EF4444',
      found: '#10B981',
      resolved: '#6366F1',
      recruiting: '#22C55E',
      closed: '#F59E0B',
      completed: '#9CA3AF',
      cancelled: '#EF4444',
      open: '#3B82F6',
      inProgress: '#E8714A',
    },
  },

  // ── 차트 색상 팔레트 (새 브랜드 컬러 반영) ──────────────────
  chart: ['#E8714A', '#3D8B7A', '#22C55E', '#F59E0B', '#8B5CF6', '#06B6D4', '#EF4444', '#F97316'],

  // ── 그림자 토큰 ──────────────────────────────────────────────
  shadows: {
    none: 'none',
    sm: '0 1px 3px rgba(28,25,23,0.08), 0 1px 2px rgba(28,25,23,0.04)',
    md: '0 4px 8px rgba(28,25,23,0.08), 0 2px 4px rgba(28,25,23,0.04)',
    lg: '0 10px 24px rgba(28,25,23,0.10), 0 4px 8px rgba(28,25,23,0.06)',
    xl: '0 20px 40px rgba(28,25,23,0.14), 0 8px 16px rgba(28,25,23,0.08)',
    focus: '0 0 0 3px rgba(232,113,74,0.3)',  // focus ring [신규]
  },

  // ── 간격 시스템 (8pt 그리드) ────────────────────────────────
  spacing: {
    xs: '4px',
    sm: '8px',
    md: '12px',
    lg: '16px',
    xl: '20px',
    xxl: '24px',  // 변경: 30px → 24px (8pt 그리드 맞춤)
    '3xl': '32px',  // [신규]
    '4xl': '40px',  // [신규]
    '5xl': '48px',  // [신규]
    '6xl': '64px',  // [신규]
  },

  // ── Border Radius ─────────────────────────────────────────────
  borderRadius: {
    xs: '2px',      // 작은 배지 [신규]
    sm: '4px',      // 태그, 소형 배지
    md: '8px',      // 버튼, 입력창 (기존 6px → 8px)
    lg: '12px',     // 카드 (기존 8px → 12px)
    xl: '16px',     // 모달, 드롭다운 (기존 12px → 16px)
    '2xl': '24px',  // 큰 카드, 패널 [신규]
    pill: '9999px', // 알약형 버튼, 배지 [신규]
    full: '50%',    // 아바타, 원형 버튼
  },

  // ── 타이포그래피 (완전 재설계) ───────────────────────────────
  typography: {
    fontFamily: {
      korean: "'Pretendard Variable', 'Pretendard', -apple-system, BlinkMacSystemFont, 'Noto Sans KR', sans-serif",
      english: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif",
      mono: "'JetBrains Mono', 'Fira Code', monospace",
    },
    fontWeight: {
      regular: 400,
      medium: 500,
      semibold: 600,
      bold: 700,
      extrabold: 800,
    },
    lineHeight: {
      tight: 1.2,    // 헤딩용
      normal: 1.5,   // 본문용
      relaxed: 1.75, // 읽기 편한 긴 텍스트
    },
    // 크기 스케일
    hero: { fontSize: '48px', fontWeight: '800', lineHeight: 1.2 },   // [신규]
    h1: { fontSize: '32px', fontWeight: '700', lineHeight: 1.2 },     // 기존 20px → 32px
    h2: { fontSize: '24px', fontWeight: '600', lineHeight: 1.2 },     // 기존 18px → 24px
    h3: { fontSize: '20px', fontWeight: '600', lineHeight: 1.5 },     // 기존 16px → 20px
    h4: { fontSize: '17px', fontWeight: '500', lineHeight: 1.5 },     // 기존 14px → 17px
    body1: { fontSize: '15px', fontWeight: '400', lineHeight: 1.5 },  // 기존 13px → 15px
    body2: { fontSize: '13px', fontWeight: '400', lineHeight: 1.5 },  // 기존 12px → 13px
    caption: { fontSize: '11px', fontWeight: '400', lineHeight: 1.2 },// 기존 10px → 11px
    tiny: { fontSize: '10px', fontWeight: '400', lineHeight: 1.2 },   // [신규]
  },

  // ── 애니메이션 Duration [신규] ────────────────────────────────
  duration: {
    instant: '0ms',
    fast: '100ms',
    normal: '200ms',
    slow: '300ms',
    slower: '500ms',
  },

  // ── Easing 함수 [신규] ────────────────────────────────────────
  easing: {
    linear: 'linear',
    easeIn: 'cubic-bezier(0.4, 0, 1, 1)',
    easeOut: 'cubic-bezier(0, 0, 0.2, 1)',
    easeInOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
    spring: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
  },

  // ── Container max-width [신규] ───────────────────────────────
  container: {
    sm: '640px',
    md: '768px',
    lg: '1024px',
    xl: '1280px',
    full: '100%',
  },
};

export const darkTheme = {
  colors: {
    // ── Primary (다크에서 밝게) ───────────────────────────────
    primary: '#F0926E',       // 다크에서 밝게 (기존 #FF7E36)
    primaryDark: '#E8714A',   // (기존 #E86B2A)
    primaryLight: '#F5AE92',  // (기존 #FF9558)
    primarySoft: '#3D2218',   // 다크 배경용

    // ── Secondary ────────────────────────────────────────────
    secondary: '#5BA898',
    secondaryDark: '#3D8B7A',
    secondaryLight: '#7DC0B4',
    secondarySoft: '#1A2E2B',

    // ── Neutral: 따뜻한 다크 ──────────────────────────────────
    background: '#171412',    // 따뜻한 다크 (기존 #1A1A1A)
    surface: '#242220',       // (기존 #2D2D2D)
    surfaceSoft: '#1E1C1A',   // (기존 #262626)
    surfaceElevated: '#2E2C2A', // (기존 #363636)
    surfaceHover: '#322F2C',  // (기존 #3A3A3A)

    // ── 텍스트 ───────────────────────────────────────────────
    text: '#F5F0EC',          // 따뜻한 오프화이트 (기존 #F5F5F5)
    textSecondary: '#C4BDB7', // (기존 #C5C5C5)
    textLight: '#9C958E',     // (기존 #A0A0A0)
    textInverse: '#1C1917',
    textMuted: '#7A7470',

    // ── Border ───────────────────────────────────────────────
    border: '#3D3A37',        // (기존 #505050)
    borderLight: '#302D2A',   // (기존 #404040)
    borderDark: '#524E4A',    // (기존 #666666)
    borderFocus: '#F0926E',

    // ── 시맨틱 ───────────────────────────────────────────────
    success: '#4ADE80',
    successDark: '#22C55E',
    successSoft: '#052E16',
    warning: '#FCD34D',
    warningDark: '#F59E0B',
    warningSoft: '#2D1B00',
    error: '#F87171',
    errorDark: '#EF4444',
    errorSoft: '#2D0A0A',
    info: '#60A5FA',
    infoDark: '#3B82F6',
    infoSoft: '#0C1A3D',

    // ── 기타 ─────────────────────────────────────────────────
    shadow: 'rgba(0,0,0,0.4)',
    shadowHover: 'rgba(0,0,0,0.5)',
    gradient: 'linear-gradient(135deg, #F0926E 0%, #F5AE92 100%)',
    overlay: 'rgba(0,0,0,0.7)',

    // ── 도메인 (다크모드) ─────────────────────────────────────
    domain: {
      location: '#60A5FA',
      meetup: '#34D399',
      care: '#FCD34D',
      community: '#A78BFA',
      missing: '#F87171',
    },

    // ── 카테고리 (다크 — 라이트와 동일 색상 유지) ─────────────
    category: {
      all: '#818CF8',
      daily: '#F472B6',
      pride: '#F9A8D4',
      question: '#60A5FA',
      info: '#34D399',
      review: '#A78BFA',
      meetup: '#FCD34D',
      notice: '#F87171',
      missing: '#F87171',
      adoption: '#A78BFA',
      free: '#818CF8',
    },

    // ── OAuth (브랜드 정책상 고정) ────────────────────────────
    oauth: {
      google: '#4285F4',
      naver: '#03C75A',
      kakao: '#FEE500',
      kakaoText: '#3C1E1E',
    },

    // ── AI 추천 (다크모드) ────────────────────────────────────
    ai: {
      accent: '#F5A623',
      bg: '#3D3220',
      text: '#F5C668',
    },

    // ── 상태 배지 (다크모드) ──────────────────────────────────
    status: {
      missing: '#F87171',
      found: '#34D399',
      resolved: '#818CF8',
      recruiting: '#4ADE80',
      closed: '#FCD34D',
      completed: '#9CA3AF',
      cancelled: '#F87171',
      open: '#60A5FA',
      inProgress: '#F0926E',
    },
  },

  // 차트 — 다크에서도 새 브랜드 컬러 유지
  chart: ['#E8714A', '#3D8B7A', '#22C55E', '#F59E0B', '#8B5CF6', '#06B6D4', '#EF4444', '#F97316'],

  // 나머지 토큰은 lightTheme와 공유
  shadows: lightTheme.shadows,
  spacing: lightTheme.spacing,
  borderRadius: lightTheme.borderRadius,
  typography: lightTheme.typography,
  duration: lightTheme.duration,
  easing: lightTheme.easing,
  container: lightTheme.container,
};
