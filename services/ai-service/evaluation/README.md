# Evaluation runner

이 디렉터리는 실제 기획서 5~10개를 같은 조건으로 비교하기 위한 오프라인 평가 도구입니다.

## 결과 파일 형식

`results/{documentId}-{pipeline}.json`에 원문 PDF나 API 키를 넣지 말고, 분석 결과와 사용량 메타데이터만 저장합니다.

```json
{
  "documentId": "doc-001",
  "pipeline": "pypdf",
  "pageCount": 8,
  "latencyMs": 1200,
  "totalCost": 0.08,
  "analysis": {
    "profile": {"backend": ["Spring Boot"]},
    "candidates": [{"field": "backend", "proposedValue": ["Spring Boot"], "evidence": [{"page": 2}]}]
  }
}
```

## 실행

```powershell
& .venv\Scripts\python.exe evaluation\run_evaluation.py `
  --gold-dir evaluation\gold `
  --results-dir evaluation\results `
  --output evaluation\reports\latest
```

생성물은 `latest.json`과 `latest.md`이며, 정확도·누락률·허위 추출률·quote 위치 성공률·평균 지연시간·총 비용을 pipeline별로 비교합니다.
