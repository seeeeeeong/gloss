import type { NextConfig } from 'next'
import { SERVER_URL } from './src/lib/server-url'

// One origin, so the session cookie is first-party. Caddy does this in a deployment.
const API_PATHS = ['/api/:path*']

const nextConfig: NextConfig = {
  output: 'standalone',
  // Next denies hot reload to 127.0.0.1 otherwise.
  allowedDevOrigins: ['127.0.0.1'],
  async rewrites() {
    return API_PATHS.map((source) => ({ source, destination: `${SERVER_URL}${source}` }))
  },
}

export default nextConfig
