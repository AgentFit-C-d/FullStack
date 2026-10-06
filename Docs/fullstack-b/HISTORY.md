# Full Stack B milestone history

Catalog 근거 키 충돌 차단 (2026-10-07): 서로 다른 권한 매핑 두 개가 같은 문자열 근거 키가 되어 한 검증 출처가 조용히 덮이는 반례를 재현했다. 지원·권한·조합의 근거 키 충돌을 모두 릴리스 오류로 처리한다. `mvn clean verify`에서 112개 발견, 111개 통과, Windows 심볼릭 링크 테스트 1개 건너뜀.

Catalog JSON 후행 데이터 차단 (2026-10-06): 매니페스트·내용 파일·템플릿 인덱스의 유효 JSON 뒤에 두 번째 JSON 객체를 붙여도 읽히는 반례 세 개를 재현했다. 세 파서 모두 후행 토큰을 거부한다. `mvn clean verify`에서 111개 발견, 110개 통과, Windows 심볼릭 링크 테스트 1개 건너뜀.

Catalog 스키마 버전 정수 절단 차단 (2026-10-06): `4294967297`이 `intValue()`에서 `1`이 되어 매니페스트·내용·템플릿 인덱스의 v1 검사에 통과하는 반례 세 개를 재현했다. 세 경계 모두 정확한 JSON int `1`만 허용한다. `mvn clean verify`에서 108개 발견, 107개 통과, Windows 심볼릭 링크 테스트 1개 건너뜀.

Preview 조립 메타데이터 예산 (2026-10-06): Preview 요청의 긴 추천·도구 ID와 기존 파일 대상 키가 Catalog 로딩 전 예산 검사에서 통과하는 반례를 재현했다. 조립 입구에도 재생성 경로와 같은 128/200-code-point 상한을 적용했다. `mvn clean verify`에서 106개 발견, 105개 통과, Windows 심볼릭 링크 테스트 1개 건너뜀.

ZIP 재생성 메타데이터 예산 (2026-10-06): 추천·도구 ID와 파일 대상 키에 길이 제한이 없어 큰 문자열이 지문 해시까지 전달되는 반례를 재현했다. 초안 API의 ID 128 code points, 키 200 code points 한도를 직접 지문·ZIP 경로에 적용했다. `mvn clean verify`에서 105개 발견, 104개 통과, Windows 심볼릭 링크 테스트 1개 건너뜀. HTTP 요청 본문 한도는 아직 연결되지 않았다.

ZIP 재생성 입력 예산 (2026-10-06): 직접 지문·ZIP 경로에서는 100,001자 입력이 비교와 해시까지 진행되는 반례를 재현했다. 이제 선택·정책 수, 생성/기존 파일 수, 파일별 Unicode code points와 목록별 UTF-8 bytes를 먼저 제한한다. `mvn clean verify`에서 104개 발견, 103개 통과, Windows 심볼릭 링크 테스트 1개 건너뜀. HTTP 직렬화 본문 한도는 별도로 필요하다.

접두어 Secret 변수 검사 (2026-10-06): `TEAM_API_KEY` 같은 키와 `export TEAM_TOKEN`, `AWS_SECRET_ACCESS_KEY` 리터럴이 이전에는 Preview를 통과하는 반례를 재현했다. 생성·기존 파일 양쪽에 적용되는 검사 패턴을 확장하고 환경 변수 참조는 유지했다. `mvn clean verify`에서 103개 발견, 102개 통과, Windows 심볼릭 링크 테스트 1개 건너뜀. 완전한 Secret 탐지는 아니다.

권한 매핑 기본값 방어 (2026-10-06): 빈 tool/action 키에도 기본 Ask가 생성되는 반례를 재현하고, 유효하지 않은 매핑과 Ask 없는 Allow 전용 매핑을 차단했다. `PermissionPolicyGateTest`와 `ConfigurationSelectionValidatorTest`의 10개 테스트가 통과했다. 실제 Claude Code 정책 변환·집행 시험은 미수행이다.

다중 도구 출력 누락 차단 (2026-10-06): 합성 Catalog에서 두 도구를 선택하고 한 도구의 템플릿만 인덱싱했을 때 기존 렌더러가 성공하는 반례를 재현했다. 선택한 모든 도구에 검토된 출력이 있어야 하도록 수정했다. `mvn clean verify`에서 102개 발견, 101개 통과, Windows 심볼릭 링크 테스트 1개 건너뜀. 실제 Client 템플릿과 릴리스 승인은 여전히 미완료다.

Preview 대상 일치 검사 (2026-10-06): 요청의 OS/Client/버전이 A가 별도로 제공한 현재 Environment 대상과 다르면 Catalog를 읽기 전 거부한다. 이전 메서드 시그니처로는 새 테스트가 컴파일되지 않는 것을 확인하고 수정했다. `mvn clean verify`에서 101개 발견, 100개 통과, Windows 심볼릭 링크 테스트 1개 건너뜀. 실제 A 조회·인증 연결은 미완료다.

