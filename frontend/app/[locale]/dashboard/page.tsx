'use client';

import { useEffect, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import UserService from '@/services/UserService';
import { useAuth } from '@/context/AuthContext';
import { API_BASE, WS_BASE } from '@/lib/api';

const CAMERA_WS_URL = 'wss://camera-backend-itip-nl-14.apps.okd.ucll.cloud/ws/camera';

const KEYBOARD_HZ = 30;
const STEER_STEP = 4;
const STEER_RETURN = 6;
const THROTTLE_RAMP_UP = 6;
const THROTTLE_DECAY = 8;
const BRAKE_RAMP_UP = 10;
const PEDAL_WAKE_PCT = 8;
const PEDAL_SLEEP_PCT = 4;
const PEDAL_FULL_PCT = 96;

const pedalState: Record<'throttle' | 'brake', 'rest' | 'active'> = {
  throttle: 'rest',
  brake: 'rest',
};

function pedalPercent(rawAxis: number, which: 'throttle' | 'brake'): number {
  const clamped = Math.max(-1, Math.min(1, rawAxis));
  const pct = ((1 - clamped) / 2) * 100;
  if (pct >= PEDAL_FULL_PCT) {
    pedalState[which] = 'active';
    return 100;
  }
  if (pedalState[which] === 'rest') {
    if (pct < PEDAL_WAKE_PCT) return 0;
    pedalState[which] = 'active';
    return Math.round(pct);
  }
  if (pct < PEDAL_SLEEP_PCT) {
    pedalState[which] = 'rest';
    return 0;
  }
  return Math.round(pct);
}

interface ControllerState {
  steering: number;
  movement: number;
  connected: boolean;
}

function SteeringWheel({ value }: { value: number }) {
  const deg = (value / 100) * 180;
  return (
    <div className="relative w-56 h-56 flex items-center justify-center">
      <div className="absolute inset-0 rounded-full bg-gradient-to-br from-zinc-700 via-zinc-900 to-black blur-2xl opacity-60" />
      <div
        className="relative w-48 h-48 rounded-full border-[10px] border-zinc-700 shadow-[0_0_40px_rgba(239,68,68,0.4)] transition-transform duration-75"
        style={{ transform: `rotate(${deg}deg)` }}
      >
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-24 h-24 rounded-full bg-zinc-800 border-4 border-zinc-700 flex items-center justify-center">
          <div className="text-red-500 font-bold text-xs tracking-widest">NL-14</div>
        </div>
        <div className="absolute left-1/2 -translate-x-1/2 -top-2 w-2 h-6 bg-red-500 rounded-full shadow-[0_0_10px_rgba(239,68,68,0.8)]" />
        <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-44 h-1 bg-zinc-700" />
        <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 h-44 w-1 bg-zinc-700" />
      </div>
    </div>
  );
}

function MovementGauge({ value }: { value: number }) {
  const isAccel = value >= 0;
  const pct = Math.abs(value);
  return (
    <div className="relative w-full">
      <div className="flex justify-between mb-3 text-xs font-mono tracking-widest">
        <span className="text-red-400">◄ BRAKE</span>
        <span className="text-zinc-400">0</span>
        <span className="text-green-400">THROTTLE ►</span>
      </div>
      <div className="relative h-12 bg-zinc-900 rounded-lg border border-zinc-800 overflow-hidden shadow-inner">
        <div className="absolute inset-y-0 left-1/2 w-px bg-zinc-600 z-10" />
        {[0, 25, 50, 75].map((p) => (
          <div
            key={`l${p}`}
            className="absolute inset-y-0 w-px bg-zinc-800"
            style={{ left: `${50 - p / 2}%` }}
          />
        ))}
        {[25, 50, 75, 100].map((p) => (
          <div
            key={`r${p}`}
            className="absolute inset-y-0 w-px bg-zinc-800"
            style={{ left: `${50 + p / 2}%` }}
          />
        ))}
        {isAccel ? (
          <div
            className="absolute inset-y-0 left-1/2 bg-gradient-to-r from-green-600 to-green-400 shadow-[0_0_20px_rgba(34,197,94,0.6)] transition-all duration-75"
            style={{ width: `${pct / 2}%` }}
          />
        ) : (
          <div
            className="absolute inset-y-0 bg-gradient-to-l from-red-600 to-red-400 shadow-[0_0_20px_rgba(239,68,68,0.6)] transition-all duration-75"
            style={{ right: '50%', width: `${pct / 2}%` }}
          />
        )}
      </div>
      <div className="flex justify-center mt-4">
        <div
          className={`text-6xl font-black font-mono tracking-tight ${isAccel ? 'text-green-400' : 'text-red-400'}`}
          style={{ textShadow: `0 0 30px currentColor` }}
        >
          {value > 0 ? '+' : ''}
          {value}
        </div>
      </div>
    </div>
  );
}

// ── Camera feed component ─────────────────────────────────────────────────────
function CameraFeed() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const [camConnected, setCamConnected] = useState(false);
  const [customUrl, setCustomUrl] = useState(CAMERA_WS_URL);
  const [inputUrl, setInputUrl] = useState(CAMERA_WS_URL);
  const lastFrameRef = useRef(0);

  useEffect(() => {
    let shouldReconnect = true;
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined;

    function connect(url: string) {
      const ws = new WebSocket(url);
      ws.binaryType = 'arraybuffer';
      wsRef.current = ws;

      ws.onopen = () => setCamConnected(true);
      ws.onclose = () => {
        setCamConnected(false);
        if (wsRef.current === ws && shouldReconnect) {
          reconnectTimer = setTimeout(() => connect(url), 3000);
        }
      };
      ws.onerror = () => ws.close();
      ws.onmessage = (e) => {
        const now = Date.now();
        if (now - lastFrameRef.current < 100) return; // max 10fps
        lastFrameRef.current = now;
        const blob = new Blob([e.data], { type: 'image/jpeg' });
        const objectUrl = URL.createObjectURL(blob);
        const img = new Image();
        img.onload = () => {
          const canvas = canvasRef.current;
          if (!canvas) return;
          const ctx = canvas.getContext('2d');
          if (!ctx) return;
          canvas.width = img.width;
          canvas.height = img.height;
          ctx.drawImage(img, 0, 0);
          URL.revokeObjectURL(objectUrl);
        };
        img.onerror = () => URL.revokeObjectURL(objectUrl);
        img.src = objectUrl;
      };
    }

    connect(customUrl);

    return () => {
      shouldReconnect = false;
      clearTimeout(reconnectTimer);
      wsRef.current?.close();
      wsRef.current = null;
    };
  }, [customUrl]);

  const handleConnect = () => {
    wsRef.current?.close();
    wsRef.current = null;
    setCustomUrl(inputUrl);
  };

  return (
    <div className="mt-8 bg-zinc-950/70 backdrop-blur border border-zinc-800 rounded-2xl p-6 relative overflow-hidden">
      <div className="absolute top-0 right-0 w-48 h-48 bg-red-600 rounded-full blur-3xl opacity-5" />
      <div className="relative">
        <div className="flex items-center justify-between mb-4 flex-wrap gap-3">
          <div>
            <div className="text-xs font-mono tracking-[0.3em] text-zinc-400">LIVE FEED</div>
            <div className="text-xl font-bold">Camera</div>
          </div>
          <div
            className={`flex items-center gap-3 px-4 py-2 rounded-full border ${camConnected ? 'border-green-500/30 bg-green-500/10' : 'border-red-500/30 bg-red-500/10'}`}
          >
            <span className="relative flex w-2.5 h-2.5">
              {camConnected && (
                <span className="absolute inset-0 rounded-full bg-green-400 animate-ping opacity-75" />
              )}
              <span
                className={`relative w-2.5 h-2.5 rounded-full ${camConnected ? 'bg-green-400' : 'bg-red-400'}`}
              />
            </span>
            <span
              className={`text-xs font-mono tracking-widest font-bold ${camConnected ? 'text-green-300' : 'text-red-300'}`}
            >
              {camConnected ? 'STREAMING' : 'OFFLINE'}
            </span>
          </div>
        </div>

        <div className="relative w-full aspect-video bg-zinc-900 rounded-xl border border-zinc-800 overflow-hidden flex items-center justify-center">
          {!camConnected && (
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 text-zinc-600">
              <svg
                className="w-16 h-16 opacity-30"
                fill="none"
                stroke="currentColor"
                strokeWidth={1}
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M15 10l4.553-2.276A1 1 0 0121 8.723v6.554a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z"
                />
              </svg>
              <span className="text-xs font-mono tracking-widest">WAITING FOR STREAM</span>
            </div>
          )}
          <canvas ref={canvasRef} className="w-full h-full object-contain" />
        </div>

        <div className="mt-4 flex gap-2 flex-wrap">
          <input
            type="text"
            value={inputUrl}
            onChange={(e) => setInputUrl(e.target.value)}
            placeholder="wss://..."
            className="flex-1 min-w-0 px-4 py-2 text-xs font-mono bg-zinc-900 border border-zinc-700 rounded-lg text-zinc-200 placeholder-zinc-600 focus:outline-none focus:border-red-500/60 transition-colors"
          />
          <button
            onClick={handleConnect}
            className="px-5 py-2 text-xs font-mono tracking-widest font-bold rounded-lg border border-red-500/40 bg-red-500/10 text-red-300 hover:bg-red-500/20 hover:border-red-500/60 transition-all whitespace-nowrap"
          >
            CONNECT
          </button>
        </div>
        <div className="mt-2 text-xs font-mono text-zinc-600 truncate">{customUrl}</div>
      </div>
    </div>
  );
}
// ─────────────────────────────────────────────────────────────────────────────

