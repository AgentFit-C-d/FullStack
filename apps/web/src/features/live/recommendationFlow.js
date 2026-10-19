import { apiRequest } from './api.js'

export async function createAndReadRecommendation(base, expectedVersions, request = apiRequest) {
  await request(`${base}/recommendations`, {
    method: 'POST', body: { expectedVersions },
  })
  return request(`${base}/recommendations/latest`)
}
