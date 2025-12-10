import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { locationServiceApi } from '../../api/locationServiceApi';

const initial = {
  name: '',
  category: '',
  category1: '',
  category2: '',
  category3: '',
  address: '',
  detailAddress: '',
  sido: '',
  sigungu: '',
  eupmyeondong: '',
  ri: '',
  bunji: '',
  roadName: '',
  buildingNumber: '',
  zipCode: '',
  description: '',
  operatingHours: '', // 운영시간 문자열 (예: "월~금 09:00~18:00")
  closedDay: '',
  phone: '',
  website: '',
  parkingAvailable: false,
  priceInfo: '',
  petFriendly: false,
  isPetOnly: false,
  petSize: '',
  petRestrictions: '',
  petExtraFee: '',
  indoor: false,
  outdoor: false,
};

const categories = ['', '병원', '용품점', '유치원', '카페', '호텔', '미용실'];

export default function LocationServiceForm({ show, onClose, onSuccess }) {
  const [fields, setFields] = useState(initial);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // 다움 주소 스크립트 동적 로드 (없으면)
    if (show && !window.daum?.Postcode) {
      const script = document.createElement('script');
      script.src = '//t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
      script.async = true;
      document.body.appendChild(script);
      return () => { document.body.removeChild(script); };
    }
  }, [show]);

  // 주소검색 버튼 동작
  const handleAddressSearch = () => {
    if (!window.daum?.Postcode) { 
      alert('주소 검색 스크립트 로드중입니다. 잠시 후 다시 시도'); 
      return; 
    }
    
    new window.daum.Postcode({
      oncomplete: function(data) {
        const address = data.roadAddress || data.jibunAddress;
        setFields(f => ({ ...f, address }));
        // 위도/경도는 서버에서 자동으로 변환됩니다.
      }
    }).open();
  };

  if (!show) return null;

  const handleC = (e) => setFields({ ...fields, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const payload = {
        name: fields.name,
        category: fields.category3 || fields.category2 || fields.category1 || fields.category,
        category1: fields.category1,
        category2: fields.category2,
        category3: fields.category3,
        address: fields.address,
        detailAddress: fields.detailAddress || '',
        sido: fields.sido,
        sigungu: fields.sigungu,
        eupmyeondong: fields.eupmyeondong,
        ri: fields.ri,
        bunji: fields.bunji,
        roadName: fields.roadName,
        buildingNumber: fields.buildingNumber,
        zipCode: fields.zipCode,
        description: fields.description,
        operatingHours: fields.operatingHours || '',
        closedDay: fields.closedDay || '',
        phone: fields.phone,
        website: fields.website || '',
        parkingAvailable: fields.parkingAvailable || false,
        priceInfo: fields.priceInfo || '',
        petFriendly: fields.petFriendly || false,
        isPetOnly: fields.isPetOnly || false,
        petSize: fields.petSize || '',
        petRestrictions: fields.petRestrictions || '',
        petExtraFee: fields.petExtraFee || '',
        indoor: fields.indoor || false,
        outdoor: fields.outdoor || false,
      };
      await locationServiceApi.createService(payload);
      if (onSuccess) onSuccess({ ...payload });
    } catch (err) {
      setError('등록 실패: ' + (err?.response?.data?.error || err.message));
    }
    setLoading(false);
  };

  return (
    <ModalBg>
      <ModalBox>
        <FormTitle>서비스 등록</FormTitle>
        <StyledForm onSubmit={handleSubmit} autoComplete="off">
          {/* 기본 정보 */}
          <FieldGroup>
            <label htmlFor="name">이름</label>
            <StyledInput id="name" name="name" placeholder="이름" value={fields.name} onChange={handleC} required />
          </FieldGroup>

          <FieldGroup>
            <label htmlFor="category">카테고리</label>
            <StyledSelect id="category" name="category" value={fields.category} onChange={handleC} required>
              <option value="" disabled>카테고리 선택</option>
              {categories.slice(1).map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </StyledSelect>
          </FieldGroup>

          <FieldGroup>
            <label style={labelStyle} htmlFor="address">주소</label>
            <div style={{display:'flex',gap:'0.5rem'}}>
              <StyledInput id="address" name="address" placeholder="주소" value={fields.address} onChange={handleC} required readOnly style={{flex:1}} />
              <AddressBtn type="button" onClick={handleAddressSearch}>주소찾기</AddressBtn>
            </div>
          </FieldGroup>
          <FieldGroup>
            <label style={labelStyle} htmlFor="detail_address">상세주소</label>
            <StyledInput id="detail_address" name="detailAddress" placeholder="예: 301동 1205호, 3층 우측 등" value={fields.detailAddress||''} onChange={handleC} autoComplete="off" />
          </FieldGroup>


          {/* 추가 정보 */}
          <FieldGroup>
            <label htmlFor="description">설명</label>
            <StyledInput id="description" name="description" placeholder="예: 애견 동반 가능, 넓은 주차장 등" value={fields.description} onChange={handleC} />
          </FieldGroup>

          <FieldGroup>
            <label htmlFor="operating_hours">운영시간</label>
            <StyledInput 
              id="operating_hours" 
              name="operatingHours" 
              placeholder="예: 월~금 09:00~18:00" 
              value={fields.operatingHours} 
              onChange={handleC} 
            />
          </FieldGroup>

          <FieldGroup>
            <label htmlFor="closed_day">휴무일</label>
            <StyledInput 
              id="closed_day" 
              name="closedDay" 
              placeholder="예: 매주 월요일, 법정공휴일" 
              value={fields.closedDay} 
              onChange={handleC} 
            />
          </FieldGroup>

          <FieldGroup>
            <label htmlFor="phone">전화번호</label>
            <StyledInput id="phone" name="phone" placeholder="예: 02-123-4567" value={fields.phone} onChange={handleC} />
          </FieldGroup>

          <FieldGroup>
            <label htmlFor="website">웹사이트</label>
            <StyledInput id="website" name="website" placeholder="홈페이지 주소 (선택)" value={fields.website} onChange={handleC} />
          </FieldGroup>

          <FieldGroup>
            <label htmlFor="price_info">가격 정보</label>
            <StyledInput id="price_info" name="priceInfo" placeholder="예: 입장료 5,000원" value={fields.priceInfo} onChange={handleC} />
          </FieldGroup>

          <FieldGroup>
            <label>
              <input 
                type="checkbox" 
                name="parkingAvailable" 
                checked={fields.parkingAvailable || false} 
                onChange={(e) => setFields({ ...fields, parkingAvailable: e.target.checked })} 
              />
              주차 가능
            </label>
          </FieldGroup>

          <FieldGroup>
            <label>
              <input 
                type="checkbox" 
                name="petFriendly" 
                checked={fields.petFriendly || false} 
                onChange={(e) => setFields({ ...fields, petFriendly: e.target.checked })} 
              />
              반려동물 동반 가능
            </label>
          </FieldGroup>

          <FieldGroup>
            <label>
              <input 
                type="checkbox" 
                name="isPetOnly" 
                checked={fields.isPetOnly || false} 
                onChange={(e) => setFields({ ...fields, isPetOnly: e.target.checked })} 
              />
              반려동물 전용
            </label>
          </FieldGroup>

          <FieldGroup>
            <label htmlFor="pet_size">입장 가능 동물 크기</label>
            <StyledInput id="pet_size" name="petSize" placeholder="예: 소형견, 중형견, 대형견" value={fields.petSize} onChange={handleC} />
          </FieldGroup>

          <FieldGroup>
            <label htmlFor="pet_restrictions">반려동물 제한사항</label>
            <StyledInput id="pet_restrictions" name="petRestrictions" placeholder="예: 목줄 필수, 리드줄 필수" value={fields.petRestrictions} onChange={handleC} />
          </FieldGroup>

          <FieldGroup>
            <label htmlFor="pet_extra_fee">애견 동반 추가 요금</label>
            <StyledInput id="pet_extra_fee" name="petExtraFee" placeholder="예: 소형견 3,000원" value={fields.petExtraFee} onChange={handleC} />
          </FieldGroup>

          <FlexRow>
            <FieldGroup>
              <label>
                <input 
                  type="checkbox" 
                  name="indoor" 
                  checked={fields.indoor || false} 
                  onChange={(e) => setFields({ ...fields, indoor: e.target.checked })} 
                />
                실내
              </label>
            </FieldGroup>
            <FieldGroup>
              <label>
                <input 
                  type="checkbox" 
                  name="outdoor" 
                  checked={fields.outdoor || false} 
                  onChange={(e) => setFields({ ...fields, outdoor: e.target.checked })} 
                />
                실외
              </label>
            </FieldGroup>
          </FlexRow>

          {/* 에러 및 버튼 */}
          {error && <ErrorText>{error}</ErrorText>}
          <ButtonRow>
            <CancelBtn type="button" onClick={onClose}>취소</CancelBtn>
            <SubmitBtn type="submit" disabled={loading}>{loading ? '등록중...' : '등록하기'}</SubmitBtn>
          </ButtonRow>
        </StyledForm>
      </ModalBox>
    </ModalBg>
  );
}

