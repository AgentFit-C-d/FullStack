import { describe, expect, it, vi } from 'vitest';
import { createProject, uploadPlanningInput } from './agentfitApi';
import * as client from './client';

describe('AgentFit API', () => {
  it('uploads exactly one planning input with the project version', async () => {
    const spy = vi.spyOn(client, 'apiFetch').mockResolvedValue({ revision: 1 });
    const file = new File(['# plan'], 'plan.md', { type: 'text/markdown' });
    await uploadPlanningInput('p1', 3, file);
    const [, init] = spy.mock.calls[0];
    expect(init?.method).toBe('PUT');
    expect((init?.body as FormData).get('expectedVersion')).toBe('3');
    expect((init?.body as FormData).get('file')).toBe(file);
    spy.mockRestore();
  });
  it('does not send file and text together', async () => {
    await expect(uploadPlanningInput('p1', 1, new File(['x'], 'x.md'), 'text')).rejects.toThrow('하나만');
  });
  it('creates a project from its response envelope', async () => {
    vi.spyOn(client, 'apiFetch').mockResolvedValue({ project: { id: 'p1', name: 'Demo', version: 1 } });
    await expect(createProject('Demo')).resolves.toMatchObject({ id: 'p1' });
    vi.restoreAllMocks();
  });
});
