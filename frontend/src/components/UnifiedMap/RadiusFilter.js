import React from 'react';
import styled from 'styled-components';

const RADIUS_OPTIONS = [1, 3, 5, 10];

const RadiusFilter = ({ radius, onRadiusChange }) => {
  return (
    <FilterBar>
      <Label>반경</Label>
      {RADIUS_OPTIONS.map(r => (
        <RadiusButton
          key={r}
          $active={radius === r}
          onClick={() => onRadiusChange(r)}
        >
          {r}km
        </RadiusButton>
      ))}
    </FilterBar>
  );
};

export default RadiusFilter;

const FilterBar = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: ${props => props.theme.colors.surface};
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;

const Label = styled.span`
  font-size: 13px;
  color: ${props => props.theme.colors.textSecondary};
  margin-right: 4px;
`;

const RadiusButton = styled.button`
  padding: 4px 12px;
  border-radius: 14px;
  border: 1px solid ${props => props.$active ? props.theme.colors.primary : props.theme.colors.border};
  background: ${props => props.$active ? props.theme.colors.primary : props.theme.colors.background};
  color: ${props => props.$active ? 'white' : props.theme.colors.text};
  font-size: 13px;
  font-weight: ${props => props.$active ? 600 : 400};
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    border-color: ${props => props.theme.colors.primary};
    color: ${props => props.$active ? 'white' : props.theme.colors.primary};
  }
`;