/* Styled-components 아래 동일 */
const ModalBg = styled.div`
  position: fixed; 
  z-index: 1100;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.16);
  display: flex;
  align-items: center;
  justify-content: center;
`;
const ModalBox = styled.div`
  padding: 1.3rem 1.2rem 1.4rem;
  background: #fff;
  border-radius: 11px;
  min-width: 400px;
  max-width: 90vw;
  box-shadow: 0 4px 18px 0 rgba(0,0,0,.11);
  position: relative;
`;
const FormTitle = styled.h2`
  text-align: center;
  margin-bottom: 1rem;
  font-size: 1.16rem;
  font-weight: 700;
  color: #23242c;
  letter-spacing: -.5px;
`;
const StyledForm = styled.form`
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
`;
const FieldGroup = styled.div`
  color: black;
  display: flex;
  flex-direction: column;
  gap: 0.22rem;
`;
const FlexRow = styled.div`
  display: flex;
  gap: 0.7rem;
`;
const StyledInput = styled.input`
  padding: 0.4rem 0.9rem;
  border: 1.2px solid #dcdfe3;
  border-radius: 6px;
  font-size: 0.99rem;
  color: #212227;
  background: #fff;
  outline: none;
  transition: border .16s;

  &::placeholder{
    color:#bbbbbb;
    opacity:1;
  }

  &:focus {
    border-color:#45a0fe; 
    background:#fafdff; 
  }
`;
const StyledSelect = styled.select`
  padding: 0.4rem 0.9rem;
  border: 1.2px solid #dcdfe3;
  border-radius: 6px;
  font-size: 0.98rem;
  color: #212227;
  background: #fff;
  outline: none;
  
  &::placeholder{
   color: #bbbbbb;
  }

  &:focus{
    border-color:#45a0fe;
    background:#fafdff;
  }
`;
const ButtonRow = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: 0.7rem;
  margin-top: .2rem;
