import styled from 'styled-components';

export const InfoPanel = styled.div`
  position: absolute;
  bottom: 0;
  right: 0;
  width: ${props => props.$width || '300px'};
  max-height: ${props => props.$maxHeight || '65vh'};
  background: ${props => props.theme.colors.surface};
  border-left: 1px solid ${props => props.theme.colors.border};
  border-top: 1px solid ${props => props.theme.colors.border};
  border-radius: 12px 0 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 500;
  box-shadow: -4px -2px 16px rgba(0,0,0,0.12);

  @media (max-width: 600px) {
    width: 100%;
    border-radius: 12px 12px 0 0;
    max-height: 60vh;
  }
`;

export const PanelHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px 6px;
  flex-shrink: 0;
`;

export const CloseButton = styled.button`
  background: none;
  border: none;
  color: ${props => props.theme.colors.textSecondary};
  cursor: pointer;
  font-size: 15px;
  padding: 2px 6px;
  border-radius: 4px;
  &:hover { background: ${props => props.theme.colors.surfaceHover}; }
`;

export const PanelTitle = styled.h3`
  font-size: 15px;
  font-weight: 700;
  color: ${props => props.theme.colors.text};
  margin: 0;
  padding: 0 14px 8px;
`;

export const Divider = styled.hr`
  border: none;
  border-top: 1px solid ${props => props.theme.colors.border};
  margin: 0;
  flex-shrink: 0;
`;

export const InfoRow = styled.div`
  display: flex;
  gap: 8px;
  font-size: 13px;
  align-items: flex-start;
`;

export const InfoLabel = styled.span`
  color: ${props => props.theme.colors.textSecondary};
  min-width: ${props => props.$minWidth || '48px'};
  flex-shrink: 0;
  font-size: 12px;
`;

export const InfoValue = styled.span`
  color: ${props => props.theme.colors.text};
  word-break: break-word;

  a {
    color: ${props => props.theme.colors.primary};
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }
`;

export const InfoGrid = styled.div`
  padding: ${props => props.$padding || '0 14px'};
  display: flex;
  flex-direction: column;
  gap: 5px;
  overflow-y: auto;
`;

export const ActionRow = styled.div`
  padding: 10px 14px;
  flex-shrink: 0;
`;
