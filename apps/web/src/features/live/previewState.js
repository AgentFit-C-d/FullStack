export function previewIsCurrent(previewRequest, recommendation, preview, now = Date.now()) {
  const expiresAt = Date.parse(preview?.expiresAt)
  return !!previewRequest?.recommendationId
    && previewRequest.recommendationId === recommendation?.recommendationId
    && recommendation.validity === 'CURRENT'
    && Number.isFinite(expiresAt) && expiresAt > now
}
