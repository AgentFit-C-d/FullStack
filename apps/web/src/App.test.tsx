import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from './App';
import * as api from './api/agentfitApi';

describe('analysis workflow', () => {
  it('shows the GitHub login action when no session exists', async () => {
    vi.spyOn(api, 'getSession').mockResolvedValue(null);
    render(<App />);
    expect(await screen.findByRole('link', { name: /github로 로그인/i })).toBeInTheDocument();
  });

  it('reloads the selected project analysis instead of leaving the saved review only in screen state', async () => {
    vi.spyOn(api, 'getSession').mockResolvedValue({ user: { id: 1, name: 'Tester' } });
    vi.spyOn(api, 'listProjects').mockResolvedValue([
      { id: 'p1', name: '첫 프로젝트', version: 1 },
      { id: 'p2', name: '두 번째 프로젝트', version: 1 },
    ]);
    const latestAnalysis = vi.spyOn(api, 'latestAnalysis').mockResolvedValue({ id: 2, projectId: 'p2', state: 'SUCCEEDED' });
    vi.spyOn(api, 'latestAnalysisResult').mockResolvedValue({
      attemptId: 2,
      requestId: 'request-2',
      generation: 1,
      contractVersion: '1.0',
      createdAt: '2026-10-28T00:00:00Z',
      result: {
        profile: {}, fieldStates: {}, candidates: [], questions: [], safeWarnings: [],
        modelMetadata: { provider: 'upstage', model: 'information-extract', latencyMs: 1, promptVersion: 'test', inputFormat: 'text' },
      },
    });

    render(<App />);
    const selector = await screen.findByRole('combobox');
    fireEvent.change(selector, { target: { value: 'p2' } });

    await waitFor(() => expect(latestAnalysis).toHaveBeenCalledWith('p2'));
  });
});
