import React from 'react';
import styled from 'styled-components';
import UserList from '../../User/UserList';

const UserManagementSection = () => {
  return (
    <Wrapper>
      <Header>
        <Title>사용자 관리</Title>
        <Subtitle>전체 사용자 목록 및 권한/정지 관리를 수행합니다.</Subtitle>
      </Header>
      <Content>
        {/* 이미있는 페이지를 여기로 가져옴 */}
        <UserList />
      </Content>
    </Wrapper>
  );
};

export default UserManagementSection;

const Wrapper = styled.div``;

const Header = styled.div`
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const Title = styled.h1`
  font-size: ${props => props.theme.typography.h2.fontSize};
  font-weight: ${props => props.theme.typography.h2.fontWeight};
  margin-bottom: ${props => props.theme.spacing.xs};
`;

const Subtitle = styled.p`
  color: ${props => props.theme.colors.textSecondary};
`;

const Content = styled.div`
  margin-top: ${props => props.theme.spacing.md};
`;

