# AI v2 입력 fixture

`analysis.md`, `analysis.txt`, `analysis.pdf.b64`는 테스트와 계약 확인용 합성 입력이다. PDF는 저장소에서 바이너리를 만들지 않도록 base64로 관리하며, 실행 시 요청 경계에서만 디코딩한다. 운영 서비스는 이 fixture를 사용하지 않고 Core가 전달한 transient 입력만 처리한다.
