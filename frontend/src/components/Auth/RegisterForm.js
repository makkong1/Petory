import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { useAuth } from '../../contexts/AuthContext';
import { userProfileApi } from '../../api/userApi';

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

    setEmailVerificationSending(true);
    setError('');
    setSuccess('');

    try {
      await userProfileApi.sendPreRegistrationVerificationEmail(finalEmail);
      setEmailVerificationSent(true);
      setSuccess('이메일 인증 메일이 발송되었습니다. 이메일을 확인해주세요.');
    } catch (error) {
      console.error('이메일 인증 메일 발송 실패:', error);
      setError(error.response?.data?.message || '이메일 발송에 실패했습니다.');
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
    <RegisterContainer>
      <Title>회원가입</Title>

      <Form onSubmit={handleSubmit}>
        <InputGroup>
          <Label htmlFor="id">아이디 *</Label>
          <NicknameInputGroup>
            <Input
              type="text"
              id="id"
              name="id"
              value={formData.id}
              onChange={handleChange}
              required
              disabled={loading}
              placeholder="아이디를 입력하세요"
            />
            <CheckButton
              type="button"
              onClick={handleIdCheck}
              disabled={loading || idCheck.checking || !formData.id}
            >
              {idCheck.checking ? '확인 중...' : '중복 확인'}
            </CheckButton>
          </NicknameInputGroup>
          {idCheck.message && (
            <NicknameMessage available={idCheck.available}>
              {idCheck.message}
            </NicknameMessage>
          )}
        </InputGroup>

        <InputGroup>
          <Label htmlFor="username">이름 *</Label>
          <Input
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
            <Input
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
            <CheckButton
              type="button"
              onClick={handleNicknameCheck}
              disabled={loading || nicknameCheck.checking || !formData.nickname}
            >
              {nicknameCheck.checking ? '확인 중...' : '중복 확인'}
            </CheckButton>
          </NicknameInputGroup>
          {nicknameCheck.message && (
            <NicknameMessage available={nicknameCheck.available}>
              {nicknameCheck.message}
            </NicknameMessage>
          )}
        </InputGroup>

        <InputGroup>
          <Label htmlFor="password">비밀번호 *</Label>
          <Input
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
            <EmailIdInput
              type="text"
              id="emailId"
              name="emailId"
              value={formData.emailId}
              onChange={handleChange}
              placeholder="이메일 아이디"
              required
              disabled={loading}
            />
            <EmailAt>@</EmailAt>
            <EmailDomainSelect
              id="emailDomain"
              name="emailDomain"
              value={formData.emailDomain}
              onChange={handleChange}
              disabled={loading}
              required
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
            </EmailDomainSelect>
            {formData.emailDomain === 'custom' && (
              <EmailCustomInput
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
              />
            )}
          </EmailInputGroup>
          {emailVerified ? (
            <EmailVerificationStatus verified={true}>
              ✓ 이메일 인증 완료
            </EmailVerificationStatus>
          ) : (
            <>
              <EmailVerificationStatus verified={false}>
                ⚠ 이메일 인증을 완료하면 더 많은 기능을 이용할 수 있습니다
              </EmailVerificationStatus>
              <EmailVerificationButton
                type="button"
                onClick={handleSendVerificationEmail}
                disabled={loading || emailVerificationSending || !formData.emailId || !formData.emailDomain}
              >
                {emailVerificationSending ? '발송 중...' : emailVerificationSent ? '인증 메일 재발송' : '이메일 인증 메일 발송'}
              </EmailVerificationButton>
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
          <Select
            id="role"
            name="role"
            value={formData.role}
            onChange={handleChange}
            disabled={loading}
            required
          >
            <option value="USER">🐾 일반 사용자 (펫케어 서비스 이용)</option>
            <option value="SERVICE_PROVIDER">🏥 서비스 제공자 (펫케어 서비스 제공)</option>
          </Select>
        </InputGroup>

        <InputGroup>
          <Label htmlFor="location">지역</Label>
          <Input
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
                      <PetInput
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
                      <PetSelect
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
                      </PetSelect>
                      {pet.petType === 'ETC' && (
                        <PetInput
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
                      <PetInput
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
                      <PetSelect
                        value={pet.gender}
                        onChange={(e) => handlePetChange(index, 'gender', e.target.value)}
                        disabled={loading}
                      >
                        <option value="UNKNOWN">미확인</option>
                        <option value="M">수컷</option>
                        <option value="F">암컷</option>
                      </PetSelect>
                    </PetInputGroup>
                  </PetInputRow>

                  <PetInputRow>
                    <PetInputGroup>
                      <PetLabel>나이</PetLabel>
                      <PetInput
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
                      <PetInput
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
                      <PetInput
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
                      <PetInput
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

        <Button type="submit" disabled={loading}>
          {loading ? '회원가입 중...' : '회원가입'}
        </Button>
      </Form>

      <LinkText>
        이미 계정이 있으신가요?{' '}
        <a href="#" onClick={(e) => {
          e.preventDefault();
          if (onSwitchToLogin) onSwitchToLogin();
        }}>
          로그인
        </a>
      </LinkText>
    </RegisterContainer>
  );
};

export default RegisterForm;

const RegisterContainer = styled.div`
  max-width: 600px;
  width: 100%;
  margin: 0 auto;
  padding: 2.5rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  
  @media (max-width: 768px) {
    max-width: 90%;
    padding: 2rem;
  }
`;

const Title = styled.h2`
  text-align: center;
  margin-bottom: 2rem;
  color: #333;
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 1rem;
`;

const InputGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
`;

const Label = styled.label`
  font-weight: 500;
  color: #555;
`;

const Input = styled.input`
  padding: 0.75rem;
  border: 2px solid #e1e5e9;
  border-radius: 6px;
  font-size: 0.95rem;
  transition: all 0.2s ease;
  
  &:focus {
    outline: none;
    border-color: #007bff;
    box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.1);
    transform: translateY(-1px);
  }
  
  &:hover {
    border-color: #007bff;
  }
`;

const Select = styled.select`
  padding: 0.75rem;
  border: 2px solid #e1e5e9;
  border-radius: 6px;
  font-size: 0.95rem;
  background: white;
  transition: all 0.2s ease;
  
  &:focus {
    outline: none;
    border-color: #007bff;
    box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.1);
    transform: translateY(-1px);
  }
  
  &:hover {
    border-color: #007bff;
  }
`;

const Button = styled.button`
  padding: 0.75rem 1.5rem;
  background: #28a745;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  
  &:hover {
    background: #218838;
    transform: translateY(-1px);
    box-shadow: 0 3px 8px rgba(40, 167, 69, 0.3);
  }
  
  &:disabled {
    background: #6c757d;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
  }
`;

const ErrorMessage = styled.div`
  color: #dc3545;
  font-size: 0.875rem;
  margin-top: 0.5rem;
`;

const SuccessMessage = styled.div`
  color: #28a745;
  font-size: 0.875rem;
  margin-top: 0.5rem;
`;

const LinkText = styled.p`
  text-align: center;
  margin-top: 1rem;
  color: #666;
  
  a {
    color: #007bff;
    text-decoration: none;
    
    &:hover {
      text-decoration: underline;
    }
  }
`;

const NicknameInputGroup = styled.div`
  display: flex;
  gap: 0.5rem;
  align-items: flex-start;
`;

const CheckButton = styled.button`
  padding: 0.75rem 1rem;
  background: #007bff;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
  
  &:hover:not(:disabled) {
    background: #0056b3;
    transform: translateY(-1px);
  }
  
  &:disabled {
    background: #6c757d;
    cursor: not-allowed;
    transform: none;
  }
`;

const NicknameMessage = styled.div`
  font-size: 0.875rem;
  margin-top: 0.25rem;
  color: ${props => props.available ? '#28a745' : '#dc3545'};
`;

const PetCardsContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1rem;
`;

const PetCard = styled.div`
  border: 2px solid #e1e5e9;
  border-radius: 8px;
  padding: 1rem;
  background: #f8f9fa;
  transition: all 0.2s ease;
  
  &:hover {
    border-color: #007bff;
    box-shadow: 0 2px 8px rgba(0, 123, 255, 0.1);
  }
`;

const PetCardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
`;

const PetCardTitle = styled.h4`
  margin: 0;
  color: #333;
  font-size: 0.95rem;
  font-weight: 600;
`;

const RemovePetButton = styled.button`
  background: #dc3545;
  color: white;
  border: none;
  border-radius: 50%;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 1rem;
  line-height: 1;
  transition: all 0.2s ease;
  
  &:hover:not(:disabled) {
    background: #c82333;
    transform: scale(1.1);
  }
  
  &:disabled {
    background: #6c757d;
    cursor: not-allowed;
  }
`;

const PetCardBody = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
`;

const PetInputRow = styled.div`
  display: flex;
  gap: 0.75rem;
  
  @media (max-width: 768px) {
    flex-direction: column;
  }
`;

const PetInputGroup = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
`;

const PetLabel = styled.label`
  font-size: 0.875rem;
  font-weight: 500;
  color: #555;
`;

const PetInput = styled.input`
  padding: 0.5rem;
  border: 1px solid #ced4da;
  border-radius: 4px;
  font-size: 0.9rem;
  transition: all 0.2s ease;
  
  &:focus {
    outline: none;
    border-color: #007bff;
    box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.1);
  }
  
  &:disabled {
    background: #e9ecef;
    cursor: not-allowed;
  }