AI 빈 후보 방어 (2026-10-06): `AiCapabilityIntake`가 빈 claim 목록을 질문 유무와 관계없이 거부하도록 테스트를 먼저 추가했다. 추가 테스트의 예상 실패를 확인한 뒤 경계를 보완했다. `mvn clean verify`는 100개 발견, 99개 통과, Windows 심볼릭 링크 권한으로 1개 건너뜀. 9개 전량/부분 응답 의미, AI 전송 형식과 A 연동은 미합의다.

The checked Phase 1 items are domain implementation only. They do not imply a verified real Catalog, production API, database persistence, or completed B feature.

Phase 1 verification: `mvn test` passed with 13 tests (twelve B domain tests and the existing health test) using a local Maven cache and Java 24 to compile for the project's Java 21 target. `javap` confirmed class-file major version 65 (Java 21). Execution on a Java 21 runtime remains untested because this workstation currently has JDK 17 and 24. No live Catalog, PostgreSQL, AI, or external Client checks ran.

Catalog envelope milestone: `mvn package` passed with 18 tests discovered, 17 passed and one Windows symlink test skipped. This step verified file integrity before the subsequent semantic parser was added.

Catalog semantic parsing verification (2026-10-06): the B-owned public load path now checks manifest/file hashes and then parses the six JSON files against the proposed v1 schema. It rejects duplicate JSON keys, missing/unknown references, unreviewed permission mapping, absent evidence, and environment-agnostic combination claims; dependency-only tools may have no direct Capability. `mvn package` passed with 28 tests discovered, 27 passed and the same symlink test skipped. This does not complete B08: the team still needs to approve the schema, provide reviewed real support records, and define target-specific combination verification. No live Catalog or public B API is enabled.

Selection guard milestone (2026-10-06): pure B09a checks selected tools and reviewed permission choices before a future Preview. At this stage multi-tool choices were blocked pending the target-specific model added below. The guard still does not bind choices to A's confirmed snapshot or persist/generate anything.

Targeted combination milestone (2026-10-06): synthetic release tests now exercise exact-target multi-tool recommendation and selection. The production Catalog remains unpopulated; this milestone changes the contract and validation model only. `mvn package` passed with 37 tests discovered, 36 passed, and one Windows symlink test skipped; `git diff --check` passed. Java 21 class-file target (major 65) was confirmed using JDK 24, but a Java 21 runtime is still unavailable locally. See [targeted combinations](catalog/targeted-combinations-plan.md) for the proposed contract and remaining real-release work.

Preview comparison milestone (2026-10-06): an in-memory comparator now validates generated/provided target paths, labels unprovided files as proposals, and computes exact UTF-8 hashes plus transient unified diffs. `mvn package` passed with 45 tests discovered, 44 passed, and one Windows symlink test skipped. Actual reviewed templates, sensitive-input screening, fingerprint/approval/export, and A-owned access/persistence contracts remain open. The comparator has no public API and does not inspect the user's PC.

Preview fingerprint milestone (2026-10-06): a pure B11a calculator now includes A-shaped Project/Profile/confirmation/Review/Developer/Environment versions, Catalog release/hash, selected tools/policies, generated file hashes, and generator version. An independent Python fixture pins canonical SHA-256 output. `mvn package` passed with 50 tests discovered, 49 passed, one Windows symlink test skipped; Java 21 class-file target (major 65) and `git diff --check` passed. A's current snapshot, approval/expiry persistence, and ZIP byte regeneration are not connected yet.

ZIP regeneration milestone (2026-10-06): a pure B11b exporter recomputes Preview fingerprints from transient inputs, refuses changed inputs, and writes only Previewed paths and exact UTF-8 bytes to an in-memory ZIP. `mvn package` passed with 54 tests discovered, 53 passed, one Windows symlink test skipped; Java 21 class-file target (major 65) and `git diff --check` passed. This is not an export API: A-owned approval, expiry, ownership, current-basis, transaction, and audit checks remain unimplemented.

Static template milestone (2026-10-06): `templates/index.json` binds known tool keys to manifest-hashed `templates/` or `guides/` source text and safe output paths. A separately supplied approved Catalog hash must match before any selected static text becomes a Preview candidate. `mvn package` passed with 59 tests discovered, 58 passed, and one Windows symlink test skipped; `git diff --check` passed. The current implementation does not provision that approval, populate real Client templates, validate selection eligibility, or expose a Preview API. Those integration and real-release checks remain open.

