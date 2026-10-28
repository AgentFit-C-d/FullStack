# 기획서 업로드 프론트 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 로그인된 사용자가 프로젝트를 선택하거나 생성한 뒤 PDF/Markdown 기획서를 업로드하고 업로드된 입력 메타데이터를 확인할 수 있게 한다.

**Architecture:** `apps/web`에 React + Vite 앱을 만들고, API 호출을 작은 클라이언트 모듈로 분리한다. 화면은 세션 확인 → 프로젝트 선택/생성 → 파일 또는 텍스트 입력 → 업로드 결과 표시의 단계형 흐름으로 구성한다. 분석 시작과 결과 화면은 다음 작업으로 남긴다.

**Tech Stack:** React, Vite, TypeScript, browser `fetch`, Spring Core API multipart endpoints.

**Spec:** `docs/superpowers/specs/2026-10-27-analysis-review-frontend-design.md`

## Global Constraints

- 기획 입력은 파일과 텍스트 중 정확히 하나만 보낸다.
- 파일은 PDF 또는 Markdown만 허용하고 최대 10 MiB로 제한한다.
- 업로드 요청에는 프로젝트의 현재 `expectedVersion`을 multipart part로 포함한다.
- 세션 쿠키를 위해 API 요청에 `credentials: 'include'`를 사용한다.
- 추천 기능은 구현하지 않는다.

## Review Focus

- 비로그인 세션: 업로드 화면 대신 로그인 안내를 보여준다.
- 프로젝트가 없는 상태: 프로젝트 생성 후 목록을 갱신한다.
- 파일과 텍스트 동시 입력: 업로드 버튼을 막고 하나만 선택하게 한다.
- 잘못된 확장자/10 MiB 초과 파일: 브라우저에서 업로드 전에 차단한다.
- 버전 충돌 또는 분석 중 입력 교체 실패: API 오류를 사용자 메시지로 표시한다.

---

### Task 1: Web 앱 기본 구조와 API 클라이언트

**Files:**
- Create: `apps/web/package.json`
- Create: `apps/web/tsconfig.json`
- Create: `apps/web/vite.config.ts`
- Create: `apps/web/index.html`
- Create: `apps/web/src/main.tsx`
- Create: `apps/web/src/api/client.ts`
- Test: `apps/web/src/api/client.test.ts`

**Interfaces:**
- Produces `apiFetch<T>(path: string, init?: RequestInit): Promise<T>` with JSON error handling and `credentials: 'include'`.

- [ ] **Step 1: Write the failing API client test**

`apiFetch`가 credentials를 포함하고, 2xx가 아닌 응답에서 서버 메시지를 포함한 오류를 던지는지 검증한다.

- [ ] **Step 2: Run the test and verify it fails**

Run: `npm test -- --run src/api/client.test.ts`
Expected: FAIL because the web app and client do not exist.

- [ ] **Step 3: Implement the Vite app shell and `apiFetch`**

API 기본 주소는 `VITE_API_BASE_URL`이 있으면 사용하고 없으면 빈 문자열을 사용한다.

- [ ] **Step 4: Run the test and verify it passes**

Run: `npm test -- --run src/api/client.test.ts`
Expected: PASS.

### Task 2: Session and project/input API contracts

**Files:**
- Create: `apps/web/src/api/agentfitApi.ts`
- Test: `apps/web/src/api/agentfitApi.test.ts`

**Interfaces:**
- `getSession(): Promise<Session | null>`
- `listProjects(): Promise<Project[]>`
- `createProject(name: string): Promise<Project>`
- `uploadPlanningInput(projectId: string, expectedVersion: number, file?: File, inputText?: string): Promise<PlanningInputMetadata>`

- [ ] **Step 1: Write failing contract tests**

Assert that project creation uses `POST /api/projects`, and upload uses `PUT /api/projects/{id}/planning-input` with `expectedVersion` plus exactly one of `file` or `inputText`.

- [ ] **Step 2: Run tests and verify they fail**

Run: `npm test -- --run src/api/agentfitApi.test.ts`
Expected: FAIL because API functions do not exist.

- [ ] **Step 3: Implement the API functions**

Map the current Spring response shapes without exposing raw content, owner ID, or credentials to the UI.

- [ ] **Step 4: Run tests and verify they pass**

Run: `npm test -- --run src/api/agentfitApi.test.ts`
Expected: PASS.

### Task 3: Upload screen

**Files:**
- Create: `apps/web/src/App.tsx`
- Create: `apps/web/src/styles.css`
- Test: `apps/web/src/App.test.tsx`

**Interfaces:**
- Renders session gate, project selector/creator, file/text input toggle, upload action, and uploaded metadata.

- [ ] **Step 1: Write failing UI tests**

Cover unauthenticated login CTA, project creation, invalid extension rejection, file upload with current project version, and successful metadata display.

- [ ] **Step 2: Run tests and verify they fail**

Run: `npm test -- --run src/App.test.tsx`
Expected: FAIL because the screen is not implemented.

- [ ] **Step 3: Implement the upload flow**

Keep the file/text mode mutually exclusive, disable upload while pending, and show server errors in a visible status region.

- [ ] **Step 4: Run tests and verify they pass**

Run: `npm test -- --run src/App.test.tsx`
Expected: PASS.

### Task 4: Browser verification

**Files:**
- Modify: `apps/web/README.md`

- [ ] **Step 1: Run the production build**

Run: `npm run build`
Expected: Vite build completes without TypeScript errors.

- [ ] **Step 2: Document local API configuration**

Document `VITE_API_BASE_URL` and the expected Spring API origin.

- [ ] **Step 3: Commit the upload slice**

Use commit message: `feat(web): add planning input upload flow`.
