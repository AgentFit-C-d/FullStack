import { createContext, useContext } from 'react'

export const DemoContext = createContext(null)

export function useDemoProjects() {
  const context = useContext(DemoContext)
  if (!context) throw new Error('DemoProvider가 필요합니다.')
  return context
}
