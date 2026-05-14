import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { userProfileApi } from '../../api/userApi';
import {
  FormHeader,
  FormHeaderLogo,
  FormTitle,
  FormSubtitle,
  PillInput,
  PillSelect,
  GradientButton,
  OutlineButton,
  FormSwitchLink,
} from './AuthShell';

const RegisterForm = ({ onRegisterSuccess, onSwitchToLogin }) => {
  const { register } = useAuth();
  const [formData, setFormData] = useState({
    id: '',
    username: '',
    nickname: '',
    password: '',
    email: '',
    emailId: '', // @ 앞부분
    emailDomain: 'gmail.com', // @ 뒷부분 (도메인)
    customEmailDomain: '', // 직접 입력 시 도메인
    role: 'USER',
    location: '',
    petInfo: ''
  });
  const [pets, setPets] = useState([]); // 반려동물 목록
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [nicknameCheck, setNicknameCheck] = useState({ checking: false, available: null, message: '' });
  const [idCheck, setIdCheck] = useState({ checking: false, available: null, message: '' });
  const [emailVerified, setEmailVerified] = useState(false); // 이메일 인증 완료 여부
  const [emailVerificationSending, setEmailVerificationSending] = useState(false); // 이메일 발송 중 여부
  const [emailVerificationSent, setEmailVerificationSent] = useState(false); // 이메일 발송 완료 여부
  const [emailVerificationError, setEmailVerificationError] = useState(''); // 이메일 인증 관련 에러 메시지

  // URL 파라미터에서 이메일 인증 완료 상태 확인
  useEffect(() => {
    const urlParams = new URLSearchParams(window.location.search);
    const emailVerifiedParam = urlParams.get('emailVerified');
    const emailParam = urlParams.get('email');

    if (emailVerifiedParam === 'true' && emailParam) {
      // 이메일 인증 완료 상태로 설정
      setEmailVerified(true);
      setSuccess('이메일 인증이 완료되었습니다. 회원가입을 진행해주세요.');

      // URL에서 파라미터 제거
      window.history.replaceState({}, '', window.location.pathname);

      // 이메일 자동 입력 (선택적)
      if (emailParam && !formData.emailId) {
        const emailParts = emailParam.split('@');
        if (emailParts.length === 2) {
          setFormData(prev => ({
            ...prev,
            emailId: emailParts[0],
            emailDomain: emailParts[1],
            email: emailParam
          }));
        }
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => {
      const updated = {
        ...prev,
        [name]: value
      };
      // 이메일 ID나 도메인 변경 시 전체 이메일 자동 조합
      if (name === 'emailId' || name === 'emailDomain' || name === 'customEmailDomain') {
        const finalEmail = updated.emailDomain === 'custom'
          ? (updated.emailId && updated.customEmailDomain ? `${updated.emailId}@${updated.customEmailDomain}` : '')
          : (updated.emailId && updated.emailDomain ? `${updated.emailId}@${updated.emailDomain}` : '');
        updated.email = finalEmail;

        // 이메일 변경 시 인증 상태 초기화
        if (emailVerified || emailVerificationSent) {
          setEmailVerified(false);
          setEmailVerificationSent(false);
        }
        // 이메일 변경 시 인증 에러 메시지 초기화
        if (emailVerificationError) {
          setEmailVerificationError('');
        }
      }
      return updated;
    });
    // 에러 메시지 초기화
    if (error) setError('');
    // 닉네임 변경 시 중복 검사 상태 초기화
    if (name === 'nickname') {
      setNicknameCheck({ checking: false, available: null, message: '' });
    }
    // 아이디 변경 시 중복 검사 상태 초기화
    if (name === 'id') {
      setIdCheck({ checking: false, available: null, message: '' });
    }
  };

  // 아이디 중복 검사
  const handleIdCheck = async () => {
    if (!formData.id || formData.id.trim().length === 0) {
      setIdCheck({ checking: false, available: false, message: '아이디를 입력해주세요.' });
      return;
    }

    setIdCheck({ checking: true, available: null, message: '확인 중...' });

    try {
      const response = await userProfileApi.checkIdAvailability(formData.id);
      setIdCheck({
        checking: false,
        available: response.data.available,
        message: response.data.message
      });
    } catch (error) {
      console.error('아이디 중복 검사 실패:', error);
      setIdCheck({
        checking: false,
        available: false,
        message: '아이디 중복 검사 중 오류가 발생했습니다.'
      });
    }
  };

  // 닉네임 중복 검사
  const handleNicknameCheck = async () => {
    if (!formData.nickname || formData.nickname.trim().length === 0) {
      setNicknameCheck({ checking: false, available: false, message: '닉네임을 입력해주세요.' });
      return;
    }

    if (formData.nickname.length > 50) {
      setNicknameCheck({ checking: false, available: false, message: '닉네임은 50자 이하여야 합니다.' });
      return;
    }

    setNicknameCheck({ checking: true, available: null, message: '확인 중...' });

    try {
      const response = await userProfileApi.checkNicknameAvailability(formData.nickname);
      setNicknameCheck({
        checking: false,
        available: response.data.available,
        message: response.data.message
      });
    } catch (error) {
      console.error('닉네임 중복 검사 실패:', error);
      setNicknameCheck({
        checking: false,
        available: false,
        message: '닉네임 중복 검사 중 오류가 발생했습니다.'
      });
    }
  };

  // 반려동물 추가
  const handleAddPet = () => {
    setPets([...pets, {
      petName: '',
      petType: 'DOG',
      customPetType: '', // 기타 선택 시 입력할 종류
      breed: '',
      gender: 'UNKNOWN',
      age: '',
      color: '',
      weight: '',
      isNeutered: false,
      birthDate: '',
      healthInfo: '',
      specialNotes: ''
    }]);
  };

  // 반려동물 삭제
  const handleRemovePet = (index) => {
    setPets(pets.filter((_, i) => i !== index));
  };

  // 반려동물 정보 변경
  const handlePetChange = (index, field, value) => {
    const updatedPets = [...pets];
    updatedPets[index] = { ...updatedPets[index], [field]: value };
    setPets(updatedPets);
  };

  // 이메일 인증 메일 발송 (회원가입 전)
  const handleSendVerificationEmail = async () => {
    // 이메일 필수 검증
    if (!formData.emailId || formData.emailId.trim().length === 0) {
      setEmailVerificationError('이메일 아이디를 입력해주세요.');
      return;
    }
    if (!formData.emailDomain || formData.emailDomain.trim().length === 0) {
      setEmailVerificationError('이메일 도메인을 선택해주세요.');
      return;
    }
    if (formData.emailDomain === 'custom' && (!formData.customEmailDomain || formData.customEmailDomain.trim().length === 0)) {
      setEmailVerificationError('이메일 도메인을 입력해주세요.');
      return;
    }

    // 최종 이메일 조합
    const finalEmail = formData.emailDomain === 'custom'
      ? `${formData.emailId}@${formData.customEmailDomain}`
      : `${formData.emailId}@${formData.emailDomain}`;

    setEmailVerificationSending(true);
    setEmailVerificationError('');
    setSuccess('');

    try {
      const response = await userProfileApi.sendPreRegistrationVerificationEmail(finalEmail);
      
      // 응답의 success 필드를 확인
      if (response.data && response.data.success === false) {
        setEmailVerificationError(response.data.message || '이메일 발송에 실패했습니다.');
        setEmailVerificationSent(false);
      } else {
        setEmailVerificationSent(true);
        setEmailVerificationError('');
        setSuccess('이메일 인증 메일이 발송되었습니다. 이메일을 확인해주세요.');
      }
    } catch (error) {
      console.error('이메일 인증 메일 발송 실패:', error);
      setEmailVerificationError(error.response?.data?.message || '이메일 발송에 실패했습니다.');
      setEmailVerificationSent(false);
    } finally {
      setEmailVerificationSending(false);
    }
  };

  // 이메일 인증 완료 여부 확인 (주기적으로 체크)
  useEffect(() => {
    if (!emailVerified && emailVerificationSent && formData.email) {
      const checkVerification = async () => {
        try {
          const response = await userProfileApi.checkPreRegistrationVerification(formData.email);
          if (response.data.verified) {
            setEmailVerified(true);
            setSuccess('이메일 인증이 완료되었습니다. 회원가입을 진행해주세요.');
          }
        } catch (error) {
          // 에러는 무시 (아직 인증 안 됨)
        }
      };

      // 3초마다 확인 (이메일 발송 후에만)
      const interval = setInterval(checkVerification, 3000);
      return () => clearInterval(interval);
    }
  }, [formData.email, emailVerified, emailVerificationSent]);

  const handleSubmit = async (e) => {
    e.preventDefault();

    // 아이디 필수 검증
    if (!formData.id || formData.id.trim().length === 0) {
      setError('아이디를 입력해주세요.');
      return;
    }

    // 아이디 중복 검사 확인
    if (idCheck.available === null) {
      setError('아이디 중복 검사를 먼저 해주세요.');
      return;
    }

    if (!idCheck.available) {
      setError('사용할 수 없는 아이디입니다.');
      return;
    }

    // 이메일 필수 검증
    if (!formData.emailId || formData.emailId.trim().length === 0) {
      setError('이메일 아이디를 입력해주세요.');
      return;
    }
    if (!formData.emailDomain || formData.emailDomain.trim().length === 0) {
      setError('이메일 도메인을 선택해주세요.');
      return;
    }
    if (formData.emailDomain === 'custom' && (!formData.customEmailDomain || formData.customEmailDomain.trim().length === 0)) {
      setError('이메일 도메인을 입력해주세요.');
      return;
    }

    // 최종 이메일 조합
    const finalEmail = formData.emailDomain === 'custom'
      ? `${formData.emailId}@${formData.customEmailDomain}`
      : `${formData.emailId}@${formData.emailDomain}`;

    // 닉네임 필수 검증
    if (!formData.nickname || formData.nickname.trim().length === 0) {
      setError('닉네임은 필수입니다.');
      return;
    }

    // 닉네임 중복 검사 확인
    if (nicknameCheck.available === null) {
      setError('닉네임 중복 검사를 먼저 해주세요.');
      return;
    }

    if (!nicknameCheck.available) {
      setError('사용할 수 없는 닉네임입니다.');
      return;
    }

    // 이메일 인증 안했으면 경고
    if (!emailVerified) {
      const restrictedFeatures = [
        '게시글 수정/삭제',
        '댓글 수정/삭제',
        '펫케어 서비스 이용',
        '모임 생성/참여',
        '리뷰 작성',
        '실종 제보 작성'
      ];

      const confirmMessage = `이메일 인증을 하지 않으면 다음 기능들이 제한됩니다:\n\n${restrictedFeatures.join('\n')}\n\n그래도 회원가입을 진행하시겠습니까?`;

      const proceed = window.confirm(confirmMessage);

      if (!proceed) {
        // 이메일 인증 안내
        setError('이메일 인증을 완료한 후 회원가입을 진행해주세요. 이메일 인증 버튼을 클릭하여 인증 메일을 받아주세요.');
        return;
      }
    }

    // 반려동물 필수 필드 검증
    for (let i = 0; i < pets.length; i++) {
      const pet = pets[i];
      if (!pet.petName || pet.petName.trim().length === 0) {
        setError(`반려동물 ${i + 1}의 이름을 입력해주세요.`);
        return;
      }
      if (!pet.petType) {
        setError(`반려동물 ${i + 1}의 종류를 선택해주세요.`);
        return;
      }
      if (pet.petType === 'ETC' && (!pet.customPetType || pet.customPetType.trim().length === 0)) {
        setError(`반려동물 ${i + 1}의 종류를 입력해주세요.`);
        return;
      }
    }

    // 반려동물 정보를 petInfo로 변환 (간단한 요약)
    const petInfoText = pets.length > 0
      ? pets.map(pet => {
        const typeMap = {
          'DOG': '강아지',
          'CAT': '고양이',
          'BIRD': '새',
          'RABBIT': '토끼',
          'HAMSTER': '햄스터',
          'ETC': '기타'
        };
        return `${pet.petName}(${typeMap[pet.petType] || '기타'})${pet.breed ? ` - ${pet.breed}` : ''}`;
      }).join(', ')
      : '';

    const submitData = {
      ...formData,
      email: finalEmail, // 조합된 최종 이메일
      petInfo: petInfoText
    };

    setLoading(true);
    setError('');
    setSuccess('');

    try {
      const response = await register(submitData);

      // 회원가입 성공 메시지
      if (emailVerified) {
        setSuccess('회원가입 성공! 이메일 인증이 완료되어 모든 기능을 이용하실 수 있습니다. 로그인해주세요.');
      } else {
        setSuccess('회원가입 성공! 입력하신 이메일로 인증 메일이 발송되었습니다. 이메일 인증을 완료하면 더 많은 기능을 이용할 수 있습니다.');
      }

      // 회원가입 성공 시 콜백 호출
      if (onRegisterSuccess) {
        onRegisterSuccess(response.user || response);
      }

    } catch (error) {
      console.error('회원가입 실패:', error);
      setError(error.response?.data?.error || '회원가입 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <FormHeader>
        <FormHeaderLogo>🐾 Petory</FormHeaderLogo>
        <FormTitle>회원가입</FormTitle>
        <FormSubtitle>몇 가지 정보만 입력하면 시작할 수 있어요</FormSubtitle>
      </FormHeader>

      <Form onSubmit={handleSubmit}>
        <InputGroup>
          <Label htmlFor="id">아이디 *</Label>
          <NicknameInputGroup>
            <PillInput
              type="text"
              id="id"
              name="id"
              value={formData.id}
              onChange={handleChange}
              required
              disabled={loading}
              placeholder="아이디를 입력하세요"
            />
            <OutlineButton
              type="button"
              onClick={handleIdCheck}
              disabled={loading || idCheck.checking || !formData.id}
            >
              {idCheck.checking ? '확인 중...' : '중복 확인'}
            </OutlineButton>
          </NicknameInputGroup>
          {idCheck.message && (
            <NicknameMessage available={idCheck.available}>
              {idCheck.message}
            </NicknameMessage>
          )}
        </InputGroup>

        <InputGroup>
          <Label htmlFor="username">이름 *</Label>
          <PillInput
            type="text"
            id="username"
            name="username"
            value={formData.username}
            onChange={handleChange}
            required
            disabled={loading}
          />
        </InputGroup>

        <InputGroup>
          <Label htmlFor="nickname">닉네임 *</Label>
          <NicknameInputGroup>
            <PillInput
              type="text"
              id="nickname"
              name="nickname"
              value={formData.nickname}
              onChange={handleChange}
              required
              disabled={loading}
              placeholder="닉네임을 입력하세요"
              maxLength={50}
            />
            <OutlineButton
              type="button"
              onClick={handleNicknameCheck}
              disabled={loading || nicknameCheck.checking || !formData.nickname}
            >
              {nicknameCheck.checking ? '확인 중...' : '중복 확인'}
            </OutlineButton>
          </NicknameInputGroup>
          {nicknameCheck.message && (
            <NicknameMessage available={nicknameCheck.available}>
              {nicknameCheck.message}
            </NicknameMessage>
          )}
        </InputGroup>

        <InputGroup>
          <Label htmlFor="password">비밀번호 *</Label>
          <PillInput
            type="password"
            id="password"
            name="password"
            value={formData.password}
            onChange={handleChange}
            required
            disabled={loading}
          />
        </InputGroup>

        <InputGroup>
          <Label htmlFor="email">이메일 *</Label>
          <EmailInputGroup>
            <PillInput
              type="text"
              id="emailId"
              name="emailId"
              value={formData.emailId}
              onChange={handleChange}
              placeholder="이메일 아이디"
              required
              disabled={loading}
              style={{ flex: 1, minWidth: 0 }}
            />
            <EmailAt>@</EmailAt>
            <PillSelect
              id="emailDomain"
              name="emailDomain"
              value={formData.emailDomain}
              onChange={handleChange}
              disabled={loading}
              required
              style={{ flex: 1, minWidth: 0 }}
            >
              <option value="gmail.com">gmail.com</option>
              <option value="naver.com">naver.com</option>
              <option value="daum.net">daum.net</option>
              <option value="kakao.com">kakao.com</option>
              <option value="hanmail.net">hanmail.net</option>
              <option value="nate.com">nate.com</option>
              <option value="outlook.com">outlook.com</option>
              <option value="yahoo.com">yahoo.com</option>
              <option value="custom">직접 입력</option>
            </PillSelect>
            {formData.emailDomain === 'custom' && (
              <PillInput
                type="text"
                name="customEmailDomain"
                value={formData.customEmailDomain || ''}
                onChange={(e) => {
                  const customDomain = e.target.value;
                  setFormData(prev => ({
                    ...prev,
                    customEmailDomain: customDomain,
                    email: prev.emailId && customDomain ? `${prev.emailId}@${customDomain}` : ''
                  }));
                }}
                placeholder="도메인 입력 (예: example.com)"
                disabled={loading}
                required
                style={{ flex: 1, minWidth: 0 }}
              />
            )}
          </EmailInputGroup>
          {emailVerificationError && (
            <EmailVerificationErrorMessage>
              {emailVerificationError}
            </EmailVerificationErrorMessage>
          )}
          {emailVerified ? (
            <EmailVerificationStatus verified={true}>
              ✓ 이메일 인증 완료
            </EmailVerificationStatus>
          ) : (
            <>
              <EmailVerificationStatus verified={false}>
                ⚠ 이메일 인증을 완료하면 더 많은 기능을 이용할 수 있습니다
              </EmailVerificationStatus>
              <OutlineButton
                type="button"
                onClick={handleSendVerificationEmail}
                disabled={loading || emailVerificationSending || !formData.emailId || !formData.emailDomain}
              >
                {emailVerificationSending ? '발송 중...' : emailVerificationSent ? '인증 메일 재발송' : '이메일 인증 메일 발송'}
              </OutlineButton>
              {emailVerificationSent && !emailVerified && (
                <EmailVerificationInfo>
                  이메일 인증 메일이 발송되었습니다. 이메일을 확인하여 인증을 완료해주세요.
                  <br />
                  인증 완료 후 자동으로 인증 상태가 업데이트됩니다.
                </EmailVerificationInfo>
              )}
            </>
          )}
        </InputGroup>

        <InputGroup>
          <Label htmlFor="role">역할 *</Label>
          <PillSelect
            id="role"
            name="role"
            value={formData.role}
            onChange={handleChange}
            disabled={loading}
            required
          >
            <option value="USER">🐾 일반 사용자 (펫케어 서비스 이용)</option>
            <option value="SERVICE_PROVIDER">🏥 서비스 제공자 (펫케어 서비스 제공)</option>
          </PillSelect>
        </InputGroup>

        <InputGroup>
          <Label htmlFor="location">지역</Label>
          <PillInput
            type="text"
            id="location"
            name="location"
            value={formData.location}
            onChange={handleChange}
            placeholder="예: 서울시 강남구"
            disabled={loading}
          />
        </InputGroup>

        <InputGroup>
          <Label>반려동물 정보</Label>
          <PetCardsContainer>
            {pets.map((pet, index) => (
              <PetCard key={index}>
                <PetCardHeader>
                  <PetCardTitle>반려동물 {index + 1}</PetCardTitle>
                  <RemovePetButton
                    type="button"
                    onClick={() => handleRemovePet(index)}
                    disabled={loading}
                  >
                    ✕
                  </RemovePetButton>
                </PetCardHeader>
                <PetCardBody>
                  <PetInputRow>
                    <PetInputGroup>
                      <PetLabel>이름 *</PetLabel>
                      <PillInput
                        type="text"
                        value={pet.petName}
                        onChange={(e) => handlePetChange(index, 'petName', e.target.value)}
                        placeholder="반려동물 이름"
                        disabled={loading}
                        maxLength={50}
                        required
                      />
                    </PetInputGroup>
                    <PetInputGroup>
                      <PetLabel>종류 *</PetLabel>
                      <PillSelect
                        value={pet.petType}
                        onChange={(e) => handlePetChange(index, 'petType', e.target.value)}
                        disabled={loading}
                        required
                      >
                        <option value="DOG">강아지</option>
                        <option value="CAT">고양이</option>
                        <option value="BIRD">새</option>
                        <option value="RABBIT">토끼</option>
                        <option value="HAMSTER">햄스터</option>
                        <option value="ETC">기타</option>
                      </PillSelect>
                      {pet.petType === 'ETC' && (
                        <PillInput
                          type="text"
                          value={pet.customPetType}
                          onChange={(e) => handlePetChange(index, 'customPetType', e.target.value)}
                          placeholder="어떤 펫인지 입력해주세요 (예: 햄스터, 토끼 등)"
                          disabled={loading}
                          required
                          style={{ marginTop: '0.5rem' }}
                        />
                      )}
                    </PetInputGroup>
                  </PetInputRow>

                  <PetInputRow>
                    <PetInputGroup>
                      <PetLabel>품종</PetLabel>
                      <PillInput
                        type="text"
                        value={pet.breed}
                        onChange={(e) => handlePetChange(index, 'breed', e.target.value)}
                        placeholder="예: 골든 리트리버"
                        disabled={loading}
                        maxLength={50}
                      />
                    </PetInputGroup>
                    <PetInputGroup>
                      <PetLabel>성별</PetLabel>
                      <PillSelect
                        value={pet.gender}
                        onChange={(e) => handlePetChange(index, 'gender', e.target.value)}
                        disabled={loading}
                      >
                        <option value="UNKNOWN">미확인</option>
                        <option value="M">수컷</option>
                        <option value="F">암컷</option>
                      </PillSelect>
                    </PetInputGroup>
                  </PetInputRow>

                  <PetInputRow>
                    <PetInputGroup>
                      <PetLabel>나이</PetLabel>
                      <PillInput
                        type="text"
                        value={pet.age}
                        onChange={(e) => handlePetChange(index, 'age', e.target.value)}
                        placeholder="예: 3살, 5개월"
                        disabled={loading}
                        maxLength={30}
                      />
                    </PetInputGroup>
                    <PetInputGroup>
                      <PetLabel>색상/털색</PetLabel>
                      <PillInput
                        type="text"
                        value={pet.color}
                        onChange={(e) => handlePetChange(index, 'color', e.target.value)}
                        placeholder="예: 갈색, 흰색"
                        disabled={loading}
                        maxLength={50}
                      />
                    </PetInputGroup>
                  </PetInputRow>

                  <PetInputRow>
                    <PetInputGroup>
                      <PetLabel>몸무게 (kg)</PetLabel>
                      <PillInput
                        type="number"
                        step="0.1"
                        min="0"
                        value={pet.weight}
                        onChange={(e) => handlePetChange(index, 'weight', e.target.value)}
                        placeholder="예: 5.5"
                        disabled={loading}
                      />
                    </PetInputGroup>
                    <PetInputGroup>
                      <PetLabel>생년월일</PetLabel>
                      <PillInput
                        type="date"
                        value={pet.birthDate}
                        onChange={(e) => handlePetChange(index, 'birthDate', e.target.value)}
                        disabled={loading}
                      />
                    </PetInputGroup>
                  </PetInputRow>

                  <PetInputRow>
                    <PetInputGroup>
                      <PetLabel>
                        <PetCheckbox
                          type="checkbox"
                          checked={pet.isNeutered}
                          onChange={(e) => handlePetChange(index, 'isNeutered', e.target.checked)}
                          disabled={loading}
                        />
                        중성화 여부
                      </PetLabel>
                    </PetInputGroup>
                  </PetInputRow>

                  <PetInputRow>
                    <PetInputGroup style={{ flex: '1 1 100%' }}>
                      <PetLabel>건강 정보</PetLabel>
                      <PetTextarea
                        value={pet.healthInfo}
                        onChange={(e) => handlePetChange(index, 'healthInfo', e.target.value)}
                        placeholder="질병, 알레르기, 특이사항 등"
                        disabled={loading}
                        rows={2}
                      />
                    </PetInputGroup>
                  </PetInputRow>

                  <PetInputRow>
                    <PetInputGroup style={{ flex: '1 1 100%' }}>
                      <PetLabel>특이사항</PetLabel>
                      <PetTextarea
                        value={pet.specialNotes}
                        onChange={(e) => handlePetChange(index, 'specialNotes', e.target.value)}
                        placeholder="성격, 주의사항 등"
                        disabled={loading}
                        rows={2}
                      />
                    </PetInputGroup>
                  </PetInputRow>
                </PetCardBody>
              </PetCard>
            ))}
            <AddPetButton
              type="button"
              onClick={handleAddPet}
              disabled={loading}
            >
              + 반려동물 추가
            </AddPetButton>
          </PetCardsContainer>
        </InputGroup>

        {error && <ErrorMessage>{error}</ErrorMessage>}
        {success && <SuccessMessage>{success}</SuccessMessage>}

        <GradientButton type="submit" disabled={loading}>
          {loading ? '회원가입 중...' : '회원가입'}
        </GradientButton>
      </Form>

      <FormSwitchLink>
        이미 계정이 있으신가요?
        <button type="button" onClick={() => { if (onSwitchToLogin) onSwitchToLogin(); }}>
          로그인
        </button>
      </FormSwitchLink>
    </>
  );
};

export default RegisterForm;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.lg};
`;

const InputGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.sm};
`;

const Label = styled.label`
  font-weight: 600;
  font-size: 13px;
  color: ${({ theme }) => theme.colors.text};
`;

const ErrorMessage = styled.div`
  color: ${({ theme }) => theme.colors.error};
  font-size: 13px;
  margin-top: 4px;
`;

const SuccessMessage = styled.div`
  color: ${({ theme }) => theme.colors.success};
  font-size: 13px;
  margin-top: 4px;
`;

const NicknameInputGroup = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.spacing.sm};
  align-items: flex-start;
`;

const NicknameMessage = styled.div`
  font-size: 13px;
  margin-top: 4px;
  color: ${({ theme, available }) => available ? theme.colors.success : theme.colors.error};
`;

const PetCardsContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.lg};
`;

const PetCard = styled.div`
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.xl};
  padding: ${({ theme }) => theme.spacing.lg};
  background: ${({ theme }) => theme.colors.surface};
  transition: all 0.2s ease;

  &:hover {
    border-color: ${({ theme }) => theme.colors.primary};
    box-shadow: ${({ theme }) => theme.shadows.sm};
  }
`;

const PetCardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: ${({ theme }) => theme.spacing.md};
`;

const PetCardTitle = styled.h4`
  margin: 0;
  color: ${({ theme }) => theme.colors.text};
  font-size: 14px;
  font-weight: 600;
`;

const RemovePetButton = styled.button`
  background: ${({ theme }) => theme.colors.error};
  color: #fff;
  border: none;
  border-radius: ${({ theme }) => theme.borderRadius.full};
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    background: ${({ theme }) => theme.colors.errorDark};
    transform: scale(1.1);
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.textLight};
    cursor: not-allowed;
  }
