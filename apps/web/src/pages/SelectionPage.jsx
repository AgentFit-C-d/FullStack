import { useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router'
import { useDemoProjects } from '../features/projects/DemoContext.js'
import { getVisibleProjects } from '../features/projects/projectModel.js'
import { canEnterDemoSelection, validateSelectionPreview } from '../features/selection/selectionModel.js'
import { permissionExamples, selectionCardExamples } from '../features/selection/selectionDemoFixture.js'
import PageHeader from '../components/layout/PageHeader.jsx'
import WizardProgress from '../features/projects/WizardProgress.jsx'
import { Button, Card, FormField } from '../components/ui/UI.jsx'
import styles from './SelectionPage.module.css'

export default function SelectionPage() {
  const { demoId } = useParams()
  const navigate = useNavigate()
  const { state, dispatch } = useDemoProjects()
  const project = getVisibleProjects(state).find(item => item.id === demoId)
  if (!project) return <Card padded><h1>프로젝트를 찾을 수 없어요</h1><p>현재 시연 세션의 목록에서 프로젝트를 다시 선택해주세요.</p><Button to="/demo/projects">내 프로젝트로</Button></Card>
  const path = `/demo/projects/${encodeURIComponent(project.id)}`
  if (!canEnterDemoSelection(state, project.id)) return <Navigate to={`${path}/recommendation`} replace />
  return <SelectionScreen key={project.id} mode="demo" preview={state.selectionPreviews[project.id]} recommendationPath={`${path}/recommendation`} onPreview={() => { dispatch({ type: 'preview-enter', id: project.id }); navigate(`${path}/preview`) }} onEdit={action => dispatch({ ...action, type: 'selection-preview', id: project.id })} />
}

export function SelectionScreen({ mode, preview, recommendationPath, onEdit, onPreview }) {
  const [notice, setNotice] = useState('')
  if (mode !== 'demo') return <Card padded><h1>도구·권한 선택을 이용할 수 없어요</h1><p>인증된 세션과 검증된 추천·Catalog 데이터가 필요합니다.</p></Card>
  if (preview.source !== 'demo-ui') return <Card padded><h1>도구 선택 정보를 확인할 수 없어요</h1></Card>
  const denied = preview.visible && preview.permission === 'DENY' ? validateSelectionPreview(preview) : ''
  function edit(action) { onEdit(action); setNotice('') }
  function next() {
    const error = validateSelectionPreview(preview)
    setNotice(error ? `${error} 실제 설정 Preview는 준비되지 않았습니다. 별도 시연 경로에서 화면만 확인할 수 있습니다.` : '시연 선택 조건만 확인했어요. 실제 Preview는 준비되지 않았습니다. 별도 시연 경로를 이용해주세요. 실제 권한 승인·설치·설정 파일 생성·서버 저장은 수행하지 않았습니다.')
  }
  return <>
    <PageHeader eyebrow="08 / 10 · TOOL & PERMISSIONS" title="도구와 권한을 선택하세요" description="실제 추천 결과가 제공되면 도구를 고르고 권한 정책을 정합니다." />
    <WizardProgress currentStep={8} navigationOnly />
    <div className={styles.preview}><Button variant="soft" aria-expanded={preview.visible} aria-controls="selection-examples" onClick={() => edit({ edit: 'visibility', value: !preview.visible })}>{preview.visible ? '선택 UI 예시 미리보기 닫기' : '선택 UI 예시 미리보기'}</Button></div>
    <div id="selection-examples" className={styles.grid}>
      <Card padded><h3>추천 도구 선택</h3><p>도구 카드 전체를 눌러 선택하거나 해제할 수 있습니다.</p>
        <div className={styles.list}>{preview.visible ? selectionCardExamples.map(tool => {
          const selected = tool.required || preview.selectedIds.includes(tool.id)
          return <button key={tool.id} type="button" className={`${styles.tool} ${selected ? styles.selected : ''}`} aria-pressed={selected} aria-disabled={tool.required || undefined} onClick={() => edit({ edit: 'tool', toolId: tool.id })}>
            <span className={styles.icon} aria-hidden="true">{selected ? '✓' : '◇'}</span><span className={styles.body}><span className={styles.head}><strong>{tool.name}</strong>{(tool.required || selected) && <span className={styles.badge}>{tool.required ? '필수 UI 예시' : '✓ 선택됨'}</span>}</span><span className={styles.description}>버전: 확인되지 않음 · 시연 카드<br />{tool.description}</span></span>
          </button>
        }) : <div className={styles.item}><strong>선택할 수 있는 검증된 도구가 없습니다.</strong><p>Catalog 연동 전에는 임의 도구를 제안하거나 선택 상태로 만들지 않습니다.</p></div>}</div>
      </Card>
      <Card padded><h3>권한 정책</h3><p>{preview.visible ? '시연 기본값은 사용할 때마다 확인입니다. 실제 승인 정책은 미확정입니다.' : '실제 도구별 권한 정보와 승인 정책은 확인되지 않았습니다.'}</p>
        <FormField id="selection-permission" label="도구 실행 권한"><select id="selection-permission" className={styles.select} disabled={!preview.visible} value={preview.visible ? preview.permission : ''} onChange={event => edit({ edit: 'permission', value: event.target.value })}>{!preview.visible && <option value="">미조회 · 확인되지 않음</option>}{permissionExamples.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></FormField>
        {denied && <div className={styles.warning} role="alert"><span aria-hidden="true">!</span><span>{denied}</span></div>}
        <div className={styles.alert}><span aria-hidden="true">ⓘ</span><span>실제 도구별 권한 범위는 지원 manifest 확인 후 표시됩니다. ASK_EACH_TIME은 이 시연의 기본 조작값이며 실제 서비스 정책을 확정하지 않습니다.</span></div>
        {preview.visible && <><Button aria-expanded={preview.permissionDetailsOpen} aria-controls="selection-permission-info" onClick={() => edit({ edit: 'details' })}>권한 정보 {preview.permissionDetailsOpen ? '닫기' : '보기'}</Button>{preview.permissionDetailsOpen && <div id="selection-permission-info" className={`${styles.item} ${styles.permissionInfo}`}><strong>권한 정보 UI 예시</strong><p>항상 허용 / 사용할 때마다 확인 / 허용하지 않음의 표시만 확인합니다. 실제 파일·명령 실행 권한 범위는 미확정이며 어떤 권한도 부여하지 않습니다.</p></div>}</>}
      </Card>
    </div>
    <p className={styles.notice}>실제 설정 Preview와 승인은 제공하지 않습니다. 선택은 현재 프로젝트의 시연 메모리에만 유지되며 새로고침 시 초기화됩니다.</p>
    <p className={styles.notice} role="status">{notice}</p>
    <div className={styles.preview}><Button variant="soft" onClick={onPreview}>시연용 설정 확인·승인 화면 미리보기</Button></div>
    <div className={styles.actions}><Button to={recommendationPath}>← 추천 확인</Button><Button variant="primary" large onClick={next}>Preview 확인하기 →</Button></div>
  </>
}
