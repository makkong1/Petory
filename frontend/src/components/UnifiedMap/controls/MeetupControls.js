import React from 'react';
import styled from 'styled-components';

const MeetupControls = ({ onCreateClick }) => {
  return (
    <Wrapper>
      <CreateButton onClick={onCreateClick}>
        ➕ 모임 만들기
      </CreateButton>
    </Wrapper>
  );
};

export default MeetupControls;

const Wrapper = styled.div`
  padding: 8px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
`;

const CreateButton = styled.button`
  padding: 7px 16px;
  border-radius: 8px;
  border: none;
  background: ${props => props.theme.colors.domain.meetup};
  color: white;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s;

  &:hover { opacity: 0.88; }
`;
