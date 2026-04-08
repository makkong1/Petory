import styled, { css } from 'styled-components';

const baseInputStyles = css`
  width: 100%;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.5;
  border-radius: ${({ theme }) => theme.borderRadius.lg};
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.text};
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  outline: none;

  &::placeholder {
    color: ${({ theme }) => theme.colors.textLight};
  }

  &:focus {
    border-color: ${({ theme }) => theme.colors.primary};
    box-shadow: 0 0 0 3px ${({ theme }) => theme.colors.primary}25;
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceSoft};
    color: ${({ theme }) => theme.colors.textLight};
    cursor: not-allowed;
  }

  ${({ hasError, theme }) =>
    hasError &&
    css`
      border-color: ${theme.colors.error};
      &:focus {
        border-color: ${theme.colors.error};
        box-shadow: 0 0 0 3px ${theme.colors.error}25;
      }
    `}
`;

export const Input = styled.input`
  ${baseInputStyles}
`;

export const Textarea = styled.textarea`
  ${baseInputStyles}
  resize: vertical;
  min-height: 100px;
`;

export const Select = styled.select`
  ${baseInputStyles}
  cursor: pointer;
`;

export const InputWrapper = styled.div`
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

export const InputLabel = styled.label`
  font-size: 13px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
`;

export const InputError = styled.span`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.error};
`;

export default Input;
