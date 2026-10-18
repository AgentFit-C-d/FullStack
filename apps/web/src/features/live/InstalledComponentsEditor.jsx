export default function InstalledComponentsEditor({ known, items, onKnownChange, onChange }) {
  return <div>
    <label><input type="checkbox" checked={known} onChange={event => onKnownChange(event.target.checked)} />
      설치 도구 목록을 알고 있습니다</label>
    <p>미확인 상태는 추천 시 추가 확인 대상입니다. 목록을 알고 있다면 비어 있는 상태도 그대로 저장할 수 있습니다. 버전 입력은 선택 사항입니다.</p>
    {known && <>
      {items.map((item, index) => <div key={index}>
        <label>설치 도구 키<input aria-label={`설치 도구 ${index + 1} 키`} value={item.toolKey}
          onChange={event => onChange(items.map((current, position) => position === index
            ? { ...current, toolKey: event.target.value } : current))} /></label>
        <label>설치 버전 (선택)<input aria-label={`설치 도구 ${index + 1} 버전`} value={item.version || ''}
          onChange={event => onChange(items.map((current, position) => position === index
            ? { ...current, version: event.target.value } : current))} /></label>
        <button type="button" onClick={() => onChange(items.filter((_, position) => position !== index))}>도구 삭제</button>
      </div>)}
      <button type="button" disabled={items.length >= 50} onClick={() => onChange([...items, { toolKey: '', version: '' }])}>설치 도구 추가</button>
    </>}
  </div>
}
