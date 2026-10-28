export interface Session {
  user: {
    id: number;
    name: string;
  };
  session?: {
    expiresAt?: string;
  };
}

export interface Project {
  id: string;
  name: string;
  version: number;
  currentInputRevision?: number | null;
  stage?: string;
}

export interface PlanningInputMetadata {
  revision: number;
  inputType: string;
  fileName?: string | null;
  mediaType?: string | null;
  byteSize?: number | null;
  characterCount?: number | null;
  pageCount?: number | null;
}

export interface AnalysisAttempt {
  id: number;
  projectId: string;
  state: string;
  deadlineAt?: string;
  startedAt?: string;
  errorCode?: string | null;
}

export type AnalysisState = 'suggested' | 'unknown' | 'unresolved';

export interface AnalysisEvidence { documentId: string; start?: number; end?: number; page?: number; quote?: string; }

export interface AnalysisCandidate {
  id: string;
  version: number;
  field: string | null;
  proposedValue: string | string[] | null;
  status: AnalysisState | 'confirmed';
  source?: 'AI' | 'USER_INPUT';
  evidence: AnalysisEvidence[];
  usages?: Array<{ value: string; purpose: string; evidence: AnalysisEvidence[] }>;
}

export interface AnalysisReview {
  confirmedCandidateIds: string[];
  removedCandidateIds: string[];
  manualCandidates: AnalysisCandidate[];
  updatedAt?: string;
}

export interface AnalysisResultPayload {
  profile: Record<string, unknown>;
  fieldStates: Record<string, AnalysisState>;
  candidates: AnalysisCandidate[];
  humanReview?: AnalysisReview;
  questions: Array<{ id: string; field: string | null; message: string; itemIds: string[]; required: boolean }>;
  safeWarnings: string[];
  modelMetadata: { provider: string; model: string; latencyMs: number; promptVersion: string; inputFormat: string; secondaryReview?: { provider: string; model: string; status: string; targetCount: number; acceptedCount: number; latencyMs: number } };
}

export interface AnalysisResultResponse { attemptId: number; requestId: string; generation: number; contractVersion: string; result: AnalysisResultPayload; createdAt: string; }
