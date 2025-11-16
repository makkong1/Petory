import React, { useEffect, useState } from 'react';
import styled from 'styled-components';
import { reportApi } from '../../../api/reportApi';

const ReportDetailModal = ({ reportId, onClose, onHandled }) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [detail, setDetail] = useState(null);
  const [status, setStatus] = useState('RESOLVED');
  const [actionTaken, setActionTaken] = useState('NONE');
  const [adminNote, setAdminNote] = useState('');
  const [readOnly, setReadOnly] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const fetchDetail = async () => {
      try {
        setLoading(true);
        setError(null);
        const res = await reportApi.getDetail(reportId);
        const data = res.data;
        setDetail(data);
        // PENDING이 아니면 읽기 전용
        const isReadOnly = data?.report?.status && data.report.status !== 'PENDING';
        setReadOnly(!!isReadOnly);
        if (isReadOnly) {
          // 현재 상태 값 반영
          setStatus(data.report.status);
          setActionTaken(data.report.actionTaken || 'NONE');
          setAdminNote(data.report.adminNote || '');
        }
      } catch (e) {
        setError('신고 상세를 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };
    if (reportId) fetchDetail();
  }, [reportId]);

  const handleSubmit = async () => {
    try {
      setSubmitting(true);
      await reportApi.handle(reportId, { status, actionTaken, adminNote });
      onHandled?.();
      onClose?.();
    } catch (e) {
      alert('신고 처리에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!reportId) return null;

  return (
    <Overlay>
      <Modal>
        <Header>
          <Title>신고 상세</Title>
          <CloseButton onClick={onClose}>✕</CloseButton>
        </Header>
        {loading ? (
          <Message>로딩 중...</Message>
        ) : error ? (
          <Message>{error}</Message>
        ) : (
          <>
            <Section>
              <SectionTitle>신고 정보</SectionTitle>
              <Row><Label>ID</Label><Value>#{detail?.report?.idx}</Value></Row>
              <Row><Label>대상</Label><Value>{detail?.report?.targetType} #{detail?.report?.targetIdx}</Value></Row>
              <Row><Label>신고자</Label><Value>{detail?.report?.reporterName || '-'} (#{detail?.report?.reporterId})</Value></Row>
              <Row><Label>사유</Label><Value>{detail?.report?.reason}</Value></Row>
              <Row><Label>상태</Label><Value>{detail?.report?.status}</Value></Row>
            </Section>
            <Section>
              <SectionTitle>대상 미리보기</SectionTitle>
              <PreviewCard>
                <PreviewTitle>
                  제목 : {detail?.target?.title || '(제목 없음)'} <small>({detail?.target?.type} #{detail?.target?.id})</small>
                </PreviewTitle>
                {detail?.target?.summary && (
                  <PreviewSummary>내용 : {detail?.target?.summary}</PreviewSummary>
                )}
                {detail?.target?.authorName && (
                  <PreviewMeta>작성자: {detail?.target?.authorName}</PreviewMeta>
                )}
              </PreviewCard>
            </Section>
            <Section>
              <SectionTitle>관리자 처리</SectionTitle>
              <FormRow>
                <FormLabel>처리 상태</FormLabel>
                <Select value={status} onChange={e => setStatus(e.target.value)} disabled={readOnly}>
                  <option value="RESOLVED">RESOLVED (처리완료)</option>
                  <option value="REJECTED">REJECTED (반려)</option>
                </Select>
              </FormRow>
              <FormRow>
                <FormLabel>조치</FormLabel>
                <Select value={actionTaken} onChange={e => setActionTaken(e.target.value)} disabled={readOnly}>
                  <option value="NONE">조치 없음</option>
                  <option value="DELETE_CONTENT">콘텐츠 삭제</option>
                  <option value="SUSPEND_USER">유저 정지</option>
                  <option value="WARN_USER">유저 경고</option>
                  <option value="OTHER">기타 조치</option>
                </Select>
              </FormRow>
              <FormRow>
                <FormLabel>관리자 메모</FormLabel>
                <TextArea
                  rows={4}
                  value={adminNote}
                  onChange={e => setAdminNote(e.target.value)}
                  placeholder="처리 사유나 메모를 입력하세요"
                  disabled={readOnly}
                />
              </FormRow>
              <Actions>
                {!readOnly && (
                  <SubmitButton disabled={submitting} onClick={handleSubmit}>
                    {submitting ? '처리 중...' : '처리하기'}
                  </SubmitButton>
                )}
                <CancelButton onClick={onClose}>닫기</CancelButton>
              </Actions>
            </Section>
          </>
        )}
      </Modal>
    </Overlay>
  );
};

export default ReportDetailModal;

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
`;

const Modal = styled.div`
  width: min(860px, 92vw);
  max-height: 88vh;
  overflow: auto;
  background: ${props => props.theme.colors.surface};
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.lg};
  box-shadow: 0 10px 28px ${props => props.theme.colors.shadow};
  padding: ${props => props.theme.spacing.lg};
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${props => props.theme.spacing.md};
`;

const Title = styled.h2`
  margin: 0;
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  cursor: pointer;
  color: ${props => props.theme.colors.textSecondary};
  font-size: 18px;
`;

const Section = styled.div`
  margin-bottom: ${props => props.theme.spacing.lg};
`;

const SectionTitle = styled.h3`
  margin-bottom: ${props => props.theme.spacing.sm};
`;

const Row = styled.div`
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: ${props => props.theme.spacing.sm};
  padding: 6px 0;
  border-bottom: 1px dashed ${props => props.theme.colors.border};
`;

const Label = styled.div`
  color: ${props => props.theme.colors.textSecondary};
`;

const Value = styled.div``;

const PreviewCard = styled.div`
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: ${props => props.theme.borderRadius.md};
  padding: ${props => props.theme.spacing.md};
  background: ${props => props.theme.colors.surfaceSoft};
`;

const PreviewTitle = styled.div`
  font-weight: 600;
`;

const PreviewMeta = styled.div`
  color: ${props => props.theme.colors.textSecondary};
  font-size: ${props => props.theme.typography.caption.fontSize};
  margin-top: 4px;
`;

const PreviewSummary = styled.pre`
  white-space: pre-wrap;
  margin-top: ${props => props.theme.spacing.sm};
  font-family: inherit;
`;

const FormRow = styled.div`
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: ${props => props.theme.spacing.sm};
  margin-bottom: ${props => props.theme.spacing.sm};
  align-items: center;
`;

const FormLabel = styled.label`
  color: ${props => props.theme.colors.textSecondary};
`;

const Select = styled.select`
  padding: 8px 10px;
  border-radius: ${props => props.theme.borderRadius.md};
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
`;

const TextArea = styled.textarea`
  padding: 8px 10px;
  border-radius: ${props => props.theme.borderRadius.md};
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  resize: vertical;
`;

const Actions = styled.div`
  display: flex;
  gap: ${props => props.theme.spacing.sm};
  margin-top: ${props => props.theme.spacing.md};
`;

const SubmitButton = styled.button`
  background: ${props => props.theme.colors.primary};
  color: white;
  border: none;
  padding: 10px 16px;
  border-radius: ${props => props.theme.borderRadius.md};
  cursor: pointer;
`;

const CancelButton = styled.button`
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.text};
  border: 1px solid ${props => props.theme.colors.border};
  padding: 10px 16px;
  border-radius: ${props => props.theme.borderRadius.md};
  cursor: pointer;
`;

const Message = styled.div`
  padding: ${props => props.theme.spacing.lg};
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
`;


