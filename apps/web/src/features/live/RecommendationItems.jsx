import { permissionKey, previewPolicies } from './permissions.js'
import { riskReviewText } from './recommendationItems.js'

function ToolItems({ items, selectedKeys, permissionChoices, onSelect, onPermissionChange }) {
  if (!items?.length) return null
  return <ul>{items.map(item => <li key={item.toolKey}>
    <label><input type="checkbox" checked={selectedKeys.includes(item.toolKey)}
      onChange={event => onSelect(item.toolKey, event.target.checked)} /> {item.name} ({item.toolKey})</label>
    {item.reason && <p>추천 근거: {item.reason}</p>}
    <p>{riskReviewText(item.riskReview)}</p>
    {item.sourceUrl && <p>출처: <a href={item.sourceUrl} target="_blank" rel="noopener noreferrer">도구 원문</a></p>}
    {selectedKeys.includes(item.toolKey) && (item.permissionOptions || []).map(option =>
      <label key={option.mappingKey}>{option.mappingKey} 권한
        <select value={permissionChoices[permissionKey(item.toolKey, option.mappingKey)] ?? option.defaultPolicy}
          onChange={event => onPermissionChange(item.toolKey, option.mappingKey, event.target.value)}>
          {previewPolicies(option).map(policy => <option key={policy} value={policy}>
            {policy === 'ASK_EACH_TIME' ? '매번 확인' : '항상 허용'}
          </option>)}
        </select>
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