`;

const PetSelect = styled.select`
  padding: 0.5rem;
  border: 1px solid #ced4da;
  border-radius: 4px;
  font-size: 0.9rem;
  background: white;
  transition: all 0.2s ease;
  
  &:focus {
    outline: none;
    border-color: #007bff;
    box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.1);
  }
  
  &:disabled {
    background: #e9ecef;
    cursor: not-allowed;
  }
`;

const AddPetButton = styled.button`
  padding: 0.75rem;
  background: #f8f9fa;
  border: 2px dashed #ced4da;
  border-radius: 8px;
  color: #495057;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  
  &:hover:not(:disabled) {
    background: #e9ecef;
    border-color: #007bff;
    color: #007bff;
    transform: translateY(-1px);
  }
  
  &:disabled {
    background: #e9ecef;
    cursor: not-allowed;
    opacity: 0.6;
  }
`;

const PetCheckbox = styled.input`
  margin-right: 0.5rem;
  width: 18px;
  height: 18px;
  cursor: pointer;
  
  &:disabled {
    cursor: not-allowed;
  }
`;

const PetTextarea = styled.textarea`
  padding: 0.5rem;
  border: 1px solid #ced4da;
  border-radius: 4px;
  font-size: 0.9rem;
  font-family: inherit;
  resize: vertical;
  transition: all 0.2s ease;
  
  &:focus {
    outline: none;
    border-color: #007bff;
    box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.1);
  }
  
  &:disabled {
    background: #e9ecef;
    cursor: not-allowed;
  }
