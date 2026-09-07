import { normalizeErrorData } from './apiClient';

describe('normalizeErrorData', () => {
  it('message만 있으면 error에도 복사한다 (.error만 읽는 호출부 대응)', () => {
    const error = { response: { data: { message: '잔액이 부족합니다.' } } };
    normalizeErrorData(error);
    expect(error.response.data.error).toBe('잔액이 부족합니다.');
  });

  it('error만 있으면 message에도 복사한다 (.message만 읽는 호출부 대응)', () => {
    const error = { response: { data: { error: '권한이 없습니다.' } } };
    normalizeErrorData(error);
    expect(error.response.data.message).toBe('권한이 없습니다.');
  });

  it('둘 다 있으면 그대로 둔다', () => {
    const error = { response: { data: { message: 'M', error: 'E' } } };
    normalizeErrorData(error);
    expect(error.response.data).toEqual({ message: 'M', error: 'E' });
  });

  it('response가 없으면 에러 없이 통과한다', () => {
    const error = {};
    expect(() => normalizeErrorData(error)).not.toThrow();
  });
});
