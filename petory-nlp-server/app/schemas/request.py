from pydantic import BaseModel
from typing import Optional
from enum import Enum

class PetType(str, Enum):
    DOG = "DOG"
    CAT = "CAT"
    OTHER = "OTHER"

class PetIntentAnalyzeRequest(BaseModel):
    text: str
    petType: Optional[PetType] = None