`;

const EmailInputGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
`;

const EmailIdInput = styled.input`
  flex: 1;
  min-width: 120px;
  padding: 0.75rem;
  border: 2px solid #e1e5e9;
  border-radius: 6px;
  font-size: 0.95rem;
  transition: all 0.2s ease;
  
  &:focus {
    outline: none;
    border-color: #007bff;
    box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.1);
    transform: translateY(-1px);
  }
  
  &:hover {
    border-color: #007bff;
  }
  
  &:disabled {
    background: #f5f5f5;
    cursor: not-allowed;
  }
`;

const EmailAt = styled.span`
  font-size: 1rem;
  font-weight: 600;
  color: #666;
  white-space: nowrap;
`;

const EmailDomainSelect = styled.select`
  flex: 1;
  min-width: 150px;
  padding: 0.75rem;
  border: 2px solid #e1e5e9;
  border-radius: 6px;
  font-size: 0.95rem;
  background: white;
  transition: all 0.2s ease;
  
  &:focus {
    outline: none;
    border-color: #007bff;
    box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.1);
    transform: translateY(-1px);
  }
  
  &:hover {
    border-color: #007bff;
  }
  
  &:disabled {
    background: #f5f5f5;
    cursor: not-allowed;
  }
`;

const EmailVerificationStatus = styled.div`
  margin-top: 0.5rem;
  padding: 0.5rem;
  font-size: 0.875rem;
  color: ${props => props.verified ? '#4caf50' : '#ff9800'};
  font-weight: ${props => props.verified ? '600' : '500'};
`;

const EmailVerificationButton = styled.button`
  margin-top: 0.5rem;
  padding: 0.5rem 1rem;
  background: ${props => props.theme.colors.primary || '#007bff'};
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: ${props => props.disabled ? 'not-allowed' : 'pointer'};
  opacity: ${props => props.disabled ? 0.6 : 1};
  transition: all 0.2s ease;
  
  &:hover:not(:disabled) {
    background: ${props => props.theme.colors.primaryDark || '#0056b3'};
    transform: translateY(-1px);
  }
`;

const EmailVerificationInfo = styled.div`
  margin-top: 0.5rem;
  padding: 0.75rem;
  background: #e3f2fd;
  border-left: 3px solid #2196f3;
  border-radius: 4px;
  font-size: 0.875rem;
  color: #1976d2;
  line-height: 1.5;
`;

const EmailCustomInput = styled.input`
  flex: 1;
  min-width: 150px;
  padding: 0.75rem;
  border: 2px solid #e1e5e9;
  border-radius: 6px;
  font-size: 0.95rem;
  transition: all 0.2s ease;
  
  &:focus {
    outline: none;
    border-color: #007bff;
    box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.1);
    transform: translateY(-1px);
  }
  
  &:hover {
    border-color: #007bff;
  }
  
  &:disabled {
    background: #f5f5f5;
    cursor: not-allowed;
  }
`;