export default function DashboardPage() {
  const t = useTranslations('dashboard');
  const { logout } = useAuth();
  const router = useRouter();
  const [deleteConfirm, setDeleteConfirm] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const handleDeleteAccount = async () => {
    try {
      await UserService.deleteAccount();
      logout();
      router.push('/');
    } catch (error) {
      setDeleteError((error as Error).message || t('deleteError'));
    }
  };

  const [state, setState] = useState<ControllerState>({
    steering: 0,
    movement: 0,
    connected: false,
  });
  const [scriptRunning, setScriptRunning] = useState(false);
  const [scriptMsg, setScriptMsg] = useState<string>('');
  const [keyboardMode, setKeyboardMode] = useState(false);
  const keysRef = useRef<{ [k: string]: boolean }>({});
  const socketRef = useRef<WebSocket | null>(null);
  const keyboardModeRef = useRef(keyboardMode);
  useEffect(() => {
    keyboardModeRef.current = keyboardMode;
  }, [keyboardMode]);

  const sendControllerInput = (payload: Record<string, unknown>) => {
    if (socketRef.current?.readyState === WebSocket.OPEN) {
      socketRef.current.send(JSON.stringify(payload));
    }
  };

  const refreshStatus = async () => {
    try {
      const r = await fetch(`${API_BASE}/api/controller/script/status`);
      const d = await r.json();
      setScriptRunning(d.running);
    } catch {}
  };

  const startScript = async () => {
    setScriptMsg(t('scriptStarting'));
    try {
      const r = await fetch(`${API_BASE}/api/controller/script/start`, { method: 'POST' });
      const d = await r.json();
      setScriptMsg(
        d.status === 'started'
          ? t('scriptStarted', { pid: d.pid })
          : d.status === 'already_running'
            ? t('scriptAlreadyRunning')
            : t('scriptError', { message: d.message ?? 'unknown' })
      );
      await refreshStatus();
    } catch {
      setScriptMsg(t('scriptFailed'));
    }
  };

  const stopScript = async () => {
    setScriptMsg(t('scriptStopping'));
    try {
      await fetch(`${API_BASE}/api/controller/script/stop`, { method: 'POST' });
      setScriptMsg(t('scriptStopped'));
      await refreshStatus();
    } catch {
      setScriptMsg(t('scriptFailed'));
    }
  };

  useEffect(() => {
    let cancelled = false;
    const runFetch = async () => {
      try {
        const r = await fetch(`${API_BASE}/api/controller/script/status`);
        const d = await r.json();
        if (!cancelled) setScriptRunning(d.running);
      } catch {}
    };
    runFetch();
    const id = setInterval(runFetch, 3000);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, []);

  useEffect(() => {
    if (!keyboardMode) return;
    fetch(`${API_BASE}/api/controller/session/start`, { method: 'POST' }).catch(() => {});
    const onUnload = () => {
      navigator.sendBeacon?.(`${API_BASE}/api/controller/session/stop`);
    };
    window.addEventListener('beforeunload', onUnload);
    return () => {
      window.removeEventListener('beforeunload', onUnload);
      fetch(`${API_BASE}/api/controller/session/stop`, { method: 'POST', keepalive: true }).catch(
        () => {}
      );
    };
  }, [keyboardMode]);

  useEffect(() => {
    if (!keyboardMode) return;
    const TRACKED = new Set(['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Space']);
    const down = (e: KeyboardEvent) => {
      if (!TRACKED.has(e.code)) return;
      e.preventDefault();
      if (e.repeat) return;
      keysRef.current[e.code] = true;
    };
    const up = (e: KeyboardEvent) => {
      if (!TRACKED.has(e.code)) return;
      keysRef.current[e.code] = false;
    };
    const blur = () => {
      keysRef.current = {};
    };
    window.addEventListener('keydown', down);
    window.addEventListener('keyup', up);
    window.addEventListener('blur', blur);
    return () => {
      window.removeEventListener('keydown', down);
      window.removeEventListener('keyup', up);
      window.removeEventListener('blur', blur);
      keysRef.current = {};
    };
  }, [keyboardMode]);

  useEffect(() => {
    if (!keyboardMode) return;
    let steering = 0;
    let throttle = 0;
    let brake = 0;
    const tick = () => {
      const k = keysRef.current;
      if (k['ArrowLeft']) steering = Math.max(-100, steering - STEER_STEP);
      else if (k['ArrowRight']) steering = Math.min(100, steering + STEER_STEP);
      else {
        if (steering > 0) steering = Math.max(0, steering - STEER_RETURN);
        if (steering < 0) steering = Math.min(0, steering + STEER_RETURN);
      }
      if (k['ArrowUp']) throttle = Math.min(100, throttle + THROTTLE_RAMP_UP);
      else throttle = Math.max(0, throttle - THROTTLE_DECAY);
      if (k['ArrowDown']) brake = Math.min(100, brake + BRAKE_RAMP_UP);
      else brake = Math.max(0, brake - BRAKE_RAMP_UP);
      if (k['Space']) {
        throttle = 0;
        brake = 100;
      }
      const payload = {
        axes: [steering / 100, 1 - (throttle / 100) * 2, 1 - (brake / 100) * 2],
        buttons: [],
        hats: [],
        timestamp: Date.now(),
      };
      sendControllerInput(payload);
      setState((s) => ({ ...s, steering, movement: throttle - brake }));
    };
    const id = setInterval(tick, 1000 / KEYBOARD_HZ);
    return () => clearInterval(id);
  }, [keyboardMode]);

  useEffect(() => {
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
    let shouldReconnect = true;
    function connect() {
      const socket = new WebSocket(`${WS_BASE}/ws`);
      socketRef.current = socket;
      socket.onopen = () => setState((s) => ({ ...s, connected: true }));
      socket.onmessage = (e) => {
        if (keyboardModeRef.current) return;
        try {
          const data = JSON.parse(e.data);
          const axes: number[] = data.axes ?? [];
          const steering = Math.round((axes[0] ?? 0) * 100);
          const throttle = pedalPercent(axes[1] ?? 1, 'throttle');
          const brake = pedalPercent(axes[2] ?? 1, 'brake');
          const movement = Math.round(throttle - brake);
          setState({ steering, movement, connected: true });
        } catch {}
      };
      socket.onerror = () => socket.close();
      socket.onclose = () => {
        setState((s) => ({ ...s, connected: false }));
        if (socketRef.current === socket) socketRef.current = null;
        if (shouldReconnect) reconnectTimer = setTimeout(connect, 2000);
      };
    }
    connect();
    return () => {
      shouldReconnect = false;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      socketRef.current?.close();
      socketRef.current = null;
    };
  }, []);

  return (
    <div className="min-h-screen bg-black text-white relative overflow-hidden">
      <div
        className="absolute inset-0 opacity-[0.03]"
        style={{
          backgroundImage: `linear-gradient(to right, #fff 1px, transparent 1px), linear-gradient(to bottom, #fff 1px, transparent 1px)`,
          backgroundSize: '40px 40px',
        }}
      />
      <div className="absolute top-0 left-1/4 w-96 h-96 bg-red-600 rounded-full blur-[150px] opacity-20" />
      <div className="absolute bottom-0 right-1/4 w-96 h-96 bg-green-600 rounded-full blur-[150px] opacity-10" />

      <div className="relative max-w-5xl mx-auto px-8 py-12">
        <div className="flex items-center justify-between mb-12 border-b border-zinc-800 pb-6 gap-6 flex-wrap">
          <div>
            <div className="text-xs font-mono tracking-[0.3em] text-red-500 mb-1">
              {t('telemetry')}
            </div>
            <h1 className="text-4xl font-black tracking-tight">
              {t('title')}
              <span className="text-red-500">.</span>
            </h1>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={startScript}
              disabled={scriptRunning}
              className="px-5 py-2 text-xs font-mono tracking-widest font-bold rounded-md border border-green-500/40 bg-green-500/10 text-green-300 hover:bg-green-500/20 hover:border-green-500/60 disabled:opacity-30 disabled:cursor-not-allowed transition-all"
            >
              ▶ {t('start')}
            </button>
            <button
              onClick={stopScript}
              disabled={!scriptRunning}
              className="px-5 py-2 text-xs font-mono tracking-widest font-bold rounded-md border border-red-500/40 bg-red-500/10 text-red-300 hover:bg-red-500/20 hover:border-red-500/60 disabled:opacity-30 disabled:cursor-not-allowed transition-all"
            >
              ■ {t('stop')}
            </button>
            <button
              onClick={() => setKeyboardMode((v) => !v)}
              className={`px-5 py-2 text-xs font-mono tracking-widest font-bold rounded-md border transition-all ${
                keyboardMode
                  ? 'border-indigo-400 bg-indigo-500/20 text-indigo-200 shadow-[0_0_15px_rgba(99,102,241,0.4)]'
                  : 'border-indigo-500/40 bg-indigo-500/10 text-indigo-300 hover:bg-indigo-500/20'
              }`}
            >
              ⌨ {t('keyboard')} {keyboardMode ? 'ON' : 'OFF'}
            </button>
            <div className="hidden md:block text-xs font-mono text-zinc-400 min-w-[120px]">
              {scriptMsg || (scriptRunning ? t('scriptRunning') : t('scriptNotRunning'))}
            </div>
          </div>
          <div
            className={`flex items-center gap-3 px-4 py-2 rounded-full border ${state.connected ? 'border-green-500/30 bg-green-500/10' : 'border-red-500/30 bg-red-500/10'}`}
          >
            <span className="relative flex w-2.5 h-2.5">
              {state.connected && (
                <span className="absolute inset-0 rounded-full bg-green-400 animate-ping opacity-75" />
              )}
              <span
                className={`relative w-2.5 h-2.5 rounded-full ${state.connected ? 'bg-green-400' : 'bg-red-400'}`}
              />
            </span>
            <span
              className={`text-xs font-mono tracking-widest font-bold ${state.connected ? 'text-green-300' : 'text-red-300'}`}
            >
              {state.connected ? t('online') : t('offline')}
            </span>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          <div className="bg-zinc-950/70 backdrop-blur border border-zinc-800 rounded-2xl p-8 relative overflow-hidden">
            <div className="absolute top-0 right-0 w-32 h-32 bg-red-600 rounded-full blur-3xl opacity-10" />
            <div className="relative">
              <div className="flex justify-between items-baseline mb-6">
                <div>
                  <div className="text-xs font-mono tracking-[0.3em] text-zinc-400">
                    {t('steeringAxis')}
                  </div>
                  <div className="text-xl font-bold">{t('steering')}</div>
                </div>
                <div
                  className="text-4xl font-black font-mono text-white"
                  style={{ textShadow: '0 0 20px rgba(239,68,68,0.5)' }}
                >
                  {state.steering > 0 ? '+' : ''}
                  {state.steering}
                </div>
              </div>
              <div className="flex justify-center mb-6">
                <SteeringWheel value={state.steering} />
              </div>
              <div className="relative h-2 bg-zinc-900 rounded-full overflow-hidden">
                <div className="absolute inset-y-0 left-1/2 w-px bg-zinc-600 z-10" />
                <div
                  className="absolute top-0 h-2 bg-gradient-to-r from-red-500 to-red-400 shadow-[0_0_15px_rgba(239,68,68,0.6)] transition-all duration-75"
                  style={{
                    width: `${Math.abs(state.steering) / 2}%`,
                    left: state.steering >= 0 ? '50%' : `${50 - Math.abs(state.steering) / 2}%`,
                  }}
                />
              </div>
              <div className="flex justify-between mt-2 text-xs font-mono text-zinc-400">
                <span>-100</span>
                <span>0</span>
                <span>+100</span>
              </div>
            </div>
          </div>

          <div className="bg-zinc-950/70 backdrop-blur border border-zinc-800 rounded-2xl p-8 relative overflow-hidden">
            <div className="absolute top-0 right-0 w-32 h-32 bg-green-600 rounded-full blur-3xl opacity-10" />
            <div className="relative">
              <div className="flex justify-between items-baseline mb-6">
                <div>
                  <div className="text-xs font-mono tracking-[0.3em] text-zinc-400">
                    {t('drive')}
                  </div>
                  <div className="text-xl font-bold">{t('movement')}</div>
                </div>
                <div className="text-xs font-mono text-zinc-400 self-end">
                  {state.movement >= 0 ? t('forward') : t('reverse')}
                </div>
              </div>
              <div className="py-8">
                <MovementGauge value={state.movement} />
              </div>
            </div>
          </div>
        </div>

        {keyboardMode && (
          <div className="mt-8 bg-indigo-950/30 border border-indigo-500/30 rounded-2xl p-6">
            <div className="text-xs font-mono tracking-[0.3em] text-indigo-400 mb-4">
              {t('keyboardControls')}
            </div>
            <div className="grid grid-cols-2 md:grid-cols-5 gap-4 text-sm">
              {[
                { key: '←', label: t('keyLeft') },
                { key: '→', label: t('keyRight') },
                { key: '↑', label: t('keyUp') },
                { key: '↓', label: t('keyDown') },
                { key: '␣', label: t('keySpace') },
              ].map(({ key, label }) => (
                <div key={key} className="flex items-center gap-3">
                  <kbd className="px-3 py-2 bg-zinc-900 border border-zinc-700 rounded-md font-mono text-lg min-w-[2.5rem] text-center text-white">
                    {key}
                  </kbd>
                  <span className="text-zinc-400 text-xs">{label}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="mt-8 text-center">
          <div className="inline-flex items-center gap-2 text-xs font-mono tracking-widest text-zinc-400">
            <span className="w-8 h-px bg-zinc-700" />
            <span>{t('websocketInfo')}</span>
            <span className="w-8 h-px bg-zinc-700" />
          </div>
        </div>

        {/* ── Camera Feed ── */}
        <CameraFeed />

        {/* GDPR — Right to erasure */}
        <div className="mt-10 border border-red-900/40 bg-red-950/20 rounded-2xl p-6">
          <div className="text-xs font-mono tracking-[0.3em] text-red-500 mb-1">
            GDPR · RIGHT TO ERASURE
          </div>
          <h2 className="text-base font-semibold text-white mb-1">Delete your account</h2>
          <p className="text-xs text-zinc-400 mb-4">
            Permanently deletes your account and all associated personal data (username, name,
            email). This action cannot be undone. View our{' '}
            <Link
              href="/privacy"
              className="underline underline-offset-2 text-zinc-300 hover:text-white transition-colors"
            >
              Privacy Notice
            </Link>{' '}
            for details.
          </p>
          {deleteError && <p className="text-red-400 text-xs mb-3">{deleteError}</p>}
          {!deleteConfirm ? (
            <button
              onClick={() => setDeleteConfirm(true)}
              className="px-5 py-2 text-xs font-mono tracking-widest font-bold rounded-md border border-red-700/50 bg-red-900/20 text-red-300 hover:bg-red-900/40 hover:border-red-600 transition-all"
            >
              DELETE ACCOUNT
            </button>
          ) : (
            <div className="flex items-center gap-3">
              <span className="text-xs text-zinc-400">Are you sure? This cannot be undone.</span>
              <button
                onClick={handleDeleteAccount}
                className="px-5 py-2 text-xs font-mono tracking-widest font-bold rounded-md border border-red-500 bg-red-600/30 text-red-200 hover:bg-red-600/50 transition-all"
              >
                YES, DELETE
              </button>
              <button
                onClick={() => {
                  setDeleteConfirm(false);
                  setDeleteError(null);
                }}
                className="px-5 py-2 text-xs font-mono tracking-widest font-bold rounded-md border border-zinc-700 text-zinc-400 hover:border-zinc-500 transition-all"
              >
                CANCEL
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}