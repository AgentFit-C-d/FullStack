import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import Brand from '../../components/layout/Brand.jsx'
import { apiRequest, environmentProfileBody, loadSession, planningInputBody, projectPath, reportBody } from './api.js'
import { defaultPermissionChoices, permissionKey, permissionSelections, previewPolicies } from './permissions.js'
import { inputChanges, inputsDirty } from './inputState.js'
import { confirmedProfileItems } from './profileItems.js'
import ProfileItemsEditor from './ProfileItemsEditor.jsx'
import InstalledComponentsEditor from './InstalledComponentsEditor.jsx'
import styles from './LivePage.module.css'

const isProfile = value => value?.kind === 'CONFIRMED' || value?.kind === 'DRAFT'
const parsedLines = value => value.split('\n').map(line => line.trim()).filter(Boolean)

export default function LivePage() {
  const [search, setSearch] = useSearchParams()
  const projectId = search.get('project')
  const [session, setSession] = useState(undefined)
  const [projects, setProjects] = useState([])
  const [detail, setDetail] = useState(null)
  const [inputs, setInputs] = useState(null)
  const [recommendation, setRecommendation] = useState(null)
  const [preview, setPreview] = useState(null)
  const [previewRequest, setPreviewRequest] = useState(null)
  const [approval, setApproval] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [projectName, setProjectName] = useState('')
  const [planningText, setPlanningText] = useState('')
  const [planningFile, setPlanningFile] = useState(null)
  const [planningReady, setPlanningReady] = useState(false)
  const [profileEdit, setProfileEdit] = useState({ projectName: '', projectType: '', domain: '' })
  const [profileItems, setProfileItems] = useState([])
  const [role, setRole] = useState('FULLSTACK')
  const [responsibilities, setResponsibilities] = useState('')
  const [osFamily, setOsFamily] = useState('WINDOWS')
  const [clientId, setClientId] = useState('demo-claude-code')
  const [clientVersion, setClientVersion] = useState('demo-1')
  const [installedComponentsKnown, setInstalledComponentsKnown] = useState(false)
  const [installedComponents, setInstalledComponents] = useState([])
  const [selectedKeys, setSelectedKeys] = useState([])
  const [permissionChoices, setPermissionChoices] = useState({})
  const [existingState, setExistingState] = useState('UNKNOWN')
  const [catalog, setCatalog] = useState(null)
  const [candidates, setCandidates] = useState(null)
  const [history, setHistory] = useState(null)
  const [selectedExportId, setSelectedExportId] = useState('')
  const [reportState, setReportState] = useState('')
  const [reportReason, setReportReason] = useState('')

  const loadProjects = useCallback(async () => {
    const response = await apiRequest('/api/projects')
    setProjects(response.projects || [])
  }, [])

  const loadProject = useCallback(async (id) => {
    if (!id) return
    const base = projectPath(id)
    const current = await apiRequest(base)
    setDetail(current)
    if (isProfile(current.draft)) {
      const review = current.confirmed?.sourceDraftId === current.draft.id
        ? current.confirmed : current.draft
      setProfileEdit({ projectName: review.projectName || current.project.name,
        projectType: review.projectType || '', domain: review.domain || '' })
      setProfileItems(review.items || [])
    }
    if (isProfile(current.confirmed)) {
      const savedInputs = await apiRequest(`${base}/recommendation-inputs`)
      setInputs(savedInputs)
      if (savedInputs.developer) {
        setRole(savedInputs.developer.role)
        setResponsibilities((savedInputs.developer.responsibilities || []).join('\n'))
      } else { setRole('FULLSTACK'); setResponsibilities('') }
      if (savedInputs.environment) {
        setOsFamily(savedInputs.environment.osFamily)
        setClientId(savedInputs.environment.clientId)
        setClientVersion(savedInputs.environment.clientVersion)
        setInstalledComponentsKnown(savedInputs.environment.installedComponentsKnown)
        setInstalledComponents(savedInputs.environment.installedComponents || [])
      } else {
        setOsFamily('WINDOWS'); setClientId('demo-claude-code'); setClientVersion('demo-1')
        setInstalledComponentsKnown(false)
        setInstalledComponents([])
      }
      try {
        const latest = await apiRequest(`${base}/recommendations/latest`)
        setRecommendation(latest)
        setSelectedKeys((latest.items || []).map(item => item.toolKey))
        setPermissionChoices(defaultPermissionChoices(latest.items))
      } catch (cause) { if (cause.status === 404) setRecommendation(null); else throw cause }
    } else { setInputs(null); setRecommendation(null) }
  }, [])

  useEffect(() => {
    let active = true
    loadSession().then(value => {
      if (!active) return
      setSession(value)
      if (value) return loadProjects()
    }).catch(cause => { if (active) setError(cause.message) })
    return () => { active = false }
  }, [loadProjects])

  useEffect(() => {
    // The state updates happen after the API request resolves, not during render.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    if (session && projectId) loadProject(projectId).catch(cause => setError(cause.message))
  }, [session, projectId, loadProject])

  useEffect(() => {
    if (!projectId || detail?.latestAttempt?.state !== 'PROCESSING') return undefined
    const timer = setInterval(() => loadProject(projectId).catch(cause => setError(cause.message)), 2500)
    return () => clearInterval(timer)
  }, [projectId, detail?.latestAttempt?.state, loadProject])

  function selectProject(id) {
    setDetail(null); setInputs(null); setRecommendation(null); setPreview(null)
    setPreviewRequest(null); setApproval(null); setError(''); setNotice('')
    setPlanningText(''); setPlanningFile(null)
    setPlanningReady(false)
    setProfileEdit({ projectName: '', projectType: '', domain: '' })
    setProfileItems([])
    setRole('FULLSTACK'); setResponsibilities(''); setOsFamily('WINDOWS')
    setClientId('demo-claude-code'); setClientVersion('demo-1'); setInstalledComponentsKnown(false)
    setInstalledComponents([])
    setSelectedKeys([]); setPermissionChoices({}); setExistingState('UNKNOWN')
    setHistory(null); setSelectedExportId(''); setReportState(''); setReportReason('')
    setSearch(id ? { project: id } : {})
  }

  async function run(action, success) {
    setBusy(true); setError(''); setNotice('')
    try { await action(); setNotice(success) }
    catch (cause) { setError(cause.message) }
    finally { setBusy(false) }
  }

  async function createProject(event) {
    event.preventDefault()
    const name = projectName.trim()
    if (!name || Array.from(name).length > 100) { setError('프로젝트 이름을 1~100자로 입력해 주세요.'); return }
    await run(async () => {
      const created = await apiRequest('/api/projects', { method: 'POST', body: { name } })
      await loadProjects()
      setProjectName('')
      selectProject(created.project.id)
    }, '프로젝트를 DB에 저장했습니다.')
  }

  async function savePlanning(event) {
    event.preventDefault()
    if (!planningFile && !planningText.trim()) { setError('파일 또는 텍스트를 입력해 주세요.'); return }
    await run(async () => {
      const form = planningInputBody(detail.project.version, planningText, planningFile)
      await apiRequest(`${projectPath(projectId)}/planning-input`, { method: 'PUT', body: form })
      setPlanningReady(true)
      await loadProject(projectId)
    }, '기획 자료를 서버에 저장했습니다. 이제 분석을 시작할 수 있습니다.')
  }

  async function startAnalysis() {
    await run(async () => {
      await apiRequest(`${projectPath(projectId)}/analysis-attempts`, {
        method: 'POST', body: { expectedVersion: detail.project.version },
      })
      setPlanningReady(false)
      await loadProject(projectId)
    }, '분석 작업을 접수했습니다. 처리 상태를 자동으로 확인합니다.')
  }

  async function confirmProfile() {
    await run(async () => {
      await apiRequest(`${projectPath(projectId)}/profile/confirm`, {
        method: 'POST', body: {
          expectedDraftVersion: detail.draft.version,
          requestId: crypto.randomUUID(),
          projectName: profileEdit.projectName.trim(),
          projectType: profileEdit.projectType.trim() || null,
          domain: profileEdit.domain.trim() || null,
          items: confirmedProfileItems(profileItems),
        },
      })
      await loadProject(projectId)
      await loadProjects()
    }, '사용자 확인을 반영한 CONFIRMED Profile을 저장했습니다.')
  }

  async function saveInputs() {
    const form = { role, responsibilities, osFamily, clientId, clientVersion,
      installedComponentsKnown, installedComponents }
    const changes = inputChanges(inputs, form)
    if (!changes.developer && !changes.environment) {
      setError(''); setNotice('저장할 변경 내용이 없습니다.')
      return
    }
    await run(async () => {
      const base = projectPath(projectId)
      const pendingDeveloper = { role, responsibilities }
      const pendingEnvironment = { osFamily, clientId, clientVersion, installedComponentsKnown,
        installedComponents }
      const environmentBody = changes.environment ? environmentProfileBody(
        inputs?.environment?.version || 0, osFamily, clientId, clientVersion,
        installedComponentsKnown, installedComponents) : null
      if (changes.developer) {
        try {
          await apiRequest(`${base}/developer-profile`, {
            method: 'PUT', body: {
              expectedVersion: inputs?.developer?.version || 0,
              role: role.trim(), responsibilities: parsedLines(responsibilities),
            },
          })
          invalidatePreview()
        } catch (cause) {
          await loadProject(projectId)
          setRole(pendingDeveloper.role)
          setResponsibilities(pendingDeveloper.responsibilities)
          setOsFamily(pendingEnvironment.osFamily)
          setClientId(pendingEnvironment.clientId)
          setClientVersion(pendingEnvironment.clientVersion)
          setInstalledComponentsKnown(pendingEnvironment.installedComponentsKnown)
          setInstalledComponents(pendingEnvironment.installedComponents)
          throw cause
        }
      }
      if (changes.environment) {
        try {
          await apiRequest(`${base}/environment-profile`, {
            method: 'PUT', body: environmentBody,
          })
          invalidatePreview()
        } catch (cause) {
          await loadProject(projectId)
          setRole(pendingDeveloper.role)
          setResponsibilities(pendingDeveloper.responsibilities)
          setOsFamily(pendingEnvironment.osFamily)
          setClientId(pendingEnvironment.clientId)
          setClientVersion(pendingEnvironment.clientVersion)
          setInstalledComponentsKnown(pendingEnvironment.installedComponentsKnown)
          setInstalledComponents(pendingEnvironment.installedComponents)
          throw new Error(`${changes.developer ? '역할은 저장됐지만 ' : ''}환경 저장은 실패했습니다. 최신 버전을 불러왔습니다. ${cause.message}`, { cause })
        }
      }
      await loadProject(projectId)
    }, '역할과 환경 선언을 DB에 저장했습니다.')
  }

  async function createRecommendation() {
    if (inputDirty) { setError('역할·환경 변경 내용을 먼저 저장해 주세요.'); return }
    await run(async () => {
      const created = await apiRequest(`${projectPath(projectId)}/recommendations`, {
        method: 'POST', body: { expectedVersions: {
          project: detail.project.version,
          developer: inputs?.developer?.version || 0,
          environment: inputs?.environment?.version || 0,
        } },
      })
      setRecommendation(created)
      setSelectedKeys((created.items || []).map(item => item.toolKey))
      setPermissionChoices(defaultPermissionChoices(created.items))
      invalidatePreview()
    }, 'AI Capability와 데모 카탈로그를 기준으로 추천을 저장했습니다.')
  }

  async function createPreview() {
    if (inputDirty) { setError('역할·환경 변경 내용을 먼저 저장해 주세요.'); return }
    await run(async () => {
      const request = {
        basis: recommendation.basis, recommendationId: recommendation.recommendationId,
        target: { osFamily, clientId, clientVersion }, selectedToolKeys: selectedKeys,
        permissionSelections: permissionSelections(recommendation.items, selectedKeys, permissionChoices),
        existingState, providedFiles: [], generatorVersion: 'agentfit-demo-web-v1',
      }
      const created = await apiRequest(`${projectPath(projectId)}/config-previews`, { method: 'POST', body: request })
      setPreviewRequest(request); setPreview(created); setApproval(null)
    }, '설정 미리보기를 생성했습니다. 내용과 경고를 확인해 주세요.')
  }

  async function approve() {
    if (inputDirty) { setError('역할·환경 변경 내용을 먼저 저장해 주세요.'); return }
    await run(async () => {
      const result = await apiRequest(`${projectPath(projectId)}/config-previews/${preview.previewId}/approval`, {
        method: 'POST', body: { fingerprint: preview.fingerprint, confirmed: true },
      })
      setApproval(result)
    }, '미리보기를 승인했습니다. 미충족 경고가 있다면 다운로드 후에도 유지됩니다.')
  }

  async function download() {
    if (inputDirty) { setError('역할·환경 변경 내용을 먼저 저장해 주세요.'); return }
    await run(async () => {
      const blob = await apiRequest(`${projectPath(projectId)}/config-approvals/${approval.approvalId}/download`, {
        method: 'POST', body: { previewId: preview.previewId, fingerprint: preview.fingerprint,
          previewRequest }, download: true,
      })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url; anchor.download = 'agentfit-config.zip'; anchor.click()
      setTimeout(() => URL.revokeObjectURL(url), 60000)
      setHistory(null)
    }, '설정 ZIP을 다운로드했습니다. 데모 파일의 적용 범위를 확인해 주세요.')
  }

  async function readCatalog() {
    await run(async () => setCatalog(await apiRequest('/api/tool-catalog')), '승인된 카탈로그를 조회했습니다.')
  }

  async function readCandidates() {
    await run(async () => setCandidates(await apiRequest('/api/tool-candidates')), '미검증 Skill 후보를 조회했습니다.')
  }

  async function readHistory() {
    await run(async () => {
      const result = await apiRequest(`${projectPath(projectId)}/configuration-history`)
      setHistory(result)
      setSelectedExportId(previous => result.items?.some(item => item.id === previous) ? previous : result.items?.[0]?.id || '')
    }, '설정 생성 이력을 조회했습니다.')
  }

  async function readMoreHistory() {
    if (!history?.nextCursor) return
    await run(async () => {
      const cursor = encodeURIComponent(history.nextCursor)
      const next = await apiRequest(`${projectPath(projectId)}/configuration-history?cursor=${cursor}`)
      setHistory(previous => ({
        items: [...previous.items, ...(next.items || []).filter(item => !previous.items.some(saved => saved.id === item.id))],
        nextCursor: next.nextCursor,
      }))
    }, '이전 생성 이력을 불러왔습니다.')
  }

  function invalidatePreview() {
    setPreview(null); setPreviewRequest(null); setApproval(null)
  }

  async function submitReport(event) {
    event.preventDefault()
    if (!selectedExportId) { setError('먼저 생성 이력을 조회해 주세요.'); return }
    let body
    try { body = reportBody(reportState, reportReason) }
    catch (cause) { setError(cause.message); return }
    await run(async () => {
      await apiRequest(`${projectPath(projectId)}/configuration-history/${encodeURIComponent(selectedExportId)}/reports`, {
        method: 'POST', body,
      })
      const result = await apiRequest(`${projectPath(projectId)}/configuration-history`)
      setHistory(result)
    }, '적용 결과를 사용자 보고로 저장했습니다. 실제 설치 검증 결과는 아닙니다.')
  }

  const draft = isProfile(detail?.draft) ? detail.draft : null
  const confirmed = detail?.confirmed?.kind === 'CONFIRMED' ? detail.confirmed : null
  const reviewProfile = confirmed?.sourceDraftId === draft?.id ? confirmed : draft
  const profileChanged = reviewProfile && (
    profileEdit.projectName.trim() !== (reviewProfile.projectName || '')
    || (profileEdit.projectType.trim() || null) !== (reviewProfile.projectType || null)
    || (profileEdit.domain.trim() || null) !== (reviewProfile.domain || null)
    || JSON.stringify(profileItems) !== JSON.stringify(reviewProfile.items || [])
  )
  const profileNeedsConfirmation = draft && (!confirmed || confirmed.sourceDraftId !== draft.id || profileChanged)
  const attempt = detail?.latestAttempt
  const inputReady = inputs?.developer && inputs?.environment
  const inputDirty = inputsDirty(inputs, { role, responsibilities, osFamily, clientId,
    clientVersion, installedComponentsKnown, installedComponents })

  return <div className={styles.page}>
    <header className={styles.top}><Brand /><nav><Link to="/app">내 프로젝트</Link></nav></header>
    <main className={styles.main}>
      <div className={styles.heading}><span>AGENTFIT · LIVE DEMO</span><h1>프로젝트에서 설정 다운로드까지</h1><p>이 화면은 실제 Core API, FastAPI, PostgreSQL에 연결됩니다. 카탈로그의 도구와 설정은 검증 전 합성 데모 데이터입니다.</p></div>
      {error && <div className={styles.error} role="alert">{error}</div>}
      {notice && <div className={styles.notice} role="status">{notice}</div>}
      {inputDirty && <div className={styles.notice}>역할·환경 변경 내용을 저장한 뒤 추천·승인·다운로드를 진행해 주세요.</div>}
      {session === undefined && <section className={styles.card}>세션 확인 중…</section>}
      {session === null && <section className={styles.card}><h2>GitHub 로그인이 필요합니다</h2><p>백엔드 API는 실제 세션과 프로젝트 소유권을 확인합니다.</p><a className={styles.action} href="/api/oauth2/authorization/github">GitHub로 로그인</a></section>}
      {session && <>
        <div className={styles.user}>로그인: <strong>{session.user.name}</strong></div>
        <div className={styles.columns}>
          <section className={styles.card}><h2>1. 내 프로젝트</h2><form onSubmit={createProject} className={styles.row}><input aria-label="프로젝트 이름" placeholder="프로젝트 이름 (최대 100자)" value={projectName} onChange={event => setProjectName(event.target.value)} /><button disabled={busy}>생성</button></form>
            <div className={styles.list}>{projects.map(project => <button key={project.id} type="button" className={project.id === projectId ? styles.selected : ''} onClick={() => selectProject(project.id)}>{project.name}</button>)}</div>
            {!projects.length && <p>아직 저장된 프로젝트가 없습니다.</p>}
          </section>
          <section className={styles.card}><h2>현재 상태</h2>{detail ? <><p><strong>{detail.project.name}</strong></p><dl><dt>프로젝트 버전</dt><dd>{detail.project.version}</dd><dt>분석</dt><dd>{attempt?.state || '시작 전'}</dd><dt>기획 확정</dt><dd>{confirmed ? `완료 · v${confirmed.version}` : '미확정'}</dd><dt>추천</dt><dd>{recommendation?.status || '생성 전'}</dd></dl></> : <p>프로젝트를 선택해 주세요.</p>}</section>
        </div>
        <section className={styles.card}><h2>승인 카탈로그와 Skill 후보</h2><p>승인 카탈로그만 추천과 설정 생성에 사용합니다. skills.sh에서 수집한 후보는 미검증 상태로 별도 표시합니다.</p><div className={styles.row}><button type="button" disabled={busy} onClick={readCatalog}>승인 카탈로그 조회</button><button type="button" disabled={busy} onClick={readCandidates}>Skill 후보 조회</button></div>
          {catalog && <div><p>카탈로그 버전: {catalog.revision} · 도구 {catalog.items?.length || 0}개</p><ul>{(catalog.items || []).map(item => <li key={item.key}>{item.name} ({item.key}) · {item.kind}</li>)}</ul></div>}
          {candidates && <div><p>{candidates.source} · {candidates.verificationStatus} · 후보 {candidates.candidates?.length || 0}개</p><ul>{(candidates.candidates || []).map(item => <li key={item.externalId}><a href={item.sourceUrl} target="_blank" rel="noopener noreferrer">{item.name}</a> · 미검증, 추천 제외</li>)}</ul></div>}
        </section>
        {detail && <>
          <section className={styles.card}><h2>2. 기획 자료와 AI 분석</h2><p>PDF·Markdown 파일 또는 텍스트를 서버에 저장합니다. 한 번에 한 방식만 전송합니다.</p><form onSubmit={savePlanning} className={styles.stack}><input aria-label="기획 파일" type="file" accept=".pdf,.md,.markdown" onChange={event => { setPlanningFile(event.target.files[0] || null); if (event.target.files[0]) setPlanningText('') }} /><textarea aria-label="기획 텍스트" placeholder="직접 입력" value={planningText} onChange={event => { setPlanningText(event.target.value); if (event.target.value) setPlanningFile(null) }} /><div className={styles.row}><button disabled={busy}>자료 저장</button><button type="button" disabled={busy || !planningReady || attempt?.state === 'PROCESSING'} onClick={startAnalysis}>AI 분석 시작</button><button type="button" disabled={busy} onClick={() => loadProject(projectId).catch(cause => setError(cause.message))}>상태 새로고침</button></div></form><p>새 분석은 이 화면에서 기획 자료를 저장한 뒤 시작할 수 있습니다.</p><p>최근 분석: {attempt?.state || '없음'} {attempt?.errorCode && `· 오류 ${attempt.errorCode}`}</p></section>
          {draft && <section className={styles.card}>
            <h2>3. 분석 초안 확인</h2>
            <p>{confirmed?.sourceDraftId === draft.id ? '확정된 항목을 수정할 수 있습니다.' : `DRAFT v${draft.version} · 문서 추출 결과를 검토하고 전체 항목을 확정합니다.`}</p>
            <div className={styles.grid3}>
              <label>프로젝트명<input value={profileEdit.projectName} onChange={event => setProfileEdit({ ...profileEdit, projectName: event.target.value })} /></label>
              <label>서비스 유형<input value={profileEdit.projectType} onChange={event => setProfileEdit({ ...profileEdit, projectType: event.target.value })} /></label>
              <label>도메인<input value={profileEdit.domain} onChange={event => setProfileEdit({ ...profileEdit, domain: event.target.value })} /></label>
            </div>
            <details><summary>기획 항목 편집 ({profileItems.length}개)</summary>
              <ProfileItemsEditor items={profileItems} onChange={setProfileItems} onError={setError} />
            </details>
            <button type="button" disabled={busy || !profileEdit.projectName.trim() || !profileNeedsConfirmation} onClick={confirmProfile}>확인하고 확정 저장</button>
          </section>}
          {confirmed && <section className={styles.card}>
            <h2>4. 내 역할과 개발환경</h2>
            <p>입력 정보는 내 프로젝트에 저장됩니다. 설치 버전은 실제 PC를 검사한 값이 아닌 사용자 선언입니다.</p>
            <div className={styles.grid3}>
              <label>역할<input value={role} onChange={event => setRole(event.target.value)} /></label>
              <label>OS<input value={osFamily} onChange={event => { setOsFamily(event.target.value); invalidatePreview() }} /></label>
              <label>AI Client<input value={clientId} onChange={event => { setClientId(event.target.value); invalidatePreview() }} /></label>
              <label>Client 버전<input value={clientVersion} onChange={event => { setClientVersion(event.target.value); invalidatePreview() }} /></label>
            </div>
            <label>담당 업무 (줄마다 하나)<textarea value={responsibilities} onChange={event => setResponsibilities(event.target.value)} /></label>
            <InstalledComponentsEditor known={installedComponentsKnown} items={installedComponents}
              onKnownChange={setInstalledComponentsKnown} onChange={setInstalledComponents} />
            <p>데모 카탈로그 대상: WINDOWS / demo-claude-code / demo-1. 다른 값이면 호환 도구가 없을 수 있습니다.</p>
            <button type="button" disabled={busy} onClick={saveInputs}>역할·환경 저장</button>
            {inputReady && <span>저장 버전: 역할 {inputs.developer.version}, 환경 {inputs.environment.version}</span>}
          </section>}
          {confirmed && <section className={styles.card}><h2>5. 추천과 권한 선택</h2><p>AI가 필요한 Capability를 분석하고, Core API가 데모 카탈로그의 호환성을 판정합니다.</p><button type="button" disabled={busy || !inputReady} onClick={createRecommendation}>추천 생성</button>{recommendation && <><p>결과: <strong>{recommendation.status}</strong> · {recommendation.validity || '새 추천'}</p><p>현재 정적 설정 생성은 개별 권한 거부를 표현할 수 없습니다. 도구 전체를 빼려면 선택을 해제하세요.</p><ul>{(recommendation.items || []).map(item => <li key={item.toolKey}><label><input type="checkbox" checked={selectedKeys.includes(item.toolKey)} onChange={event => { setSelectedKeys(event.target.checked ? [...selectedKeys, item.toolKey] : selectedKeys.filter(key => key !== item.toolKey)); invalidatePreview() }} /> {item.name} ({item.toolKey})</label>{selectedKeys.includes(item.toolKey) && (item.permissionOptions || []).map(option => <label key={option.mappingKey}>{option.mappingKey} 권한<select value={permissionChoices[permissionKey(item.toolKey, option.mappingKey)] ?? option.defaultPolicy} onChange={event => { setPermissionChoices(previous => ({ ...previous, [permissionKey(item.toolKey, option.mappingKey)]: event.target.value })); invalidatePreview() }}>{previewPolicies(option).map(policy => <option key={policy} value={policy}>{policy === 'ASK_EACH_TIME' ? '매번 확인' : '항상 허용'}</option>)}</select></label>)}</li>)}</ul>{!recommendation.items?.length && <p>{recommendation.validity === 'STALE' ? '확정 정보가 바뀌어 이전 추천이 만료됐습니다. 추천을 다시 생성해 주세요.' : '선택 가능한 도구가 없습니다. 추가 정보가 필요하거나 호환 대상이 없을 수 있습니다.'}</p>}{(recommendation.notices || []).length > 0 && <details><summary>질문·안내 ({recommendation.notices.length})</summary><pre>{JSON.stringify(recommendation.notices, null, 2)}</pre></details>}</>}</section>}
          {recommendation?.items?.length > 0 && <section className={styles.card}><h2>6. 설정 미리보기와 다운로드</h2><p>부분 구성을 선택해도 미충족 Capability를 표시합니다. 합성 데모 설정은 실제 설치에 사용하지 마세요.</p><label>현재 설정 상태 <select value={existingState} onChange={event => { setExistingState(event.target.value); invalidatePreview() }}><option value="UNKNOWN">모름</option><option value="USER_SAYS_EMPTY">사용자가 비어 있다고 선언</option></select></label><div className={styles.row}><button type="button" disabled={busy || recommendation.validity === 'STALE' || selectedKeys.length === 0} onClick={createPreview}>미리보기 생성</button>{preview && <button type="button" disabled={busy} onClick={approve}>확인하고 승인</button>}{approval && <button type="button" disabled={busy} onClick={download}>ZIP 다운로드</button>}</div>{selectedKeys.length === 0 && <p>설정 파일을 만들 도구를 하나 이상 선택해 주세요.</p>}{preview && <><p>미충족: {preview.unmetCapabilityKeys?.length ? preview.unmetCapabilityKeys.join(', ') : '없음'}</p>{preview.files?.map(file => <details key={file.relativePath}><summary>{file.relativePath} · {file.action}</summary><pre>{file.content}</pre></details>)}</>}</section>}
          {confirmed && <section className={styles.card}><h2>7. 생성 이력과 적용 결과</h2><p>다운로드 후 이력을 조회할 수 있습니다. 적용 결과는 사용자 보고이며 설치 성공을 자동 검증하지 않습니다.</p><button type="button" disabled={busy} onClick={readHistory}>생성 이력 조회</button>
            {history && <>{history.items?.length ? <><ul>{history.items.map(item => <li key={item.id}>{new Date(item.createdAt).toLocaleString('ko-KR')} · {item.status} · {item.validity} · 파일 {item.files?.length || 0}개{item.latestUserReport && ` · 사용자 보고 ${item.latestUserReport.reportedState}`}</li>)}</ul><form onSubmit={submitReport} className={styles.stack}><label>보고할 생성 이력<select value={selectedExportId} onChange={event => setSelectedExportId(event.target.value)}>{history.items.map(item => <option key={item.id} value={item.id}>{new Date(item.createdAt).toLocaleString('ko-KR')} · {item.id}</option>)}</select></label><label>적용 결과<select value={reportState} onChange={event => { setReportState(event.target.value); setReportReason('') }}><option value="">선택해 주세요</option><option value="APPLIED">적용했다고 보고</option><option value="FAILED">적용 실패 보고</option></select></label>{reportState === 'FAILED' && <label>실패 사유<select value={reportReason} onChange={event => setReportReason(event.target.value)}><option value="">선택해 주세요</option><option value="COPY_FAILED">파일 복사 실패</option><option value="AUTH_REQUIRED">인증 필요</option><option value="DEPENDENCY_MISSING">의존성 누락</option><option value="UNKNOWN">알 수 없음</option></select></label>}<button disabled={busy}>적용 결과 저장</button></form></> : <p>생성된 설정 이력이 없습니다.</p>}{history.nextCursor && <button type="button" disabled={busy} onClick={readMoreHistory}>이전 이력 더 보기</button>}</>}
          </section>}
        </>}
      </>}
    </main>
  </div>
}
