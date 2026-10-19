import test from 'node:test'
import assert from 'node:assert/strict'
import { comparisonTargets, providedFilesForPreview } from '../src/features/live/existingFiles.js'

test('생성된 파일만 기존 파일 비교 대상으로 선택한다', () => {
  const files = [
    { targetKey: 'claude', relativePath: 'CLAUDE.md', content: 'new' },
    { targetKey: 'readme', relativePath: 'README.md', content: 'new' },
  ]
  assert.deepEqual(comparisonTargets(files), [
    { targetKey: 'claude', relativePath: 'CLAUDE.md' },
    { targetKey: 'readme', relativePath: 'README.md' },
  ])
  assert.deepEqual(providedFilesForPreview(files, [
    { targetKey: 'claude', relativePath: 'CLAUDE.md', content: 'old' },
  ]), [{ targetKey: 'claude', relativePath: 'CLAUDE.md', content: 'old' }])
  assert.throws(() => providedFilesForPreview(files, [
    { targetKey: 'other', relativePath: 'secret.txt', content: 'x' },
  ]), /생성 대상/)
  assert.throws(() => providedFilesForPreview(files, []), /하나 이상/)
})
