const API_HOST = (process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:3000').replace(/\/$/, '');

export const API_BASE = `${API_HOST}/v1`;

export const WS_BASE = (process.env.NEXT_PUBLIC_WS_URL ?? API_HOST.replace(/^http/, 'ws')).replace(
  /\/$/,
  ''
);
