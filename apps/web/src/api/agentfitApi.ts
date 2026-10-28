import { apiFetch } from './client';
import type {
  AnalysisAttempt,
  AnalysisReview,
  AnalysisResultResponse,
  PlanningInputMetadata,
  Project,
  Session,
} from './types';

export type {
  AnalysisAttempt,
  AnalysisResultResponse,
  PlanningInputMetadata,
  Project,
  Session,
} from './types';

export async function getSession(): Promise<Session | null> {
  return apiFetch<Session | null>('/api/auth/get-session');
}

export async function listProjects(): Promise<Project[]> {
  const result = await apiFetch<{ projects?: Project[] } | Project[]>('/api/projects');
  return Array.isArray(result) ? result : result.projects ?? [];
}

export async function createProject(name: string): Promise<Project> {
  const result = await apiFetch<{ project: Project }>('/api/projects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  });
  return result.project;
}

export async function uploadPlanningInput(
  projectId: string,
  expectedVersion: number,
  file?: File,
  inputText?: string,
): Promise<PlanningInputMetadata> {
  if ((file ? 1 : 0) + (inputText?.trim() ? 1 : 0) !== 1) {
    throw new Error('파일 또는 텍스트 중 하나만 입력해 주세요.');
  }

  const form = new FormData();
  form.append('expectedVersion', String(expectedVersion));
  if (file) {
    form.append('file', file);
  } else {
    form.append('inputText', inputText!.trim());
  }

  return apiFetch<PlanningInputMetadata>(
    `/api/projects/${encodeURIComponent(projectId)}/planning-input`,
    { method: 'PUT', body: form },
  );
}

export async function startAnalysis(projectId: string, expectedVersion: number): Promise<AnalysisAttempt> {
  return apiFetch<AnalysisAttempt>(`/api/projects/${encodeURIComponent(projectId)}/analysis-attempts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ expectedVersion }),
  });
}

export async function latestAnalysis(projectId: string): Promise<AnalysisAttempt> {
  return apiFetch<AnalysisAttempt>(
    `/api/projects/${encodeURIComponent(projectId)}/analysis-attempts/latest`,
  );
}

export async function latestAnalysisResult(projectId: string): Promise<AnalysisResultResponse> {
  return apiFetch<AnalysisResultResponse>(
    `/api/projects/${encodeURIComponent(projectId)}/analysis-results/latest`,
  );
}

export interface AnalysisReviewRequest {
  confirmedCandidateIds: string[];
  removedCandidateIds: string[];
  manualCandidates: Array<{ field: string; value: string }>;
}

export async function saveAnalysisReview(projectId: string, review: AnalysisReviewRequest): Promise<AnalysisReview> {
  return apiFetch<AnalysisReview>(`/api/projects/${encodeURIComponent(projectId)}/analysis-results/latest/review`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(review),
  });
}
