import type { NextConfig } from 'next'
import { SERVER_URL } from './src/lib/server-url'

// One origin, so the session cookie is first-party. Caddy does this in a deployment.
const API_PATHS = ['/api/:path*']

const nextConfig: NextConfig = {
  output: 'standalone',
  // Next denies hot reload to 127.0.0.1 otherwise.
  allowedDevOrigins: ['127.0.0.1'],
  // A post was its own page before the reader; an old link opens the reader at its item.
  async redirects() {
    return [{ source: '/books/:bookId/post', destination: '/books/:bookId', permanent: false }]
  },
  async rewrites() {
    return API_PATHS.map((source) => ({ source, destination: `${SERVER_URL}${source}` }))
  },
}

export default nextConfig
