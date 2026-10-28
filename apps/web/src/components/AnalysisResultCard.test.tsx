import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AnalysisResultCard } from './AnalysisResultCard';

describe('AnalysisResultCard', () => {
  it('shows extracted features with status and source evidence', () => {
    render(
      <AnalysisResultCard
        attempt={{ id: 1, projectId: 'p1', state: 'SUCCEEDED' }}
        project={{ id: 'p1', name: 'Demo', version: 2 }}
        result={{
          attemptId: 1,
          requestId: 'r1',
          generation: 1,
          contractVersion: '1.0',
          createdAt: '2026-10-13T00:01:00Z',
          result: {
            profile: { features: ['프로젝트 분석'] },
            fieldStates: { features: 'suggested' },
            candidates: [{
              id: 'c1',
              version: 1,
              field: 'features',
              proposedValue: ['프로젝트 분석'],
              status: 'suggested',
              evidence: [{ documentId: '1', page: 2, quote: '사용자는 프로젝트 분석 기능을 이용한다.' }],
            }, {
              id: 'c2', version: 1, field: 'backend', proposedValue: ['Spring Boot'], status: 'suggested', evidence: [{ documentId: '1', page: 2, quote: '백엔드는 Spring Boot로 구현한다.' }],
            }],
            questions: [],
            safeWarnings: [],
            modelMetadata: { provider: 'upstage', model: 'information-extract', latencyMs: 10, promptVersion: 'test', inputFormat: 'original-pdf' },
          },
        }}
      />,
    );

    expect(screen.getByText('프로젝트 분석')).toBeInTheDocument();
    expect(screen.getByText('백엔드')).toBeInTheDocument();
    expect(screen.getByText('Spring Boot')).toBeInTheDocument();
    expect(screen.getAllByText('AI 근거 있음')).toHaveLength(2);
    expect(screen.queryByText(/PDF 2페이지/)).not.toBeInTheDocument();

    const featureSection = screen.getByRole('heading', { name: '프로젝트에 필요한 기능' }).parentElement!;
    fireEvent.click(within(featureSection).getByRole('button', { name: '근거 더보기' }));
    expect(screen.getByText(/PDF 2페이지/)).toBeInTheDocument();
    expect(screen.queryByText(/문서 1/)).not.toBeInTheDocument();
    expect(screen.getByText('“사용자는 프로젝트 분석 기능을 이용한다.”')).toBeInTheDocument();
  });

});
