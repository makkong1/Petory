import styled, { css } from 'styled-components';

const sizeStyles = {
  sm: css`
    padding: 6px 12px;
    font-size: 12px;
  `,
  md: css`
    padding: 10px 20px;
    font-size: 14px;
  `,
  lg: css`
    padding: 13px 28px;
    font-size: 16px;
  `,
};

const variantStyles = css`
  ${({ variant, theme }) => {
    switch (variant) {
      case 'secondary':
        return css`
          background: ${theme.colors.surface};
          color: ${theme.colors.text};
          border: 1px solid ${theme.colors.border};
          &:hover:not(:disabled) {
            background: ${theme.colors.surfaceHover};
            border-color: ${theme.colors.borderDark};
          }
        `;
      case 'danger':
        return css`
          background: ${theme.colors.error};
          color: #fff;
          border: none;
          &:hover:not(:disabled) {
            background: ${theme.colors.errorDark};
          }
        `;
      case 'ghost':
        return css`
          background: transparent;
          color: ${theme.colors.primary};
          border: 1px solid ${theme.colors.primary};
          &:hover:not(:disabled) {
            background: ${theme.colors.primary}15;
          }
        `;
      default: // primary
        return css`
          background: ${theme.colors.primary};
          color: #fff;
          border: none;
          &:hover:not(:disabled) {
            background: ${theme.colors.primaryDark};
            box-shadow: 0 4px 12px ${theme.colors.primary}40;
          }
        `;
    }
  }}
`;

const Button = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-weight: 600;
  border-radius: ${({ theme }) => theme.borderRadius.lg};
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;

  ${({ size = 'md' }) => sizeStyles[size]}
  ${variantStyles}

  &:hover:not(:disabled) {
    transform: translateY(-1px);
  }

  &:active:not(:disabled) {
    transform: translateY(0);
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceHover};
    color: ${({ theme }) => theme.colors.textLight};
    border-color: ${({ theme }) => theme.colors.border};
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
  }

  ${({ fullWidth }) => fullWidth && css`width: 100%;`}
`;

export default Button;
