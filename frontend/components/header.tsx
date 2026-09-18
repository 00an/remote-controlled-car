'use client';
import Link from 'next/link';
import React from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { useTranslations, useLocale } from 'next-intl';

export default function Header() {
  const router = useRouter();
  const pathname = usePathname();
  const { user, logout } = useAuth();
  const t = useTranslations('header');
  const locale = useLocale();

  const handleLogout = async (event: React.MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    await logout();
    router.push('/');
  };

  const switchLanguage = (newLocale: string) => {
    const pathWithoutLocale = pathname.replace(`/${locale}`, '') || '/';
    router.push(`/${newLocale}${pathWithoutLocale}`);
  };

  return (
    <header className="flex items-center justify-between px-10 h-20 bg-zinc-950 border-b border-zinc-800">
      <div className="flex items-center gap-2.5">
        <div className="w-7 h-7 rounded-full bg-gradient-to-br from-rose-600 to-orange-500 flex items-center justify-center">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            aria-hidden="true"
            className="w-4 h-4 text-white"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <circle cx="12" cy="12" r="10" />
            <circle cx="12" cy="12" r="3" />
            <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
          </svg>
        </div>
        <span className="text-base font-medium text-white tracking-wide">Remote Controlled Car</span>
      </div>
      <nav className="flex items-center gap-1">
        <Link
          href="/"
          className="px-4 py-2 text-base text-zinc-400 hover:text-white hover:bg-zinc-800 rounded-md transition-all"
        >
          {t('home')}
        </Link>
        <Link
          href="#about"
          className="px-4 py-2 text-base text-zinc-400 hover:text-white hover:bg-zinc-800 rounded-md transition-all"
        >
          {t('about')}
        </Link>
        <Link
          href="/dashboard"
          className="px-4 py-2 text-base text-zinc-400 hover:text-white hover:bg-zinc-800 rounded-md transition-all"
        >
          {t('dashboard')}
        </Link>
        {user && (
          <Link
            href="/rides"
            className="px-4 py-2 text-base text-zinc-400 hover:text-white hover:bg-zinc-800 rounded-md transition-all"
          >
            {t('rides')}
          </Link>
        )}
        <div className="w-px h-5 bg-zinc-800 mx-3" />
        <select
          value={locale}
          onChange={(e) => switchLanguage(e.target.value)}
          aria-label={t('language')}
          className="bg-zinc-950 text-zinc-400 hover:text-white border border-zinc-800 hover:border-zinc-600 rounded-md px-3 py-1.5 text-sm transition-all mr-2 cursor-pointer"
        >
          <option value="en">EN</option>
          <option value="nl">NL</option>
        </select>
        <div className="w-px h-5 bg-zinc-800 mx-3" />
        {!user && (
          <Link
            href="/login"
            className="px-4 py-2 text-base font-medium text-white bg-rose-600 hover:bg-rose-500 rounded-md transition-all"
          >
            {t('signin')}
          </Link>
        )}
        {user && (
          <span className="text-base text-zinc-400 mr-2">
            {t('welcome')} <span className="text-zinc-200 font-medium">{user?.username}</span>
          </span>
        )}
        {user && (
          <button
            onClick={handleLogout}
            className="px-4 py-2 text-base font-medium text-red-300 border border-zinc-800 hover:border-red-900 hover:bg-red-950 rounded-md transition-all"
          >
            {t('logout')}
          </button>
        )}
      </nav>
    </header>
  );
}
