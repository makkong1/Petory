import React from 'react';
import styled from 'styled-components';

const RADIUS_OPTIONS = [1, 3, 5, 10];

const RadiusFilter = ({ radius, onRadiusChange }) => {
  return (
    <FilterBar>
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
  gap: 4px;
  padding: 8px 10px 8px 14px;
`;

const RadiusButton = styled.button`
  padding: 6px 14px;
  border-radius: 50px;
  border: 1.5px solid ${props => props.$active ? props.theme.colors.primary : props.theme.colors.border};
  background: ${props => props.$active ? props.theme.colors.primarySoft : props.theme.colors.surfaceElevated + 'D9'};
  color: ${props => props.$active ? props.theme.colors.primary : props.theme.colors.textSecondary};
  font-size: 12px;
  font-weight: ${props => props.$active ? '600' : '400'};
  cursor: pointer;
  transition: all 0.2s ease;
  backdrop-filter: blur(8px);
  white-space: nowrap;

  &:hover {
    border-color: ${props => props.theme.colors.primary};
    color: ${props => props.theme.colors.primary};
  }
`;
