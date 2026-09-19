'use client';

import classNames from 'classnames';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import React, { useState } from 'react';
import UserService from '@/services/UserService';
import { StatusMessage } from '@/types';
import { useAuth } from '@/context/AuthContext';
import PrivacyNotice from '@/components/privacy/PrivacyNotice';
import { useTranslations } from 'next-intl';

const UserSignupForm: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [privacyConsent, setPrivacyConsent] = useState(false);

  const [usernameError, setUsernameError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [firstNameError, setFirstNameError] = useState<string | null>(null);
  const [lastNameError, setLastNameError] = useState<string | null>(null);
  const [emailError, setEmailError] = useState<string | null>(null);
  const [privacyConsentError, setPrivacyConsentError] = useState(false);
  const [consentGiven, setConsentGiven] = useState(false);
  const [consentError, setConsentError] = useState<string | null>(null);
  const [statusMessages, setStatusMessages] = useState<StatusMessage[]>([]);

  const { login } = useAuth();
  const router = useRouter();
  const t = useTranslations('auth.signup');

  const clearErrors = () => {
    setUsernameError(null);
    setPasswordError(null);
    setFirstNameError(null);
    setLastNameError(null);
    setEmailError(null);
    setPrivacyConsentError(false);
    setConsentError(null);
    setStatusMessages([]);
  };

  const validate = (): boolean => {
    let result = true;

    if (!username || username.trim() === '') {
      setUsernameError(t('usernameRequired'));
      result = false;
    }

    if (!password || password.trim() === '') {
      setPasswordError(t('passwordRequired'));
      result = false;
    } else if (password.length < 8) {
      setPasswordError(t('passwordTooShort'));
      result = false;
    }

    if (!firstName || firstName.trim() === '') {
      setFirstNameError(t('firstNameRequired'));
      result = false;
    }

    if (!lastName || lastName.trim() === '') {
      setLastNameError(t('lastNameRequired'));
      result = false;
    }

    if (!email || email.trim() === '') {
      setEmailError(t('emailRequired'));
      result = false;
    } else if (!email.includes('@') || !email.includes('.')) {
      setEmailError(t('emailInvalid'));
      result = false;
    }

    if (!consentGiven) {
      setConsentError(t('consentRequired'));
      result = false;
    }

    if (!privacyConsent) {
      setPrivacyConsentError(true);
      result = false;
    }

    return result;
  };

  const handleSubmit = async (event: { preventDefault: () => void }) => {
    event.preventDefault();
    clearErrors();

    if (!validate()) return;

    try {
      await UserService.signup({ username, password, firstName, lastName, email, privacyConsent });
      const loggedInUser = await UserService.authenticate({ username, password });

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
            htmlFor="usernameInput"
            className="block mb-2 text-xs tracking-[0.2em] uppercase text-zinc-400"
          >
            {t('username')}
          </label>
          <input
            id="usernameInput"
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            aria-required="true"
            aria-invalid={!!usernameError}
            aria-describedby={usernameError ? 'usernameInput-error' : undefined}
            className="w-full bg-zinc-900 border border-zinc-800 text-white text-sm rounded-lg focus:outline-none focus:border-zinc-500 p-3 transition-colors"
          />
          {usernameError && (
            <div id="usernameInput-error" className="text-red-400 text-xs mt-1">
              {usernameError}
            </div>
          )}
        </div>

        <div>
          <label
            htmlFor="firstNameInput"
            className="block mb-2 text-xs tracking-[0.2em] uppercase text-zinc-400"
          >
            {t('firstName')}
          </label>
          <input
            id="firstNameInput"
            type="text"
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
            aria-required="true"
            aria-invalid={!!firstNameError}
            aria-describedby={firstNameError ? 'firstNameInput-error' : undefined}
            className="w-full bg-zinc-900 border border-zinc-800 text-white text-sm rounded-lg focus:outline-none focus:border-zinc-500 p-3 transition-colors"
          />
          {firstNameError && (
            <div id="firstNameInput-error" className="text-red-400 text-xs mt-1">
              {firstNameError}
            </div>
          )}
        </div>

        <div>
          <label
            htmlFor="lastNameInput"
            className="block mb-2 text-xs tracking-[0.2em] uppercase text-zinc-400"
          >
            {t('lastName')}
          </label>
          <input
            id="lastNameInput"
            type="text"
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
            aria-required="true"
            aria-invalid={!!lastNameError}
            aria-describedby={lastNameError ? 'lastNameInput-error' : undefined}
            className="w-full bg-zinc-900 border border-zinc-800 text-white text-sm rounded-lg focus:outline-none focus:border-zinc-500 p-3 transition-colors"
          />
          {lastNameError && (
            <div id="lastNameInput-error" className="text-red-400 text-xs mt-1">
              {lastNameError}
            </div>
          )}
        </div>

        <div>
          <label
            htmlFor="emailInput"
            className="block mb-2 text-xs tracking-[0.2em] uppercase text-zinc-400"
          >
            {t('email')}
          </label>
          <input
            id="emailInput"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-required="true"
            aria-invalid={!!emailError}
            aria-describedby={emailError ? 'emailInput-error' : undefined}
            className="w-full bg-zinc-900 border border-zinc-800 text-white text-sm rounded-lg focus:outline-none focus:border-zinc-500 p-3 transition-colors"
          />
          {emailError && (
            <div id="emailInput-error" className="text-red-400 text-xs mt-1">
              {emailError}
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
            onChange={(e) => setPassword(e.target.value)}
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

        <div className="flex items-start gap-3">
          <input
            id="consentCheckbox"
            type="checkbox"
            checked={consentGiven}
            onChange={(e) => setConsentGiven(e.target.checked)}
            aria-required="true"
            aria-invalid={!!consentError}
            aria-describedby={consentError ? 'consent-error' : undefined}
            className="mt-0.5 w-4 h-4 accent-white shrink-0 cursor-pointer"
          />
          <label
            htmlFor="consentCheckbox"
            className="text-xs text-zinc-400 leading-relaxed cursor-pointer"
          >
            I have read and agree to the{' '}
            <Link
              href="/privacy"
              target="_blank"
              className="text-white underline underline-offset-2 hover:text-zinc-300 transition-colors"
            >
              Privacy Notice
            </Link>{' '}
            and consent to the processing of my personal data for the purpose of account creation
            and authentication.
          </label>
        </div>

        <div className="mt-2">
          <PrivacyNotice
            onConsentChange={setPrivacyConsent}
            hasError={privacyConsentError}
            errorMessage={t('privacyConsentRequired')}
          />
        </div>

        {consentError && (
          <div id="consent-error" className="text-red-400 text-xs -mt-3">
            {consentError}
          </div>
        )}

        <button
          type="submit"
          className="mt-2 px-8 py-3 rounded-full border border-zinc-700 hover:border-zinc-400 hover:text-white text-zinc-400 text-sm tracking-wide transition-all duration-200"
        >
          {t('submit')}
        </button>
      </form>
    </div>
  );
};

export default UserSignupForm;
