import styled from 'styled-components';

// ===== Layout =====

export const AuthPageWrapper = styled.div`
  color-scheme: light;
  min-height: 100vh;
  display: flex;
  background: ${({ theme }) => theme.colors.background};

  @media (min-width: 1024px) {
    flex-direction: row;
  }

  @media (max-width: 1023px) {
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: max(40px, env(safe-area-inset-top, 40px)) 20px max(40px, env(safe-area-inset-bottom, 40px));
  }
`;

// ===== Brand Panel (Desktop only, ≥1024px) =====

export const BrandPanel = styled.aside`
  display: none;

  @media (min-width: 1024px) {
    display: flex;
    flex: 0 0 45%;
    flex-direction: column;
    justify-content: space-between;
    padding: 64px 56px;
    background: linear-gradient(135deg, #E8714A 0%, #C9573A 50%, #A8442C 100%);
    position: relative;
    overflow: hidden;
  }
`;

export const BrandWordmark = styled.div`
  font-size: 14px;
  letter-spacing: 0.2em;
  color: rgba(255, 255, 255, 0.85);
  font-weight: 600;
  position: relative;
  z-index: 1;
`;

export const BrandFloatingArea = styled.div`
  position: relative;
  flex: 1;
  min-height: 320px;
`;

export const FloatingGlassCard = styled.div`
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 20px;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  width: ${({ $size = 80 }) => `${$size}px`};
  height: ${({ $size = 80 }) => `${$size}px`};
  font-size: ${({ $size = 80 }) => `${$size * 0.5}px`};
  top: ${({ $top }) => $top ?? 'auto'};
  left: ${({ $left }) => $left ?? 'auto'};
  right: ${({ $right }) => $right ?? 'auto'};
  bottom: ${({ $bottom }) => $bottom ?? 'auto'};
  transform: rotate(${({ $rotate = 0 }) => `${$rotate}deg`});
`;

export const BrandSloganGroup = styled.div`
  position: relative;
  z-index: 1;
`;

export const BrandSlogan = styled.h2`
  font-size: 36px;
  font-weight: 700;
  line-height: 1.3;
  color: #ffffff;
  margin: 0;
  white-space: pre-line;
`;

// ===== Form Panel (Right) =====

export const FormPanel = styled.section`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;

  @media (min-width: 1024px) {
    flex: 0 0 55%;
    padding: 64px 56px;
  }
`;

export const FormInner = styled.div`
  width: 100%;
  max-width: 420px;
  display: flex;
  flex-direction: column;
  gap: 24px;
`;

export const FormHeader = styled.header`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

export const FormHeaderLogo = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 18px;
  font-weight: 700;
  color: ${({ theme }) => theme.colors.primary};

  @media (min-width: 1024px) {
    display: none;
  }
`;

export const FormTitle = styled.h1`
  font-size: ${({ theme }) => theme.typography.h1.fontSize};
  font-weight: ${({ theme }) => theme.typography.h1.fontWeight};
  line-height: ${({ theme }) => theme.typography.h1.lineHeight};
  color: ${({ theme }) => theme.colors.text};
  margin: 0;
`;

export const FormSubtitle = styled.p`
  font-size: 14px;
  color: ${({ theme }) => theme.colors.textSecondary};
  margin: 0;
`;

// ===== Form Controls =====

export const PillInput = styled.input`
  color-scheme: light;
  width: 100%;
  padding: 14px 20px;
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.pill};
  font-size: 15px;
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.text};
  outline: none;
  transition: border-color ${({ theme }) => theme.duration.fast} ease,
              box-shadow ${({ theme }) => theme.duration.fast} ease;
  box-sizing: border-box;

  &:focus {
    border-color: ${({ theme }) => theme.colors.borderFocus};
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }

  &::placeholder {
    color: ${({ theme }) => theme.colors.textMuted};
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceSoft};
    color: ${({ theme }) => theme.colors.textLight};
    cursor: not-allowed;
  }
`;

export const PillSelect = styled.select`
  color-scheme: light;
  width: 100%;
  padding: 14px 20px;
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.pill};
  font-size: 15px;
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.text};
  outline: none;
  cursor: pointer;
  transition: border-color ${({ theme }) => theme.duration.fast} ease;
  box-sizing: border-box;

  &:focus {
    border-color: ${({ theme }) => theme.colors.borderFocus};
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceSoft};
    color: ${({ theme }) => theme.colors.textLight};
    cursor: not-allowed;
  }
`;

export const GradientButton = styled.button`
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #E8714A 0%, #C9573A 100%);
  color: #ffffff;
  border: none;
  border-radius: ${({ theme }) => theme.borderRadius.pill};
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity ${({ theme }) => theme.duration.normal} ease,
              transform ${({ theme }) => theme.duration.fast} ease;

  &:hover:not(:disabled) {
    opacity: 0.92;
  }

  &:active:not(:disabled) {
    transform: scale(0.98);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

export const OutlineButton = styled.button`
  width: 100%;
  padding: 14px;
  background: transparent;
  color: ${({ theme }) => theme.colors.primary};
  border: 1.5px solid ${({ theme }) => theme.colors.primary};
  border-radius: ${({ theme }) => theme.borderRadius.pill};
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: background ${({ theme }) => theme.duration.fast} ease;

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.primarySoft};
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

export const SocialButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  width: 100%;
  padding: 12px;
  background: ${({ theme }) => theme.colors.surfaceElevated};
  color: ${({ theme }) => theme.colors.text};
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.pill};
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background ${({ theme }) => theme.duration.fast} ease;

  &:hover {
    background: ${({ theme }) => theme.colors.surfaceHover};
  }
`;

export const SocialIcon = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 13px;
  font-weight: 800;
  color: #ffffff;
  background: ${({ $provider, theme }) => theme.colors.oauth[$provider] || theme.colors.primary};
`;

export const Divider = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 4px 0;

  &::before,
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: ${({ theme }) => theme.colors.borderLight};
  }

  span {
    font-size: 13px;
    color: ${({ theme }) => theme.colors.textSecondary};
  }
`;

export const FormSwitchLink = styled.div`
  text-align: center;
  font-size: 14px;
  color: ${({ theme }) => theme.colors.textSecondary};

  button {
    background: none;
    border: none;
    color: ${({ theme }) => theme.colors.primary};
    font-weight: 600;
    cursor: pointer;
    padding: 0 4px;
    font-size: 14px;

    &:hover {
      text-decoration: underline;
    }
  }
`;
