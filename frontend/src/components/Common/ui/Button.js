import React from 'react';
import styled, { css, keyframes } from 'styled-components';

// ── 스피너 애니메이션 ──────────────────────────────────────────
const spin = keyframes`
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
`;

const SpinnerIcon = styled.span`
  display: inline-block;
  width: 1em;
  height: 1em;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: ${spin} 0.7s linear infinite;
  flex-shrink: 0;
`;

// ── Size 스타일 ───────────────────────────────────────────────
const sizeStyles = {
  sm: css`
    padding: 10px 16px;
    min-height: 36px;
    font-size: ${({ theme }) => theme.typography.body2.fontSize};
    gap: 4px;
  `,
  md: css`
    padding: 12px 20px;
    min-height: 44px;
    font-size: ${({ theme }) => theme.typography.body1.fontSize};
    gap: 6px;
  `,
  lg: css`
    padding: 14px 24px;
    min-height: 48px;
    font-size: ${({ theme }) => theme.typography.body1.fontSize};
    gap: 8px;
  `,
};

// ── Variant 스타일 ────────────────────────────────────────────
const variantStyles = css`
  ${({ variant = 'primary', theme }) => {
    switch (variant) {
      case 'secondary':
        return css`
          background: ${theme.colors.surface};
          color: ${theme.colors.text};
          border: 1.5px solid ${theme.colors.border};

          &:hover:not(:disabled):not([data-loading='true']) {
            border-color: ${theme.colors.primary};
            color: ${theme.colors.primary};
            background: ${theme.colors.primarySoft};
          }

          &:active:not(:disabled):not([data-loading='true']) {
            background: ${theme.colors.primarySoft};
            transform: translateY(0);
            box-shadow: ${theme.shadows.sm};
          }
        `;

      case 'ghost':
        return css`
          background: transparent;
          color: ${theme.colors.textSecondary};
          border: none;

          &:hover:not(:disabled):not([data-loading='true']) {
            background: ${theme.colors.surfaceHover};
            color: ${theme.colors.text};
          }

          &:active:not(:disabled):not([data-loading='true']) {
            background: ${theme.colors.surfaceHover};
            transform: translateY(0);
          }
        `;

      case 'danger':
        return css`
          background: ${theme.colors.error};
          color: ${theme.colors.textInverse};
          border: none;

          &:hover:not(:disabled):not([data-loading='true']) {
            background: ${theme.colors.errorDark};
            transform: translateY(-1px);
            box-shadow: ${theme.shadows.md};
          }

          &:active:not(:disabled):not([data-loading='true']) {
            background: ${theme.colors.errorDark};
            transform: translateY(0);
            box-shadow: ${theme.shadows.sm};
          }
        `;

      case 'success':
        return css`
          background: ${theme.colors.success};
          color: ${theme.colors.textInverse};
          border: none;

          &:hover:not(:disabled):not([data-loading='true']) {
            background: ${theme.colors.successDark};
            transform: translateY(-1px);
            box-shadow: ${theme.shadows.md};
          }

          &:active:not(:disabled):not([data-loading='true']) {
            background: ${theme.colors.successDark};
            transform: translateY(0);
            box-shadow: ${theme.shadows.sm};
          }
        `;

      default: // primary
        return css`
          background: ${theme.colors.primary};
          color: ${theme.colors.textInverse};
          border: none;

          &:hover:not(:disabled):not([data-loading='true']) {
            background: ${theme.colors.primaryDark};
            transform: translateY(-1px);
            box-shadow: ${theme.shadows.md};
          }

          &:active:not(:disabled):not([data-loading='true']) {
            background: ${theme.colors.primaryDark};
            transform: translateY(0);
            box-shadow: ${theme.shadows.sm};
          }
        `;
    }
  }}
`;

// ── StyledButton ──────────────────────────────────────────────
const StyledButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: ${({ theme }) => theme.typography.fontWeight.semibold};
  border-radius: ${({ theme }) => theme.borderRadius.md};
  cursor: pointer;
  transition: background ${({ theme }) => theme.duration.fast} ${({ theme }) => theme.easing.easeOut},
              transform  ${({ theme }) => theme.duration.fast} ${({ theme }) => theme.easing.easeOut},
              box-shadow ${({ theme }) => theme.duration.fast} ${({ theme }) => theme.easing.easeOut},
              color      ${({ theme }) => theme.duration.fast} ${({ theme }) => theme.easing.easeOut},
              border-color ${({ theme }) => theme.duration.fast} ${({ theme }) => theme.easing.easeOut};
  white-space: nowrap;
  user-select: none;
  line-height: ${({ theme }) => theme.typography.lineHeight.tight};

  /* Size */
  ${({ size = 'md' }) => sizeStyles[size]}

  /* Variant */
  ${variantStyles}

  /* Disabled */
  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
  }

  /* Loading */
  &[data-loading='true'] {
    cursor: wait;
    opacity: 0.8;
    transform: none;
  }

  /* Full width */
  ${({ fullWidth }) => fullWidth && css`width: 100%;`}
`;

// ── Button 컴포넌트 (loading prop 지원) ──────────────────────
const Button = React.forwardRef(({
  children,
  loading = false,
  disabled = false,
  variant = 'primary',
  size = 'md',
  fullWidth = false,
  type = 'button',
  ...rest
}, ref) => {
  return (
    <StyledButton
      ref={ref}
      type={type}
      variant={variant}
      size={size}
      fullWidth={fullWidth}
      disabled={disabled || loading}
      data-loading={loading ? 'true' : undefined}
      {...rest}
    >
      {loading && <SpinnerIcon aria-hidden="true" />}
      {children}
    </StyledButton>
  );
});

Button.displayName = 'Button';

export default Button;
