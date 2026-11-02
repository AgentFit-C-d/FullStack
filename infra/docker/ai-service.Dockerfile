FROM python:3.11-slim

WORKDIR /app
COPY services/ai-service/ /app/
RUN pip install --no-cache-dir .

EXPOSE 8001
CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8001}"]
