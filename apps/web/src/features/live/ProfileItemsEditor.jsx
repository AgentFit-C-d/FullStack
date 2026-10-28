import { useState } from 'react'
import { addProfileItem, profileFieldKeys, renameProfileItem } from './profileItems.js'

export default function ProfileItemsEditor({ items, onChange, onError }) {
  const [fieldKey, setFieldKey] = useState('features')
  const [name, setName] = useState('')

  function add(event) {
    event.preventDefault()
    try {
      onChange(addProfileItem(items, fieldKey, name))
      setName('')
    } catch (cause) { onError(cause.message) }
  }

  return <div>
    <p>문서 근거가 있는 항목도 직접 수정·삭제할 수 있습니다. 수정한 값과 새 항목은 사용자 입력으로 기록됩니다.</p>
    <ul>{items.map((item, index) => <li key={`${item.fieldKey}-${item.ordinal}`}>
      <label>{item.fieldKey} · {item.source === 'USER' ? '사용자 입력' : '문서 추출'}
        <input aria-label={`${item.fieldKey} 항목 ${index + 1}`} value={item.name}
          onChange={event => onChange(renameProfileItem(items, index, event.target.value))} />
      </label>
      {item.source === 'DOCUMENT' && <small>근거: {item.evidence || '없음'}</small>}
      <button type="button" onClick={() => onChange(items.filter((_, position) => position !== index))}>삭제</button>
    </li>)}</ul>
    <form onSubmit={add}>
      <label>새 항목 종류<select value={fieldKey} onChange={event => setFieldKey(event.target.value)}>
        {profileFieldKeys.map(key => <option key={key} value={key}>{key}</option>)}
      </select></label>
      <input aria-label="새 기획 항목" value={name} onChange={event => setName(event.target.value)} placeholder="추가할 내용" />
      <button>항목 추가</button>
    </form>
  </div>
}
