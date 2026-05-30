from pydantic import BaseModel
from typing import List, Optional
from enum import Enum

class IntentDomain(str, Enum):
    MEDICAL = "MEDICAL"
    GROOMING = "GROOMING"
    SUPPLIES = "SUPPLIES"
    FOOD_SNACK = "FOOD_SNACK"
    WALK_OUTING = "WALK_OUTING"
    CAFE_DINING = "CAFE_DINING"
    LODGING_TRAVEL = "LODGING_TRAVEL"
    DAYCARE_BOARDING = "DAYCARE_BOARDING"
    CULTURE_SPACE = "CULTURE_SPACE"
    UNKNOWN = "UNKNOWN"

class Urgency(str, Enum):
    HIGH = "HIGH"
    NORMAL = "NORMAL"
    LOW = "LOW"

class PetIntentAnalyzeResponse(BaseModel):
    intentDomain: IntentDomain
    intent: str
    recommendedCategories: List[str]
    confidence: float
    keywords: List[str]
    intentTags: List[str]
    urgency: Urgency
    message: str
    suggestedCategories: Optional[List[str]] = None
