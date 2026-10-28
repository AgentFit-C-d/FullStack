import { describe, expect, it } from 'vitest';
import { formatAnalysisDate, getAnalysisStateLabel } from './analysis';

describe('analysis display helpers', () => {
  it('translates analysis states into user-facing Korean labels', () => {
    expect(getAnalysisStateLabel('PROCESSING')).toBe('분석 진행 중');
    expect(getAnalysisStateLabel('SUCCEEDED')).toBe('분석 완료');
    expect(getAnalysisStateLabel('UNKNOWN')).toBe('UNKNOWN');
  });

  it('formats an analysis timestamp and handles an empty timestamp', () => {
    expect(formatAnalysisDate()).toBe('—');
    expect(formatAnalysisDate('2026-10-27T00:00:00.000Z')).toContain('2026');
  });
});
