import { Outlet } from 'react-router'
import DemoProvider from '../features/projects/DemoProvider.jsx'

export default function App() {
  return <DemoProvider><Outlet /></DemoProvider>
}
