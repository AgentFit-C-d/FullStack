import { describe, expect, it, vi } from 'vitest';
import { apiFetch } from './client';

describe('apiFetch', () => {
  it('includes session credentials and throws server errors', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ message: 'bad input' }), { status: 400 }));
    await expect(apiFetch('/api/projects')).rejects.toThrow('bad input');
    expect(fetchMock).toHaveBeenCalledWith('/api/projects', expect.objectContaining({ credentials: 'include' }));
    fetchMock.mockRestore();
  });
});
