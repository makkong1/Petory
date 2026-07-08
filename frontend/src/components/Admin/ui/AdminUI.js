import styled from 'styled-components';

/**
 * 관리자 공용 UI 프리미티브 — 가독성/스캔성 중점.
 * 조밀(compact) 밀도 + zebra 줄무늬 + sticky 헤더 기준.
 * 각 섹션이 표/필터/상태를 제각각 정의하던 것을 이 컴포넌트들로 통일한다.
 */

/* ── 섹션 헤더 ── */
export const SectionHeader = styled.div`
  margin-bottom: ${p => p.theme.spacing.lg};
`;

export const SectionTitle = styled.h1`
  font-size: ${p => p.theme.typography.h2.fontSize};
  font-weight: ${p => p.theme.typography.h2.fontWeight};
  margin: 0 0 ${p => p.theme.spacing.xs};
  color: ${p => p.theme.colors.text};
`;

export const SectionSubtitle = styled.p`
  margin: 0;
  color: ${p => p.theme.colors.textSecondary};
  font-size: ${p => p.theme.typography.body2.fontSize};
`;

/* ── 툴바 (탭 + 검색 + 필터 한 줄) ── */
export const Toolbar = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
`;

export const Spacer = styled.div`
  flex: 1;
`;

/* ── 세그먼트 탭 ── */
export const TabGroup = styled.div`
  display: inline-flex;
  background: ${p => p.theme.colors.surfaceSoft};
  border-radius: 10px;
  padding: 3px;
  gap: 2px;
  flex-wrap: wrap;
`;

export const Tab = styled.button`
  border: none;
  font-family: inherit;
  font-size: 12.5px;
  font-weight: 700;
  padding: 6px 13px;
  border-radius: 8px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: background 0.15s ease, color 0.15s ease;
  color: ${p => (p.$active ? p.theme.colors.text : p.theme.colors.textSecondary)};
  background: ${p => (p.$active ? p.theme.colors.surfaceElevated : 'transparent')};
  box-shadow: ${p => (p.$active ? p.theme.shadows.sm : 'none')};

  &:hover {
    color: ${p => p.theme.colors.text};
  }
`;

export const TabCount = styled.span`
  font-size: 11px;
  font-weight: 800;
  color: ${p => (p.$active ? p.theme.colors.primary : p.theme.colors.textMuted)};
`;

/* ── 검색 / 셀렉트 ── */
export const Search = styled.input`
  background: ${p => p.theme.colors.surfaceElevated};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 9px;
  padding: 8px 12px;
  font-size: 12.5px;
  font-family: inherit;
  color: ${p => p.theme.colors.text};
  min-width: 200px;
  outline: none;

  &::placeholder {
    color: ${p => p.theme.colors.textMuted};
  }
  &:focus {
    border-color: ${p => p.theme.colors.primary};
  }
`;

export const Select = styled.select`
  background: ${p => p.theme.colors.surfaceElevated};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 9px;
  padding: 8px 12px;
  font-size: 12.5px;
  font-weight: 600;
  font-family: inherit;
  color: ${p => p.theme.colors.text};
  cursor: pointer;
`;

/* ── 표 ── */
export const TableWrap = styled.div`
  overflow: auto;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 12px;
  background: ${p => p.theme.colors.surfaceElevated};
`;

export const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;

  tbody tr:nth-child(even) {
    background: ${p => p.theme.colors.surfaceSoft};
  }
  tbody tr:hover {
    background: ${p => p.theme.colors.surfaceHover};
  }
`;

export const Th = styled.th`
  position: sticky;
  top: 0;
  z-index: 1;
  background: ${p => p.theme.colors.surface};
  text-align: ${p => p.$align || 'left'};
  padding: 9px 12px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.textSecondary};
  white-space: nowrap;
  border-bottom: 1px solid ${p => p.theme.colors.border};
`;

