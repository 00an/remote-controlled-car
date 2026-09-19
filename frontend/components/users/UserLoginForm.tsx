'use client';

import classNames from 'classnames';
import { useRouter } from 'next/navigation';
import React, { useState } from 'react';
import UserService from '@/services/UserService';
import { StatusMessage } from '@/types';
import { useAuth } from '@/context/AuthContext';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

const UserLoginForm: React.FC = () => {
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [nameError, setNameError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [statusMessages, setStatusMessages] = useState<StatusMessage[]>([]);
  const { login } = useAuth();
  const router = useRouter();
  const t = useTranslations('auth.login');

  const clearErrors = () => {
    setNameError(null);
    setPasswordError(null);
    setStatusMessages([]);
  };

  const validate = (): boolean => {
    let result = true;

    if (!name || name.trim() === '') {
      setNameError(t('usernameRequired'));
      result = false;
    }

    if (!password || password.trim() === '') {
      setPasswordError(t('passwordRequired'));
      result = false;
    }

    return result;
  };

  const handleSubmit = async (event: { preventDefault: () => void }) => {
    event.preventDefault();
    clearErrors();

    if (!validate()) {
      return;
    }

    const user = { username: name, password };

    try {
      const loggedInUser = await UserService.authenticate(user);
      setStatusMessages([{ message: t('success'), type: 'success' }]);
      login(loggedInUser);

      setTimeout(() => {
        router.push('/');
      }, 2000);
    } catch (error) {
      setStatusMessages([
        {
          message: (error as Error).message || t('unknownError'),
          type: 'error',
        },
      ]);
    }
  };

  return (
    <div className="w-full max-w-sm">
      {statusMessages && (
        <ul className="mb-6 list-none" role="status" aria-live="polite">
          {statusMessages.map(({ message, type }, index) => (
            <li
              key={index}
              className={classNames({
                'text-red-400': type === 'error',
                'text-green-400': type === 'success',
              })}
            >
              {message}
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        <div>
          <label
            htmlFor="nameInput"
            className="block mb-2 text-xs tracking-[0.2em] uppercase text-zinc-400"
          >
            {t('username')}
          </label>

          <input
            id="nameInput"
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            aria-required="true"
            aria-invalid={!!nameError}
            aria-describedby={nameError ? 'nameInput-error' : undefined}
            className="w-full bg-zinc-900 border border-zinc-800 text-white text-sm rounded-lg focus:outline-none focus:border-zinc-500 p-3 transition-colors"
          />

          {nameError && (
            <div id="nameInput-error" className="text-red-400 text-xs mt-1">
              {nameError}
            </div>
          )}
        </div>

        <div>
          <label
            htmlFor="passwordInput"
            className="block mb-2 text-xs tracking-[0.2em] uppercase text-zinc-400"
          >
            {t('password')}
          </label>

          <input
            id="passwordInput"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-required="true"
            aria-invalid={!!passwordError}
            aria-describedby={passwordError ? 'passwordInput-error' : undefined}
            className="w-full bg-zinc-900 border border-zinc-800 text-white text-sm rounded-lg focus:outline-none focus:border-zinc-500 p-3 transition-colors"
          />

          {passwordError && (
            <div id="passwordInput-error" className="text-red-400 text-xs mt-1">
              {passwordError}
            </div>
          )}
        </div>

        <button
          type="submit"
          className="mt-2 px-8 py-3 rounded-full border border-zinc-700 hover:border-zinc-400 hover:text-white text-zinc-400 text-sm tracking-wide transition-all duration-200"
        >
          {t('submit')}
        </button>
      </form>

      <p className="text-zinc-400 text-sm text-center mt-6">
        {t('noAccount')}{' '}
        <Link
          href="/signup"
          className="text-zinc-300 underline underline-offset-2 hover:text-white transition-colors"
        >
          {t('createAccount')}
        </Link>
      </p>
    </div>
  );
};

export default UserLoginForm;
