import { defineConfig } from 'vitest/config';
import { loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  return {
    plugins: [react()],
    build: {
      rollupOptions: { input: { service: 'index.html', analysis: 'analysis.html' } },
    },
    server: {
      host: '0.0.0.0',
      proxy: { '/api': { target: env.VITE_CORE_API_URL || 'http://localhost:8080', changeOrigin: true } },
    },
    test: {
      include: ['src/**/*.test.{ts,tsx}'],
      environment: 'jsdom',
      setupFiles: ['./src/testSetup.ts'],
    },
  };
});
