import React from 'react';
import styled from 'styled-components';
import UserList from '../../User/UserList';
import { SectionHeader, SectionTitle, SectionSubtitle } from '../ui/AdminUI';

const UserManagementSection = () => {
  return (
    <Wrapper>
      <SectionHeader>
        <SectionTitle>사용자 관리</SectionTitle>
        <SectionSubtitle>전체 사용자 목록 및 권한/정지 관리를 수행합니다.</SectionSubtitle>
      </SectionHeader>
      <Content>
        <UserList showHeader={false} />
      </Content>
    </Wrapper>
  );
};

export default UserManagementSection;

const Wrapper = styled.div``;

const Content = styled.div`
  margin-top: ${props => props.theme.spacing.md};
`;
