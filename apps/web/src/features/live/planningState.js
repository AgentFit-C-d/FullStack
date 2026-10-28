export function hasUnanalyzedPlanningInput(detail) {
  // Core A currently places pre-analysis input metadata in `confirmed`.
  const input = detail?.confirmed?.kind === 'CONFIRMED' ? null : detail?.confirmed || detail?.draft
  return Boolean(
    detail && !detail.latestAttempt &&
    Number.isInteger(input?.revision) && input.revision > 0 &&
    ['TEXT', 'PDF', 'MARKDOWN'].includes(input.inputType),
  )
}
