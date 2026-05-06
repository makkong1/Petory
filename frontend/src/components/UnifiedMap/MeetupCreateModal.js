import React, { useState, useEffect, useRef } from "react";
import styled from "styled-components";
import { meetupApi } from "../../api/meetupApi";
import { geocodingApi } from "../../api/geocodingApi";
import { useEmailVerification } from "../../hooks/useEmailVerification";
import MiniMapPicker from "./MiniMapPicker";

const defaultForm = () => ({
  title: "",
  description: "",
  location: "",
  latitude: null,
  longitude: null,
  date: "",
  maxParticipants: 10,
});

const MeetupCreateModal = ({ onClose, onSuccess }) => {
  const { checkAndRedirect, EmailVerificationPromptComponent } =
    useEmailVerification("MEETUP");
  const [formData, setFormData] = useState(defaultForm());
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [locationQuery, setLocationQuery] = useState("");
  const [locationResults, setLocationResults] = useState([]);
  const [showResults, setShowResults] = useState(false);
  const [locationSearching, setLocationSearching] = useState(false);
  const [showMap, setShowMap] = useState(false);
  const [selectedLocationDetails, setSelectedLocationDetails] = useState(null);
  const locationInputRef = useRef(null);
  const resultsRef = useRef(null);

  // 주소 검색 debounce
  useEffect(() => {
    if (!locationQuery || locationQuery.trim().length < 2) {
      setLocationResults([]);
      setShowResults(false);
      return;
    }
    const timer = setTimeout(async () => {
      setLocationSearching(true);
      try {
        const data = await geocodingApi.searchPlaces(locationQuery);
        if (data && data.success && data.results && data.results.length > 0) {
          setLocationResults(data.results);
          setShowResults(true);
        } else {
          setLocationResults([]);
          setShowResults(false);
        }
      } catch {
        setLocationResults([]);
      } finally {
        setLocationSearching(false);
      }
    }, 500);
    return () => clearTimeout(timer);
  }, [locationQuery]);

  // 외부 클릭 시 결과 닫기
  useEffect(() => {
    const handler = (e) => {
      if (
        locationInputRef.current &&
        !locationInputRef.current.contains(e.target) &&
        resultsRef.current &&
        !resultsRef.current.contains(e.target)
      ) {
        setShowResults(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const handleLocationSelect = (result) => {
    setFormData((prev) => ({
      ...prev,
      location: result.address,
      latitude: result.latitude,
      longitude: result.longitude,
    }));
    setLocationQuery(result.address);
    setSelectedLocationDetails({
      address: result.address,
      roadAddress: result.roadAddress || result.address,
      jibunAddress: result.jibunAddress || "",
    });
    setShowResults(false);
    setErrors((prev) => {
      const e = { ...prev };
      delete e.location;
      return e;
    });
  };

  // 지도 클릭으로 장소 선택
  const handleMapSelect = (lat, lng, address, details) => {
    setFormData((prev) => ({
      ...prev,
      location: address || prev.location,
      latitude: lat,
      longitude: lng,
    }));
    if (address) setLocationQuery(address);
    setSelectedLocationDetails(
      details
        ? {
            address: details.address || address || "",
            roadAddress:
              details.roadAddress || details.address || address || "",
            jibunAddress: details.jibunAddress || "",
          }
        : null
    );
    setErrors((prev) => {
      const e = { ...prev };
      delete e.location;
      return e;
    });
  };

  const handleLocationInputChange = (e) => {
    const value = e.target.value;
    setLocationQuery(value);

    setFormData((prev) => {
      if (!prev.location || value === prev.location) {
        return prev;
      }
      return {
        ...prev,
        location: "",
        latitude: null,
        longitude: null,
      };
    });
    setSelectedLocationDetails(null);

    if (errors.location) {
      setErrors((prev) => {
        const next = { ...prev };
        delete next.location;
        return next;
      });
    }
  };

  const clearSelectedLocation = () => {
    setFormData((prev) => ({
      ...prev,
      location: "",
      latitude: null,
      longitude: null,
    }));
    setLocationQuery("");
    setSelectedLocationDetails(null);
    setLocationResults([]);
    setShowResults(false);
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: name === "maxParticipants" ? Number(value) : value,
    }));
    if (errors[name])
      setErrors((prev) => {
        const e = { ...prev };
        delete e[name];
        return e;
      });
  };

  const validate = () => {
    const e = {};
    if (!formData.title.trim()) e.title = "제목을 입력해주세요.";
    if (!formData.location.trim()) e.location = "장소를 입력해주세요.";
    if (!formData.latitude || !formData.longitude)
      e.location = "검색 결과를 누르거나 지도에서 위치를 찍어주세요.";
    if (!formData.date) e.date = "날짜/시간을 입력해주세요.";
    else if (new Date(formData.date) < new Date())
      e.date = "현재 이후 시간을 입력해주세요.";
    if (!formData.maxParticipants || formData.maxParticipants < 1)
      e.maxParticipants = "1명 이상이어야 합니다.";
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!checkAndRedirect()) return;
    if (!validate()) return;
    setLoading(true);
    try {
      await meetupApi.createMeetup({
        title: formData.title,
        description: formData.description,
        location: formData.location,
        latitude: formData.latitude,
        longitude: formData.longitude,
        date: formData.date,
        maxParticipants: formData.maxParticipants,
      });
      onSuccess?.();
      onClose();
    } catch (err) {
      alert(err.response?.data?.error || "모임 등록에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Overlay
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <EmailVerificationPromptComponent />
      <Modal>
        <ModalHeader>
          <ModalTitle>🐾 모임 만들기</ModalTitle>
          <CloseBtn onClick={onClose}>✕</CloseBtn>
        </ModalHeader>

        <Form onSubmit={handleSubmit}>
          <Field>
            <Label>제목 *</Label>
            <Input
              name="title"
              value={formData.title}
              onChange={handleChange}
              placeholder="모임 이름을 입력하세요"
            />
            {errors.title && <ErrorMsg>{errors.title}</ErrorMsg>}
          </Field>

          <Field>
            <LocationLabelRow>
              <Label>장소 *</Label>
              <MapToggleBtn type="button" onClick={() => setShowMap((v) => !v)}>
                {showMap ? "지도 숨기기" : "지도 보기"}
              </MapToggleBtn>
            </LocationLabelRow>
            <LocationInputWrapper>
              <Input
                ref={locationInputRef}
                value={locationQuery}
                onChange={handleLocationInputChange}
                placeholder="모임 장소를 검색하세요"
                autoComplete="off"
              />
              {locationSearching && <SearchingHint>검색 중...</SearchingHint>}
              {showResults && locationResults.length > 0 && (
                <ResultsList ref={resultsRef}>
                  {locationResults.map((r, i) => (
                    <ResultItem key={i} onClick={() => handleLocationSelect(r)}>
                      <ResultAddress>{r.address}</ResultAddress>
                      {r.jibunAddress && r.jibunAddress !== r.address && (
                        <ResultSub>{r.jibunAddress}</ResultSub>
                      )}
                    </ResultItem>
                  ))}
                </ResultsList>
              )}
              {showResults &&
                !locationSearching &&
                locationQuery.trim().length >= 2 &&
                locationResults.length === 0 && (
                  <ResultsList ref={resultsRef}>
                    <EmptyResultItem>
                      검색 결과가 없습니다. 지도로 직접 선택해보세요.
                    </EmptyResultItem>
                  </ResultsList>
                )}
            </LocationInputWrapper>
            <LocationGuide>
              검색 결과를 눌러 위치를 확정하거나, 필요하면 지도에서 직접
              찍어주세요.
            </LocationGuide>
            {formData.location && formData.latitude && formData.longitude && (
              <SelectedLocationCard>
                <SelectedLocationLabel>선택된 장소</SelectedLocationLabel>
                <SelectedLocationText>
                  {selectedLocationDetails?.roadAddress || formData.location}
                </SelectedLocationText>
                {selectedLocationDetails?.jibunAddress &&
                  selectedLocationDetails.jibunAddress !==
                    (selectedLocationDetails?.roadAddress ||
                      formData.location) && (
                    <SelectedLocationSub>
                      {selectedLocationDetails.jibunAddress}
                    </SelectedLocationSub>
                  )}
                <ClearLocationButton
                  type="button"
                  onClick={clearSelectedLocation}
                >
                  다시 선택
                </ClearLocationButton>
              </SelectedLocationCard>
            )}
            {errors.location && <ErrorMsg>{errors.location}</ErrorMsg>}
            {showMap && (
              <MiniMapPicker
                lat={formData.latitude}
                lng={formData.longitude}
                selectedLabel={
                  selectedLocationDetails?.roadAddress || formData.location
                }
                onSelect={handleMapSelect}
              />
            )}
          </Field>

          <Field>
            <Label>날짜 / 시간 *</Label>
            <Input
              type="datetime-local"
              name="date"
              value={formData.date}
              onChange={handleChange}
              min={new Date().toISOString().slice(0, 16)}
            />
            {errors.date && <ErrorMsg>{errors.date}</ErrorMsg>}
          </Field>

          <Field>
            <Label>최대 인원 *</Label>
            <Input
              type="number"
              name="maxParticipants"
              value={formData.maxParticipants}
              onChange={handleChange}
              min="1"
              max="100"
            />
            {errors.maxParticipants && (
              <ErrorMsg>{errors.maxParticipants}</ErrorMsg>
            )}
          </Field>

          <Field>
            <Label>설명</Label>
            <Textarea
              name="description"
              value={formData.description}
              onChange={handleChange}
              placeholder="모임 소개, 준비물, 주의사항 등을 입력하세요"
              rows={4}
            />
          </Field>

          <ButtonRow>
            <CancelButton type="button" onClick={onClose}>
              취소
            </CancelButton>
            <SubmitButton type="submit" disabled={loading}>
              {loading ? "등록 중..." : "모임 만들기"}
            </SubmitButton>
          </ButtonRow>
        </Form>
      </Modal>
    </Overlay>
  );
};

export default MeetupCreateModal;

const Overlay = styled.div`
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 16px;
`;

const Modal = styled.div`
  background: ${(props) => props.theme.colors.surface};
  border-radius: 16px;
  width: 100%;
  max-width: 480px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.2);
`;

const ModalHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 20px 12px;
  border-bottom: 1px solid ${(props) => props.theme.colors.border};
  position: sticky;
  top: 0;
  background: ${(props) => props.theme.colors.surface};
  z-index: 1;
`;

const ModalTitle = styled.h2`
  font-size: 17px;
  font-weight: 700;
  color: ${(props) => props.theme.colors.text};
  margin: 0;
`;

const CloseBtn = styled.button`
  background: none;
  border: none;
  font-size: 16px;
  color: ${(props) => props.theme.colors.textSecondary};
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  &:hover {
    background: ${(props) => props.theme.colors.surfaceHover};
  }
`;

const Form = styled.form`
  padding: 16px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
`;

const Field = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const Label = styled.label`
  font-size: 13px;
  font-weight: 600;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const Input = styled.input`
  padding: 9px 12px;
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: 8px;
  background: ${(props) => props.theme.colors.background};
  color: ${(props) => props.theme.colors.text};
  font-size: 14px;
  outline: none;
  width: 100%;
  box-sizing: border-box;

  &:focus {
    border-color: ${(props) => props.theme.colors.primary};
  }
`;

const Textarea = styled.textarea`
  padding: 9px 12px;
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: 8px;
  background: ${(props) => props.theme.colors.background};
  color: ${(props) => props.theme.colors.text};
  font-size: 14px;
  outline: none;
  resize: none;
  line-height: 1.5;

  &:focus {
    border-color: ${(props) => props.theme.colors.primary};
  }
`;

const LocationInputWrapper = styled.div`
  position: relative;
`;

const SearchingHint = styled.div`
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 12px;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ResultsList = styled.div`
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: ${(props) => props.theme.colors.surface};
  border: 1px solid ${(props) => props.theme.colors.border};
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  z-index: 100;
  overflow: hidden;
`;

const ResultItem = styled.div`
  padding: 10px 12px;
  cursor: pointer;
  color: ${(props) => props.theme.colors.text};

  &:hover {
    background: ${(props) => props.theme.colors.surfaceHover};
  }
  & + & {
    border-top: 1px solid
      ${(props) => props.theme.colors.borderLight || props.theme.colors.border};
  }
`;

const ResultAddress = styled.div`
  font-size: 13px;
`;

const ResultSub = styled.div`
  font-size: 11px;
  color: ${(props) => props.theme.colors.textSecondary};
  margin-top: 2px;
`;

const EmptyResultItem = styled.div`
  padding: 10px 12px;
  font-size: 12px;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const LocationLabelRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const MapToggleBtn = styled.button`
  background: none;
  border: none;
  font-size: 12px;
  color: ${(props) => props.theme.colors.primary};
  cursor: pointer;
  padding: 0;
  text-decoration: underline;
`;

const LocationGuide = styled.div`
  font-size: 12px;
  color: ${(props) => props.theme.colors.textSecondary};
  line-height: 1.4;
`;

const SelectedLocationCard = styled.div`
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) =>
    props.theme.colors.surfaceSoft || props.theme.colors.background};
`;

const SelectedLocationLabel = styled.div`
  font-size: 11px;
  font-weight: 700;
  color: ${(props) => props.theme.colors.primary};
`;

const SelectedLocationText = styled.div`
  font-size: 13px;
  color: ${(props) => props.theme.colors.text};
  line-height: 1.5;
`;

const SelectedLocationSub = styled.div`
  font-size: 12px;
  color: ${(props) => props.theme.colors.textSecondary};
  line-height: 1.4;
`;

const ClearLocationButton = styled.button`
  align-self: flex-start;
  margin-top: 2px;
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  color: ${(props) => props.theme.colors.primary};
  cursor: pointer;
  text-decoration: underline;
`;

const ErrorMsg = styled.span`
  font-size: 12px;
  color: ${(props) => props.theme.colors.error || "#ef4444"};
`;

const ButtonRow = styled.div`
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 4px;
`;

const CancelButton = styled.button`
  padding: 9px 18px;
  border-radius: 8px;
  border: 1px solid ${(props) => props.theme.colors.border};
  background: none;
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 14px;
  cursor: pointer;
`;

const SubmitButton = styled.button`
  padding: 9px 20px;
  border-radius: 8px;
  border: none;
  background: ${(props) => props.theme.colors.primary};
  color: white;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
  &:hover:not(:disabled) {
    opacity: 0.9;
  }
`;