`;
const SubmitBtn = styled.button`
  background: #2788eb;
  color: #fff;
  padding: 0.5rem 1.2rem;
  font-size: 1rem;
  font-weight: bold;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background .13s;
  
  &:hover:enabled{
    background: #176dd1;
  }

  &:disabled{
    opacity:.67;
    cursor:not-allowed;
  }
`;
const CancelBtn = styled.button`
  background: #f4f7fa;
  color: #444;
  padding: 0.5rem 1.1rem;
  font-size: 1rem;
  border: 1.1px solid #cdd6e2;
  border-radius: 6px;
  cursor: pointer;
  transition:background .12s,color .12s;
  &:hover{
    background:#e9eff4;
    color:#2788eb;
  }
`;
const ErrorText = styled.div`
  color: #df3737;
  font-size: 0.95rem;
  text-align: center;
  margin-top: .1rem;`
// 라벨 스타일 개선
const labelStyle = {
  color:'#787b85',fontSize:'0.95em',fontWeight:500,marginBottom:'2px',marginLeft:'2px'};

const AddressBtn = styled.button`
  background: #eee;
  color: #2060b7;
  font-size: 0.98em;
  padding: 0.43rem 0.9rem;
  border: 1px solid #d9e3ee;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
  transition: background .14s,color .14s;
  &:hover{
    background: #deefff;
    color: #1084fe;
    border-color: #b6d7fa;
  }
`;
