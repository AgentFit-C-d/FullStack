import { createBrowserRouter } from 'react-router'
import { createAppRoutes } from './routes.jsx'
import { httpServiceApi } from '../features/service/serviceApi.js'

export const router = createBrowserRouter(createAppRoutes({ serviceApi: httpServiceApi }))
