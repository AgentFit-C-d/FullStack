import { permissionKey, previewPolicies } from './permissions.js'
import { riskReviewText } from './recommendationItems.js'
import { capabilityLabel } from './recommendationFeedback.js'

function ToolItems({ items, selectedKeys, permissionChoices, onSelect, onPermissionChange }) {
  if (!items?.length) return null
  return <ul>{items.map(item => <li key={item.toolKey}>
    <label><input type="checkbox" checked={selectedKeys.includes(item.toolKey)}
      onChange={event => onSelect(item.toolKey, event.target.checked)} /> {item.name} ({item.toolKey})</label>
    {item.reason && <p>추천 근거: {item.reason}</p>}
    <p>카탈로그 도구 버전: {item.catalogVersion} · 지원 조건: {item.supportKey}</p>
    {item.coveredRequiredCapabilities?.length > 0 && <p>충족하는 필수 기능: {item.coveredRequiredCapabilities.map(capabilityLabel).join(', ')}</p>}
    {item.coveredOptionalCapabilities?.length > 0 && <p>충족하는 선택 기능: {item.coveredOptionalCapabilities.map(capabilityLabel).join(', ')}</p>}
    {item.supportEvidence && <p>지원 확인: {item.supportEvidence.checkedAt} · <a href={item.supportEvidence.sourceUrl} target="_blank" rel="noopener noreferrer">확인 자료</a></p>}
    <p>{riskReviewText(item.riskReview)}</p>
    {item.riskReview?.evidence && <p>위험 검토: {item.riskReview.evidence.checkedAt} · <a href={item.riskReview.evidence.sourceUrl} target="_blank" rel="noopener noreferrer">확인 자료</a></p>}
    {item.sourceUrl && <p>출처: <a href={item.sourceUrl} target="_blank" rel="noopener noreferrer">도구 원문</a></p>}
    {selectedKeys.includes(item.toolKey) && (item.permissionOptions || []).map(option =>
      <label key={option.mappingKey}>{option.mappingKey} 권한 · {option.required ? '필수' : '선택'}
        <select value={permissionChoices[permissionKey(item.toolKey, option.mappingKey)] ?? option.defaultPolicy}
          onChange={event => onPermissionChange(item.toolKey, option.mappingKey, event.target.value)}>
          {previewPolicies(option).map(policy => <option key={policy} value={policy}>
            {policy === 'ASK_EACH_TIME' ? '매번 확인' : '항상 허용'}
          </option>)}
        </select>{option.evidence && <small>확인일 {option.evidence.checkedAt} · <a href={option.evidence.sourceUrl} target="_blank" rel="noopener noreferrer">권한 근거</a></small>}
      </label>)}
  </li>)}</ul>
}

export default function RecommendationItems(props) {
  const { recommendation } = props
  return <>
    {recommendation.items?.length > 0 && <><h3>기본 구성</h3><ToolItems items={recommendation.items} {...props} /></>}
    {recommendation.optionalItems?.length > 0 && <>
      <h3>선택 후보</h3><p>기본 구성에는 포함되지 않습니다. 필요한 경우 직접 선택해 주세요.</p>
      <ToolItems items={recommendation.optionalItems} {...props} />
    </>}
    {recommendation.validity === 'STALE' && recommendation.savedItems?.length > 0 &&
      <><h3>저장 당시 도구 (조회 전용)</h3><ul>{recommendation.savedItems.map(item =>
        <li key={item.toolKey}>{item.toolKey} {item.toolVersion} · {item.reason} · {riskReviewText(item.riskReview)}</li>
      )}</ul></>}
  </>
}
