import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import {
  createProject,
  getSession,
  latestAnalysis,
  latestAnalysisResult,
  listProjects,
  saveAnalysisReview,
  startAnalysis,
  uploadPlanningInput,
} from './api/agentfitApi';
import type {
  AnalysisAttempt,
  AnalysisResultResponse,
  PlanningInputMetadata,
  Project,
  Session,
} from './api/types';
import { AnalysisCard } from './components/AnalysisCard';
import { AnalysisResultCard } from './components/AnalysisResultCard';
import { PlanningInputCard } from './components/PlanningInputCard';
import { ProjectCard } from './components/ProjectCard';
import './styles.css';

const LOGIN_URL = '/api/oauth2/authorization/github';
const ALLOWED_FILE_PATTERN = /\.(pdf|md|markdown)$/i;
const MAX_FILE_SIZE = 10 * 1024 * 1024;
const POLLING_STATES = ['PROCESSING', 'QUEUED', 'RUNNING'];

export default function App() {
  const [session, setSession] = useState<Session | null | undefined>(undefined);
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [newName, setNewName] = useState('');
  const [file, setFile] = useState<File | undefined>();
  const [inputText, setInputText] = useState('');
  const [inputMode, setInputMode] = useState<'file' | 'text'>('file');
  const [uploaded, setUploaded] = useState<PlanningInputMetadata | null>(null);
  const [attempt, setAttempt] = useState<AnalysisAttempt | null>(null);
  const [analysisResult, setAnalysisResult] = useState<AnalysisResultResponse | null>(null);
  const [analysisResultError, setAnalysisResultError] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const selected = useMemo(() => projects.find((project) => project.id === selectedId), [projects, selectedId]);

  const loadAnalysisResult = useCallback(async (projectId: string) => {
    setAnalysisResultError('');
    try {
      setAnalysisResult(await latestAnalysisResult(projectId));
    } catch (error) {
      setAnalysisResult(null);
      setAnalysisResultError(errorMessage(error));
    }
  }, []);

  const refreshProjects = useCallback(async () => {
    const items = await listProjects();
    setProjects(items);
    setSelectedId((current) => current || items[0]?.id || '');
  }, []);

  useEffect(() => {
    getSession().then(async (value) => { setSession(value); if (value) await refreshProjects(); }).catch(() => setSession(null));
  }, [refreshProjects]);

  useEffect(() => {
    if (!selectedId) return;
    let cancelled = false;
    void latestAnalysis(selectedId)
      .then((latest) => {
        if (!cancelled) setAttempt(latest);
      })
      .catch(() => {
        if (!cancelled) setAttempt(null);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedId]);

  useEffect(() => {
    if (!selectedId || !attempt) return;
    if (attempt.state === 'SUCCEEDED') {
      void loadAnalysisResult(selectedId);
      return;
    }
    if (!POLLING_STATES.includes(attempt.state)) return;
    const timer = window.setInterval(async () => {
      try {
        const latest = await latestAnalysis(selectedId);
        setAttempt(latest);
        if (latest.state === 'SUCCEEDED') {
          setAnalysisResult(await latestAnalysisResult(selectedId));
        }
      } catch {
        // Keep the latest known state when polling temporarily fails.
      }
    }, 2500);
    return () => window.clearInterval(timer);
  }, [selectedId, attempt, loadAnalysisResult]);

  if (session === undefined) return <main className="shell"><p className="muted">로그인 상태를 확인하고 있습니다…</p></main>;
  if (!session) return <main className="shell login"><div className="brand">AgentFit</div><h1>기획서에서 실행 가능한 분석까지</h1><p>로그인 후 프로젝트를 만들고 AI 분석을 시작해 보세요.</p><a className="primary" href={LOGIN_URL}>GitHub로 로그인</a></main>;

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!newName.trim()) return;

    setBusy(true);
    setMessage('');
    try {
      const project = await createProject(newName.trim());
      setProjects((items) => [...items, project]);
      setSelectedId(project.id);
      setNewName('');
      setMessage('프로젝트를 만들었습니다.');
    } catch (error) {
      setMessage(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  function selectFile(event: ChangeEvent<HTMLInputElement>) {
    const nextFile = event.target.files?.[0];
    if (!nextFile) {
      setFile(undefined);
      return;
    }
    if (!ALLOWED_FILE_PATTERN.test(nextFile.name)) {
      setMessage('PDF 또는 Markdown 파일만 업로드할 수 있습니다.');
      setFile(undefined);
      return;
    }
    if (nextFile.size > MAX_FILE_SIZE) {
      setMessage('파일 크기는 10MiB 이하여야 합니다.');
      setFile(undefined);
      return;
    }
    setMessage('');
    setFile(nextFile);
  }

  async function handleUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) {
      setMessage('먼저 프로젝트를 선택해 주세요.');
      return;
    }
    setBusy(true);
    setMessage('');
    try {
      const result = await uploadPlanningInput(
        selected.id,
        selected.version,
        inputMode === 'file' ? file : undefined,
        inputMode === 'text' ? inputText : undefined,
      );
      setUploaded(result);
      setProjects((items) =>
        items.map((item) =>
          item.id === selected.id ? { ...item, version: item.version + 1 } : item,
        ),
      );
      setMessage('기획서가 업로드되었습니다.');
    } catch (error) {
      setMessage(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  async function handleAnalysis() {
    if (!selected) {
      setMessage('먼저 프로젝트를 선택해 주세요.');
      return;
    }
    setBusy(true);
    setMessage('');
    try {
      const result = await startAnalysis(selected.id, selected.version);
      setAttempt(result);
      setAnalysisResult(null);
      setAnalysisResultError('');
      setMessage('분석을 시작했습니다. 상태를 확인하고 있습니다.');
    } catch (error) {
      setMessage(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="shell">
      <header className="topbar">
        <div className="brand">AgentFit</div>
        <span>{session.user.name}님</span>
      </header>
      <section className="hero">
        <p className="eyebrow">ANALYSIS WORKSPACE</p>
        <h1>
          기획서를 업로드하고
          <br />
          AI 분석을 확인하세요.
        </h1>
        <p>추천 기능 없이, 분석 파이프라인의 입력부터 결과 확인까지 집중합니다.</p>
      </section>
      <section className="grid">
        <ProjectCard
          projects={projects}
          selectedId={selectedId}
          newName={newName}
          busy={busy}
          onSelect={(projectId) => {
            setSelectedId(projectId);
            setUploaded(null);
            setAttempt(null);
            setAnalysisResult(null);
            setAnalysisResultError('');
          }}
          onNameChange={setNewName}
          onCreate={handleCreate}
        />
        <PlanningInputCard
          inputMode={inputMode}
          file={file}
          inputText={inputText}
          uploaded={uploaded}
          busy={busy}
          selected={Boolean(selected)}
          onModeChange={setInputMode}
          onFileChange={selectFile}
          onTextChange={setInputText}
          onUpload={handleUpload}
        />
        <AnalysisCard
          attempt={attempt}
          busy={busy}
          canStart={Boolean(uploaded && selected)}
          onStart={handleAnalysis}
        />
        <AnalysisResultCard
          attempt={attempt}
          project={selected}
          result={analysisResult}
          resultError={analysisResultError}
          onReload={() => selectedId && void loadAnalysisResult(selectedId)}
          onSaveReview={async (review) => {
            if (!selectedId) return;
            await saveAnalysisReview(selectedId, review);
            await loadAnalysisResult(selectedId);
          }}
        />
      </section>
      {message && (
        <div className="toast" role="status">
          {message}
        </div>
      )}
    </main>
  );
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '요청 중 문제가 발생했습니다.';
}
