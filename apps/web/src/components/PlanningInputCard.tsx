import type { ChangeEvent, FormEvent } from 'react';
import type { PlanningInputMetadata } from '../api/types';

type InputMode = 'file' | 'text';

interface PlanningInputCardProps {
  inputMode: InputMode;
  file?: File;
  inputText: string;
  uploaded: PlanningInputMetadata | null;
  busy: boolean;
  selected: boolean;
  onModeChange: (mode: InputMode) => void;
  onFileChange: (event: ChangeEvent<HTMLInputElement>) => void;
  onTextChange: (text: string) => void;
  onUpload: (event: FormEvent<HTMLFormElement>) => void;
}

export function PlanningInputCard({
  inputMode,
  file,
  inputText,
  uploaded,
  busy,
  selected,
  onModeChange,
  onFileChange,
  onTextChange,
  onUpload,
}: PlanningInputCardProps) {
  return (
    <article className="card">
      <div className="card-title">
        <span className="step">02</span>
        <h2>기획서 입력</h2>
      </div>
      <div className="tabs">
        <button
          type="button"
          className={inputMode === 'file' ? 'active' : ''}
          onClick={() => onModeChange('file')}
        >
          파일 업로드
        </button>
        <button
          type="button"
          className={inputMode === 'text' ? 'active' : ''}
          onClick={() => onModeChange('text')}
        >
          텍스트 입력
        </button>
      </div>
      <form onSubmit={onUpload}>
        {inputMode === 'file' ? (
          <label className="drop">
            <input
              type="file"
              accept=".pdf,.md,.markdown,application/pdf,text/markdown"
              onChange={onFileChange}
            />
            {file ? (
              <strong>{file.name}</strong>
            ) : (
              <>
                <strong>PDF 또는 Markdown</strong>
                <span>최대 10MiB · 클릭해서 선택</span>
              </>
            )}
          </label>
        ) : (
          <textarea
            value={inputText}
            onChange={(event) => onTextChange(event.target.value)}
            placeholder="기획서 내용을 붙여 넣으세요."
            rows={8}
          />
        )}
        <button className="primary full" disabled={busy || !selected}>
          {busy ? '처리 중…' : '기획서 업로드'}
        </button>
      </form>
      {uploaded && (
        <div className="success">
          업로드 완료 · {uploaded.inputType} · revision {uploaded.revision}
          {uploaded.pageCount ? ` · ${uploaded.pageCount}페이지` : ''}
        </div>
      )}
    </article>
  );
}