`;

const PetCardBody = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.md};
`;

const PetInputRow = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.spacing.md};

  @media (max-width: 768px) {
    flex-direction: column;
  }
`;

const PetInputGroup = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.xs};
`;

const PetLabel = styled.label`
  font-size: 13px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
`;

const AddPetButton = styled.button`
  width: 100%;
  padding: 14px;
  background: linear-gradient(135deg, #E8714A 0%, #C9573A 100%);
  border: none;
  border-radius: 50px;
  color: white;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: ${({ theme }) => theme.spacing.sm};

  &:hover:not(:disabled) {
    opacity: 0.9;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const PetCheckbox = styled.input`
  margin-right: ${({ theme }) => theme.spacing.sm};
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: ${({ theme }) => theme.colors.primary};

  &:disabled { cursor: not-allowed; }
`;

const PetTextarea = styled.textarea`
  padding: 8px 12px;
  border: 1.5px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.md};
  font-size: 13px;
  font-family: inherit;
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.text};
  resize: vertical;
  transition: border-color 0.2s ease;
  outline: none;

  &:focus {
    border-color: ${({ theme }) => theme.colors.primary};
    box-shadow: 0 0 0 3px ${({ theme }) => theme.colors.primary}20;
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.surfaceSoft};
    cursor: not-allowed;
  }
