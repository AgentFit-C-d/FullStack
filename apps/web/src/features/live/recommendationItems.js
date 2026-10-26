export const offeredItems = recommendation => [
  ...(recommendation?.items || []),
  ...(recommendation?.optionalItems || []),
]

export function riskReviewText(review) {
  if (review?.status !== 'REVIEWED') return '위험도·외부 인증 검토 전'
  const level = { LOW: '낮음', MEDIUM: '보통', HIGH: '높음' }[review.level] || '미확인'
  const auth = review.externalAuth === 'REQUIRED' ? '외부 인증 필요' : '외부 인증 불필요'
  return `검토 위험도: ${level} · ${auth}${review.reason ? ` · ${review.reason}` : ''}`
}
