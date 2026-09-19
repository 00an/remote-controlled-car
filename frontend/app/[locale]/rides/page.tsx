'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import Link from 'next/link';
import RideService from '@/services/RideService';
import { Ride } from '@/types';

function formatDuration(seconds: number): string {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
}

function Stat({
  label,
  value,
  unit,
  color,
}: {
  label: string;
  value: string;
  unit?: string;
  color: string;
}) {
  return (
    <div className="text-right">
      <div className="text-[10px] font-mono tracking-[0.2em] text-zinc-400 uppercase">{label}</div>
      <div className={`text-2xl font-black font-mono ${color}`}>
        {value}
        {unit && <span className="text-xs text-zinc-400 ml-1">{unit}</span>}
      </div>
    </div>
  );
}

export default function RidesPage() {
  const t = useTranslations('rides');
  const [rides, setRides] = useState<Ride[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    RideService.getRideHistory()
      .then(setRides)
      .catch(() => setError(true))
      .finally(() => setLoading(false));
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

      <div className="relative max-w-3xl mx-auto px-8 py-12">
        <div className="mb-10 border-b border-zinc-800 pb-6">
          <div className="text-xs font-mono tracking-[0.3em] text-red-500 mb-1">{t('label')}</div>
          <h1 className="text-4xl font-black tracking-tight">
            {t('title')}
            <span className="text-red-500">.</span>
          </h1>
          <p className="text-sm text-zinc-400 mt-1">{t('subtitle')}</p>
        </div>

        {loading && <div className="text-sm font-mono text-zinc-400">{t('loading')}</div>}

        {!loading && error && (
          <div className="border border-red-900/40 bg-red-950/20 rounded-2xl p-6">
            <p className="text-sm text-red-300 mb-3">{t('error')}</p>
            <Link
              href="/login"
              className="text-xs font-mono tracking-widest text-zinc-300 underline underline-offset-4 hover:text-white transition-colors"
            >
              {t('loginPrompt')}
            </Link>
          </div>
        )}

        {!loading && !error && rides.length === 0 && (
          <div className="text-sm text-zinc-400 border border-zinc-800 rounded-2xl p-8 text-center">
            {t('empty')}
          </div>
        )}

        {!loading && !error && rides.length > 0 && (
          <div className="space-y-4">
            {rides.map((ride, index) => (
              <div
                key={ride.id}
                className="bg-zinc-950/70 backdrop-blur border border-zinc-800 rounded-2xl p-6 flex items-center justify-between gap-6 flex-wrap"
              >
                <div className="text-xs font-mono tracking-[0.3em] text-zinc-400">
                  {t('rideLabel')} #{index + 1}
                </div>
                <div className="flex gap-8">
                  <Stat
                    label={t('topSpeed')}
                    value={`${ride.topspeed}`}
                    unit="km/h"
                    color="text-red-400"
                  />
                  <Stat
                    label={t('avgSpeed')}
                    value={`${ride.averagespeed}`}
                    unit="km/h"
                    color="text-orange-400"
                  />
                  <Stat
                    label={t('duration')}
                    value={formatDuration(ride.timespent)}
                    color="text-green-400"
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
