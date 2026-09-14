import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist-admin-deploy',
    emptyOutDir: true,
    rollupOptions: {
      input: path.resolve(__dirname, 'src/admin-main.tsx'),
      output: {
        entryFileNames: 'assets/admin-zone-name-modal.js',
        chunkFileNames: 'assets/chunk-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
      },
    },
  },
})
