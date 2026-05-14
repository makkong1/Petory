import React from 'react';
import styled from 'styled-components';
import {
  BrandWordmark,
  BrandFloatingArea,
  FloatingGlassCard,
  BrandSloganGroup,
  BrandSlogan,
  FormInner,
} from './AuthShell';

/**
 * AuthLayout
 *
 * 로그인 ↔ 회원가입 전환 시 BrandPanel 이 좌↔우로 swap 되는 split-screen 레이아웃.
 *
 * - mode='login'    → [BrandPanel 좌(45%)] [FormPanel 우(55%)]
 * - mode='register' → [FormPanel 좌(55%)] [BrandPanel 우(45%)]
 *
 * transform: translateX 로 위치를 swap 하므로 layout 재계산 없이 자연스럽게 슬라이드된다.
 * 두 폼 (loginContent, registerContent) 은 동시에 마운트되어 폼 state 가 유지되며,
 * 보이지 않는 폼은 opacity:0 + pointer-events:none 으로 비활성화된다.
 */
const AuthLayout = ({ mode, loginContent, registerContent }) => (
  <Wrapper>
    <BrandPanel $mode={mode}>
      <BrandWordmark>PETORY</BrandWordmark>
      <BrandFloatingArea>
        <FloatingGlassCard $size={80} $top="10%" $left="18%" $rotate={-8}>🐶</FloatingGlassCard>
        <FloatingGlassCard $size={68} $top="38%" $right="14%" $rotate={6}>🐱</FloatingGlassCard>
        <FloatingGlassCard $size={92} $top="58%" $left="8%" $rotate={-4}>🐾</FloatingGlassCard>
        <FloatingGlassCard $size={60} $bottom="12%" $right="22%" $rotate={10}>🦴</FloatingGlassCard>
      </BrandFloatingArea>
      <BrandSloganGroup>
        <BrandSlogan>{'반려동물과 함께하는\n모든 순간'}</BrandSlogan>
      </BrandSloganGroup>
    </BrandPanel>

    <FormPanel $mode={mode}>
      <FormStack>
        <FormSlide $active={mode === 'login'} aria-hidden={mode !== 'login'}>
          <FormInner>{loginContent}</FormInner>
        </FormSlide>
        <FormSlide $active={mode === 'register'} aria-hidden={mode !== 'register'}>
          <FormInner>{registerContent}</FormInner>
        </FormSlide>
      </FormStack>
    </FormPanel>
  </Wrapper>
);

export default AuthLayout;

// ===== Styled =====

const TRANSITION = 'cubic-bezier(0.4, 0, 0.2, 1)';
const DURATION = '0.6s';

const Wrapper = styled.div`
  color-scheme: light;
  min-height: 100vh;
  display: flex;
  background: ${({ theme }) => theme.colors.background};
  position: relative;
  overflow-x: hidden;

  @media (min-width: 1024px) {
    flex-direction: row;
  }

  @media (max-width: 1023px) {
    width: 100%;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: max(40px, env(safe-area-inset-top, 40px)) 20px max(40px, env(safe-area-inset-bottom, 40px));
  }
`;

const BrandPanel = styled.aside`
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
    transform: translateX(${({ $mode }) => ($mode === 'login' ? '0%' : '122.222%')});
    transition: transform ${DURATION} ${TRANSITION};
    will-change: transform;
  }
`;

const FormPanel = styled.section`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;

  @media (min-width: 1024px) {
    flex: 0 0 55%;
    padding: 64px 56px;
    transform: translateX(${({ $mode }) => ($mode === 'login' ? '0%' : '-81.818%')});
    transition: transform ${DURATION} ${TRANSITION};
    will-change: transform;
  }

  @media (max-width: 1023px) {
    padding: 0;
  }
`;

/**
 * FormStack: 두 폼을 같은 grid cell 에 겹쳐 놓고 $active 인 폼만 보이도록 한다.
 * 두 폼 모두 mount 상태이므로 폼 입력값이 모드 전환 사이에 유지된다.
 *
 * grid-template-rows: auto → cell height 는 두 슬라이드 중 큰 content 기준.
 * 회원가입 폼이 길어도 FormSlide 의 max-height 가 viewport 안에 가둬서
 * BrandPanel 도 viewport 안에 머문다.
 */
const FormStack = styled.div`
  position: relative;
  width: 100%;
  max-width: 420px;
  display: grid;
  grid-template-columns: 1fr;
  grid-template-rows: auto;
`;

const FormSlide = styled.div`
  grid-column: 1;
  grid-row: 1;
  align-self: start;
  width: 100%;
  max-height: calc(100vh - 128px);
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 4px;
  opacity: ${({ $active }) => ($active ? 1 : 0)};
  pointer-events: ${({ $active }) => ($active ? 'auto' : 'none')};
  transform: translateY(${({ $active }) => ($active ? '0' : '8px')});
  transition: opacity ${DURATION} ${TRANSITION},
              transform ${DURATION} ${TRANSITION};
  will-change: opacity, transform;

  /* 얇은 스크롤바 (스크롤 필요 시에만 노출) */
  scrollbar-width: thin;
  scrollbar-color: ${({ theme }) => theme.colors.border} transparent;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: ${({ theme }) => theme.colors.border};
    border-radius: 3px;
  }

  @media (max-width: 1023px) {
    max-height: none;
    overflow: visible;
    padding-right: 0;
  }
`;
