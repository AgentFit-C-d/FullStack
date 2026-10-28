import { useReducer } from 'react'
import { DemoContext } from './DemoContext.js'
import { demoReducer, initialDemoState } from './projectModel.js'

export default function DemoProvider({ children }) {
  const [state, dispatch] = useReducer(demoReducer, initialDemoState)
  return <DemoContext.Provider value={{ state, dispatch }}>{children}</DemoContext.Provider>
}
