FROM python:3.9-slim

WORKDIR /app

# kiwipiepy(형태소 분석) 빌드에 필요한 컴파일러
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app app

# 보안: 전용 유저로 실행 (root 금지). sentence-transformers 모델 캐시 경로도 이 유저 소유로.
RUN useradd -r -m -d /home/nlp nlp \
    && mkdir -p /home/nlp/.cache/huggingface \
    && chown -R nlp:nlp /app /home/nlp
ENV HF_HOME=/home/nlp/.cache/huggingface

USER nlp

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
