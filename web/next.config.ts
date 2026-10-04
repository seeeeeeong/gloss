import type { NextConfig } from 'next'

const SERVER_URL = process.env.GLOSS_SERVER_URL ?? 'http://127.0.0.1:8140'

/**
 * The browser sends api requests to the web's own origin. In a deployment Caddy routes
 * them to the server before they reach the web; locally this rewrite does the same, so
 * both run on one origin and the session cookie is first-party.
 */
const API_PATHS = ['/api/:path*']

const nextConfig: NextConfig = {
  // The image carries the traced server alone, not node_modules.
  output: 'standalone',
  // The dev server is opened as 127.0.0.1 (process-compose, Playwright), which
  // Next treats as another origin and denies hot reload to otherwise.
  allowedDevOrigins: ['127.0.0.1'],
  async rewrites() {
    return API_PATHS.map((source) => ({ source, destination: `${SERVER_URL}${source}` }))
  },
}

export default nextConfig