Sensitive-content milestone (2026-10-06): generated and explicitly provided file text are screened before the comparator creates Preview content, hashes, or diffs. Recognizable private-key/token formats and credential-like literal assignments fail with a generic error; environment-variable references remain usable. `mvn package` passed with 62 tests discovered, 61 passed, one Windows symlink test skipped; `git diff --check` passed. Pattern screening cannot identify every secret, and there is no HTTP ingestion or logging boundary yet.

Preview assembly milestone (2026-10-06): a B-owned in-memory entry point now chains the approved Catalog hash and basis check, exact environment/permission selection, static template rendering, and Preview fingerprinting. The request has no generated-file field. `mvn package` passed with 66 tests discovered, 65 passed, one Windows symlink test skipped; `git diff --check` passed. This still lacks A's authenticated current snapshot and recommendation ownership/membership check, real Catalog release data, approval persistence, and public API.

Preview budget milestone (2026-10-06): the B-owned assembly path now rejects oversized tool/policy/file collections, files over 100,000 Unicode code points, and input or generated content over 1 MiB UTF-8. An integration test proves oversized Catalog templates cannot become Preview files. `mvn package` passed with 71 tests discovered, 70 passed, one Windows symlink test skipped; `git diff --check` passed. Exact serialized HTTP request-body limits and 413 mapping remain future API work.

Preview freshness milestone (2026-10-06): a pure metadata gate now rejects a changed current basis, expired Preview, and mismatched stored/submitted/regenerated fingerprints. Approval eligibility additionally requires explicit confirmation. `mvn package` passed with 76 tests discovered, 75 passed, one Windows symlink test skipped; `git diff --check` passed. The A-owned owner/current snapshot and actual approval persistence are not connected.

Approved export boundary milestone (2026-10-06): the public in-memory ZIP entry point now requires stored approval metadata for the same Preview ID and fingerprint, enforces approval expiry no later than Preview expiry, and rechecks the current basis and regenerated file fingerprint. The raw ZIP helper is package-private. `mvn package` passed with 80 tests discovered, 79 passed, one Windows symlink test skipped; `git diff --check` passed. B01–B12 top-level progress is 4/12 (33.3%), eight remaining; A-owned approval lookup, authentication, transaction, audit, and HTTP export remain open.

File organization (2026-10-06): B Java classes and matching tests were grouped under `catalog/model`, `recommendation/selection`, `configuration/preview`, and `configuration/export`; configuration design notes were grouped under `assembly`, `preview`, and `export`. Package names, imports, and relative documentation links were updated. This was a structural change, not a new B feature; B01–B12 progress remains 4/12.

AI Capability intake milestone (2026-10-06): a pure B-owned guard now validates the nine fixed IDs, required/optional/undetermined classification, evidence and question fields against a trusted A-supplied allowlist. It rejects duplicates, excluded paths, and incomplete claims before they can become recommendation inputs. `mvn clean verify` found 85 tests: 84 passed, one Windows symlink test skipped. No agreed Spring↔FastAPI wire parser, AI failure handling, current A snapshot, DB save, or HTTP endpoint is connected. B06 remains open and the top-level B01–B12 count remains 4/12.

User application report boundary (2026-10-06): a pure B-owned guard now accepts only APPLIED without a failure reason or FAILED with a defined reason. It assigns USER source and a server-clock timestamp; this cannot assert verified installation. `mvn clean verify` found 88 tests: 87 passed, one Windows symlink test skipped. History persistence, owner/project checks, audit, deletion, and HTTP parsing remain open. B12 remains open and B01–B12 top-level progress remains 4/12.

Recommendation-to-Preview gate (2026-10-06): the Preview assembler now requires a trusted stored recommendation and current basis, rejects non-RECOMMENDED/stale/other-ID results and tools outside its items before Catalog loading, then repeats Catalog and permission checks. `mvn clean verify` found 93 tests: 92 passed, one Windows symlink test skipped. A's authenticated owner-checked read, persistence, and real Catalog remain open; B09/B10 top-level items remain incomplete.

Generation-history metadata (2026-10-06): after recomputing a transient Preview and matching the expected approval fingerprint, B can project IDs, basis, fingerprint, and file path/action/hash into a content-free immutable history object. It reports GENERATED and NOT_RUN/NONE, and compares the current basis for CURRENT/STALE. `mvn clean verify` found 96 tests: 95 passed, one Windows symlink test skipped. Export call order, A's persistence/ownership/audit/deletion, and HTTP responses remain open. B12 remains incomplete.

Approved generation entry point (2026-10-06): B now returns approved ZIP bytes and minimum history metadata together, creating history only after the approval/freshness/ZIP path succeeds. History construction is package-private and ZIP bytes are defensively copied. `mvn clean verify` found 99 tests: 98 passed, one Windows symlink test skipped. A's owner-checked approval lookup, atomic DB save, audit, deletion, and HTTP transfer remain open; B11/B12 top-level items remain incomplete.
