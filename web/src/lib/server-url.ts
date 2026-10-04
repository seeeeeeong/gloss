/** Where the Spring server listens; the browser never calls it directly. */
export const SERVER_URL = process.env.GLOSS_SERVER_URL ?? 'http://127.0.0.1:8140'
