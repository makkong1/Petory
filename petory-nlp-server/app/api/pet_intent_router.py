from fastapi import APIRouter
from app.schemas.request import PetIntentAnalyzeRequest
from app.schemas.response import PetIntentAnalyzeResponse, IntentDomain, Urgency
from app.nlp.intent_classifier import classify
from app.nlp.tag_extractor import extract_tags
from app.nlp.tokenizer import extract_keywords
from app.rules.category_rules import get_categories, get_suggested_categories
from app.rules.urgency_rules import judge_urgency
from app.config import settings

router = APIRouter(prefix="/api/pet-intent", tags=["pet-intent"])

MEDICAL_SAFETY_MSG = "정확한 진단은 수의사 상담이 필요합니다. 증상이 심하거나 지속된다면 가까운 동물병원에 문의하세요."

@router.post("/analyze", response_model=PetIntentAnalyzeResponse)
def analyze(req: PetIntentAnalyzeRequest):
    intent, domain, confidence = classify(req.text)
    keywords = extract_keywords(req.text)
    intent_tags = extract_tags(req.text, domain)
    urgency = judge_urgency(req.text, domain)

    if confidence < settings.confidence_threshold:
        return PetIntentAnalyzeResponse(
            intentDomain=IntentDomain.UNKNOWN,
            intent="UNKNOWN",
            recommendedCategories=[],
            confidence=round(confidence, 4),
            keywords=keywords,
            intentTags=intent_tags,
            urgency=Urgency.NORMAL,
            message="입력하신 내용으로 적합한 서비스를 찾기 어렵습니다. 카테고리를 직접 선택해 주세요.",
            suggestedCategories=get_suggested_categories(),
        )

    categories = get_categories(domain)
    if domain == "MEDICAL":
        tag_desc = ", ".join(intent_tags[:2]) if intent_tags else "관련"
        message = f"{tag_desc} 불편 표현이 감지되었습니다. {MEDICAL_SAFETY_MSG}"
    else:
        message = f"입력을 바탕으로 {', '.join(categories)} 카테고리를 추천합니다."

    return PetIntentAnalyzeResponse(
        intentDomain=IntentDomain(domain),
        intent=intent,
        recommendedCategories=categories,
        confidence=round(confidence, 4),
        keywords=keywords,
        intentTags=intent_tags,
        urgency=Urgency(urgency),
        message=message,
    )