`;

const EmailInputGroup = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.sm};
  flex-wrap: wrap;
`;

const EmailAt = styled.span`
  font-size: 14px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.textSecondary};
  white-space: nowrap;
`;

const EmailVerificationErrorMessage = styled.div`
  margin-top: ${({ theme }) => theme.spacing.sm};
  padding: ${({ theme }) => theme.spacing.sm};
  font-size: 13px;
  color: ${({ theme }) => theme.colors.error};
  font-weight: 500;
  background: ${({ theme }) => theme.colors.error}10;
  border-left: 3px solid ${({ theme }) => theme.colors.error};
  border-radius: ${({ theme }) => theme.borderRadius.sm};
`;

const EmailVerificationStatus = styled.div`
  margin-top: ${({ theme }) => theme.spacing.sm};
  padding: ${({ theme }) => theme.spacing.sm};
  font-size: 13px;
  color: ${({ theme, verified }) => verified ? theme.colors.success : theme.colors.warning};
  font-weight: ${({ verified }) => verified ? '600' : '500'};
`;

const EmailVerificationInfo = styled.div`
  margin-top: ${({ theme }) => theme.spacing.sm};
  padding: ${({ theme }) => theme.spacing.md};
  background: ${({ theme }) => theme.colors.info}15;
  border-left: 3px solid ${({ theme }) => theme.colors.info};
  border-radius: ${({ theme }) => theme.borderRadius.sm};
  font-size: 13px;
  color: ${({ theme }) => theme.colors.infoDark};
  line-height: 1.5;
`;
