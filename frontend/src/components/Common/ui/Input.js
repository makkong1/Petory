import React from 'react';
import styled, { css } from 'styled-components';

// ── 기본 Input 스타일 ─────────────────────────────────────────
const baseInputStyles = css`
  width: 100%;
  padding: 12px 14px;
  min-height: 44px;
  font-size: ${({ theme }) => theme.typography.body1.fontSize};
  line-height: ${({ theme }) => theme.typography.lineHeight.normal};
  border-radius: ${({ theme }) => theme.borderRadius.md};
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.text};
  transition:
    border-color ${({ theme }) => theme.duration.fast} ${({ theme }) => theme.easing.easeOut},
    box-shadow   ${({ theme }) => theme.duration.fast} ${({ theme }) => theme.easing.easeOut};
  outline: none;

  &::placeholder {
    color: ${({ theme }) => theme.colors.textMuted};
  }

  /* Focus */
  &:focus {
    border-color: ${({ theme }) => theme.colors.primary};
    box-shadow: ${({ theme }) => theme.shadows.focus};
  }

  /* Disabled */
  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceSoft};
    color: ${({ theme }) => theme.colors.textMuted};
    cursor: not-allowed;
    opacity: 0.7;
  }

  /* Error 상태 */
  ${({ $hasError, theme }) =>
    $hasError &&
    css`
      border-color: ${theme.colors.error};
      &:focus {
        border-color: ${theme.colors.error};
        box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.2);
      }
    `}

  /* Success 상태 */
  ${({ $hasSuccess, theme }) =>
    $hasSuccess &&
    css`
      border-color: ${theme.colors.success};
      &:focus {
        border-color: ${theme.colors.success};
        box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.2);
      }
    `}
`;

// ── Styled primitives ─────────────────────────────────────────
const StyledInput = styled.input`${baseInputStyles}`;

const StyledTextarea = styled.textarea`
  ${baseInputStyles}
  resize: vertical;
  min-height: 100px;
`;

const StyledSelect = styled.select`
  ${baseInputStyles}
  cursor: pointer;
`;

// ── 레이아웃/레이블/메시지 ────────────────────────────────────
const InputWrapper = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.xs};
`;

const InputLabel = styled.label`
  font-size: ${({ theme }) => theme.typography.body2.fontSize};
  font-weight: ${({ theme }) => theme.typography.fontWeight.medium};
  color: ${({ theme }) => theme.colors.textSecondary};
  margin-bottom: ${({ theme }) => theme.spacing.xs};
  line-height: ${({ theme }) => theme.typography.lineHeight.normal};
`;

const HelperText = styled.span`
  font-size: ${({ theme }) => theme.typography.caption.fontSize};
  color: ${({ $isError, theme }) => ($isError ? theme.colors.error : theme.colors.textMuted)};
  line-height: ${({ theme }) => theme.typography.lineHeight.normal};
  margin-top: ${({ theme }) => theme.spacing.xs};
`;

// ── Input 복합 컴포넌트 ───────────────────────────────────────
/**
 * Input 컴포넌트
 *
 * @prop {string}  label       - 레이블 텍스트
 * @prop {string}  error       - 에러 메시지 (표시 시 에러 스타일 적용)
 * @prop {boolean} success     - 성공 상태 (테두리 초록색)
 * @prop {string}  helperText  - 일반 안내 메시지 (error 없을 때 표시)
 * @prop {string}  id          - label htmlFor 연결 (미지정 시 자동 생성)
 */
const Input = React.forwardRef(({
  label,
  error,
  success = false,
  helperText,
  id,
  className,
  ...rest
}, ref) => {
  const inputId = id || (label ? `input-${label.replace(/\s+/g, '-').toLowerCase()}` : undefined);

  return (
    <InputWrapper className={className}>
      {label && (
        <InputLabel htmlFor={inputId}>
          {label}
        </InputLabel>
      )}
      <StyledInput
        ref={ref}
        id={inputId}
        $hasError={!!error}
        $hasSuccess={success && !error}
        aria-invalid={!!error}
        aria-describedby={
          error
            ? `${inputId}-error`
            : helperText
            ? `${inputId}-helper`
            : undefined
        }
        {...rest}
      />
      {error && (
        <HelperText id={`${inputId}-error`} $isError role="alert">
          {error}
        </HelperText>
      )}
      {!error && helperText && (
        <HelperText id={`${inputId}-helper`}>
          {helperText}
        </HelperText>
      )}
    </InputWrapper>
  );
});

Input.displayName = 'Input';

// ── Textarea 복합 컴포넌트 ────────────────────────────────────
const Textarea = React.forwardRef(({
  label,
  error,
  success = false,
  helperText,
  id,
  className,
  ...rest
}, ref) => {
  const textareaId = id || (label ? `textarea-${label.replace(/\s+/g, '-').toLowerCase()}` : undefined);

  return (
    <InputWrapper className={className}>
      {label && (
        <InputLabel htmlFor={textareaId}>
          {label}
        </InputLabel>
      )}
      <StyledTextarea
        ref={ref}
        id={textareaId}
        $hasError={!!error}
        $hasSuccess={success && !error}
        aria-invalid={!!error}
        aria-describedby={
          error
            ? `${textareaId}-error`
            : helperText
            ? `${textareaId}-helper`
            : undefined
        }
        {...rest}
      />
      {error && (
        <HelperText id={`${textareaId}-error`} $isError role="alert">
          {error}
        </HelperText>
      )}
      {!error && helperText && (
        <HelperText id={`${textareaId}-helper`}>
          {helperText}
        </HelperText>
      )}
    </InputWrapper>
  );
});

Textarea.displayName = 'Textarea';

// ── Select 복합 컴포넌트 ──────────────────────────────────────
const Select = React.forwardRef(({
  label,
  error,
  success = false,
  helperText,
  id,
  className,
  children,
  ...rest
}, ref) => {
  const selectId = id || (label ? `select-${label.replace(/\s+/g, '-').toLowerCase()}` : undefined);

  return (
    <InputWrapper className={className}>
      {label && (
        <InputLabel htmlFor={selectId}>
          {label}
        </InputLabel>
      )}
      <StyledSelect
        ref={ref}
        id={selectId}
        $hasError={!!error}
        $hasSuccess={success && !error}
        aria-invalid={!!error}
        {...rest}
      >
        {children}
      </StyledSelect>
      {error && (
        <HelperText id={`${selectId}-error`} $isError role="alert">
          {error}
        </HelperText>
      )}
      {!error && helperText && (
        <HelperText id={`${selectId}-helper`}>
          {helperText}
        </HelperText>
      )}
    </InputWrapper>
  );
});

Select.displayName = 'Select';

// ── Named exports (기존 import 패턴 호환) ─────────────────────
export { Input, Textarea, Select, InputWrapper, InputLabel, HelperText };

// ── 기존 InputError 별칭 (하위 호환) ─────────────────────────
export const InputError = HelperText;

export default Input;