export const Td = styled.td`
  text-align: ${p => p.$align || 'left'};
  padding: 7px 12px;
  border-bottom: 1px solid ${p => p.theme.colors.borderLight};
  vertical-align: middle;
  white-space: nowrap;
  color: ${p => (p.$muted ? p.theme.colors.textSecondary : p.theme.colors.text)};
  font-weight: ${p => (p.$strong ? 700 : 400)};
  font-variant-numeric: ${p => (p.$mono ? 'tabular-nums' : 'normal')};
  font-family: ${p => (p.$mono ? 'ui-monospace, SFMono-Regular, Menlo, monospace' : 'inherit')};

  ${p => p.$ellipsis && `
    max-width: ${typeof p.$ellipsis === 'number' ? p.$ellipsis + 'px' : '360px'};
    overflow: hidden;
    text-overflow: ellipsis;
  `}
`;

export const TableMessage = styled.div`
  padding: ${p => p.theme.spacing.xl};
  text-align: center;
  color: ${p => p.theme.colors.textSecondary};
  font-size: ${p => p.theme.typography.body2.fontSize};
`;

/* ── 상태 pill (tone → theme.colors.status 기반) ── */
const toneColor = (tone, theme) => {
  const c = theme.colors;
  const s = c.status || {};
  switch (tone) {
    case 'pending': return s.open || '#3B82F6';
    case 'progress': return c.primary;
    case 'success': return s.found || '#10B981';
    case 'warning': return s.closed || '#F59E0B';
    case 'danger': return s.missing || '#EF4444';
    case 'info': return s.resolved || '#6366F1';
    case 'neutral':
    default: return c.textMuted || '#9CA3AF';
  }
};

export const StatusPill = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 800;
  padding: 3px 9px;
  border-radius: 999px;
  white-space: nowrap;
  color: ${p => toneColor(p.$tone, p.theme)};
  background: ${p => toneColor(p.$tone, p.theme) + '22'};

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: ${p => toneColor(p.$tone, p.theme)};
  }
`;

/* ── 행 액션 버튼 ── */
export const RowBtn = styled.button`
  font-size: 11.5px;
  font-weight: 700;
  padding: 5px 11px;
  border-radius: 7px;
  border: 1px solid ${p => p.theme.colors.border};
  background: ${p => p.theme.colors.surfaceElevated};
  color: ${p => p.theme.colors.textSecondary};
  cursor: pointer;
  font-family: inherit;
  transition: border-color 0.15s ease, color 0.15s ease;

  &:hover {
    border-color: ${p => p.theme.colors.primary};
    color: ${p => p.theme.colors.primary};
  }
`;

export const RowBtnPrimary = styled(RowBtn)`
  background: ${p => p.theme.colors.primary};
  border-color: ${p => p.theme.colors.primary};
  color: #fff;

  &:hover {
    color: #fff;
    opacity: 0.92;
  }
`;

export const RowBtnDanger = styled(RowBtn)`
  border-color: ${p => p.theme.colors.error};
  color: ${p => p.theme.colors.error};

  &:hover {
    border-color: ${p => p.theme.colors.error};
    color: #fff;
    background: ${p => p.theme.colors.error};
  }
`;

export const RowActions = styled.div`
  display: inline-flex;
  gap: 6px;
`;

/* 필터 라벨 (툴바 내 작은 라벨) */
export const FieldLabel = styled.span`
  font-size: 11.5px;
  font-weight: 700;
  color: ${p => p.theme.colors.textSecondary};
  margin-right: 6px;
`;

/* ── 통계 카드 (대시보드/파일 등) ── */
export const StatGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
`;

export const StatCard = styled.div`
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 12px;
  padding: 14px 16px;
  background: ${p => p.theme.colors.surfaceElevated};
`;

export const StatLabel = styled.div`
  font-size: 11.5px;
  font-weight: 700;
  color: ${p => p.theme.colors.textSecondary};
  margin-bottom: 6px;
`;

export const StatValue = styled.div`
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: ${p => (p.$accent ? p.theme.colors.primary : p.theme.colors.text)};
  font-variant-numeric: tabular-nums;
`;
