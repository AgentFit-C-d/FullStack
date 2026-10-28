import { Navigate, useNavigate, useParams } from 'react-router'
import { useDemoProjects } from '../features/projects/DemoContext.js'
import { getVisibleProjects } from '../features/projects/projectModel.js'
import { canEnterDemoPreview, unpreparedPreviewData } from '../features/preview/previewModel.js'
import { previewDemoFixture } from '../features/preview/previewDemoFixture.js'
import { selectionCardExamples, permissionExamples } from '../features/selection/selectionDemoFixture.js'
import { environmentClient } from '../features/environment/clientDefinition.js'
import PageHeader from '../components/layout/PageHeader.jsx'
import WizardProgress from '../features/projects/WizardProgress.jsx'
import { Badge, Button, Card } from '../components/ui/UI.jsx'
import styles from './PreviewPage.module.css'

export default function PreviewPage() {
  const { demoId } = useParams()
  const navigate = useNavigate()
  const { state, dispatch } = useDemoProjects()
  const project = getVisibleProjects(state).find(item => item.id === demoId)
  if (!project) return <Card padded><h1>프로젝트를 찾을 수 없어요</h1><p>현재 시연 세션의 목록에서 프로젝트를 다시 선택해주세요.</p><Button to="/demo/projects">내 프로젝트로</Button></Card>
  const path = `/demo/projects/${encodeURIComponent(project.id)}`
  if (!canEnterDemoPreview(state, project.id)) return <Navigate to={`${path}/selection`} replace />
  return <PreviewScreen key={project.id} mode="demo" entry={state.previewDrafts[project.id]} selection={state.selectionPreviews[project.id]} selectionPath={`${path}/selection`} onComplete={() => { dispatch({ type: 'complete-enter', id: project.id }); navigate(`${path}/complete`) }} onEdit={(edit, value) => dispatch({ type: 'preview-edit', id: project.id, edit, value })} />
}
export function PreviewScreen({ mode, entry, selection, selectionPath, onEdit, onComplete }) {
  if (mode !== 'demo') return <Card padded><h1>설정 Preview를 이용할 수 없어요</h1><p>인증된 세션과 서버에서 검증한 설정 Preview·승인 API 연결이 필요합니다.</p></Card>
  if (entry?.source !== 'demo-ui' || selection?.source !== 'demo-ui') return <Card padded><h1>설정 확인 정보를 불러올 수 없어요</h1></Card>
  const examples = entry.visible ? selectionCardExamples.filter(tool => selection.selectedIds.includes(tool.id)) : []
  function edit(field, value) { onEdit(field, value) }
  return <>
    <PageHeader eyebrow="09 / 10 · PREVIEW & APPROVAL" title="설정 확인과 최종 승인" description="선택한 도구·권한과 생성 예정 파일을 확인해주세요. 실제 설정이나 PC 상태를 변경·검사하지 않습니다." />
    <WizardProgress currentStep={9} navigationOnly />
    <div className={styles.preview}><Button variant="soft" aria-expanded={entry.visible} aria-controls="preview-example" onClick={() => edit('visibility', !entry.visible)}>{entry.visible ? '시연 Preview 예시 닫기' : '시연 Preview 예시 열기'}</Button><p>{unpreparedPreviewData.status === 'NOT_PREPARED' ? '실제 Preview 미준비' : 'Preview를 확인할 수 없음'} · {environmentClient.name} 설정 생성 API 미연동</p></div>
    <div className={styles.grid}>
      <Card padded><div className={styles.eyebrow}>SELECTED SETUP</div><h3 className={styles.heading}>선택한 도구</h3><p>{entry.visible ? '시연 선택 요약 · 실제 추천 도구가 아닙니다.' : '선택된 검증 도구 없음'}</p>
        {examples.length ? examples.map(tool => <div className={styles.item} key={tool.id}><strong>{tool.name}</strong><p>{tool.required ? '필수 카드 UI 동작 예시' : '선택 카드 UI 동작 예시'} · 실제 호환성·권한 미검증</p></div>) : <div className={styles.item}><p>Catalog 연결 전이라 실제 도구 선택은 없습니다.</p></div>}
        <div className={styles.divider} /><h3>선택한 권한 정책</h3><p>{entry.visible ? `${permissionExamples.find(([value]) => value === selection.permission)?.[1] ?? '확인되지 않음'} · 시연 조작값` : '확인되지 않음'}</p>
        {entry.visible && <p>실제 Claude Code 권한 규칙·모드로 변환하거나 승인하지 않습니다.</p>}
      </Card>
      <Card padded><div className={styles.eyebrow}>FILE PREVIEW</div><h3 className={styles.heading}>생성 예정 파일</h3><p>manifest 및 지원 범위가 검증되기 전이라 파일을 확정할 수 없습니다.</p>
        <div className={styles.item}><div className={styles.itemHead}><strong>예정 파일</strong><Badge>확인 전</Badge></div><p>파일 경로: <b>확인되지 않음</b></p><p>새 파일인지 수정 파일인지: <b>판단할 수 없음</b></p><p>기존 파일 상태: <b>확인 안 됨</b></p></div>
        <div className={styles.alert}><span aria-hidden="true">ⓘ</span><span>미제공 기존 설정 파일은 확인되지 않은 상태입니다. 이 Preview는 실제 PC를 검사하거나 설정을 자동 변경하지 않습니다.</span></div>
        {entry.visible && <div id="preview-example"><strong>{previewDemoFixture.title}</strong><pre className={styles.code}>{previewDemoFixture.text}</pre></div>}
      </Card>
    </div>
    <Card padded className={styles.approval}><span className={styles.warning}><Badge>시안 · 실제 승인 API 연결 전</Badge></span><h3 className={styles.confirmTitle}>최종 확인</h3><p>현재 검증된 파일이나 실제 경로가 없음을 확인해주세요. 아래 체크는 시안 화면 확인용이며 서버 승인·설정 생성·PC 적용을 수행하지 않습니다.</p>
      <label className={styles.checkline}><input type="checkbox" disabled={!entry.visible} checked={entry.acknowledged} onChange={event => edit('acknowledge', event.target.checked)} /><span>Preview 상태와 권한 정책을 확인했습니다. (시안 화면 확인)</span></label>
      <div className={styles.alert}><span aria-hidden="true">ⓘ</span><span>실서비스의 승인은 보여준 파일·권한·기준 구성에 대한 동의입니다. 사용자 PC에 자동 설치했다는 뜻이 아닙니다.</span></div>
    </Card>
    <p id="preview-demo-flow" className={styles.notice}>시연 체크는 실제 승인이 아닙니다. 예시를 닫거나 선택 도구·권한이 바뀌면 다시 확인해야 합니다. 현재 세션에만 유지되며 새로고침하면 초기화됩니다. 체크 후 버튼은 다운로드·적용 안내(10/10)의 시연 화면으로만 이동합니다. 실제 서버 승인이나 다운로드가 아닙니다.</p>
    <div className={styles.actions}><Button to={selectionPath}>← 도구·권한 수정</Button><Button variant="primary" large disabled={!entry.visible || !entry.acknowledged} onClick={onComplete} aria-describedby="preview-demo-flow">확인하고 적용 안내 보기 →</Button></div>
  </>
}
