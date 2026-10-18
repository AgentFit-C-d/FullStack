import { Outlet } from 'react-router'
import DemoProvider from '../features/projects/DemoProvider.jsx'

export function DemoRoutes() {
  return <DemoProvider><Outlet /></DemoProvider>
}
export default function App() {
  return <Outlet />
}
