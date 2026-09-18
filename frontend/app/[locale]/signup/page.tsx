'use client';
import UserSignupForm from '@/components/users/UserSignupForm';
import { useTranslations } from 'next-intl';

const SignupPage = () => {
  const t = useTranslations('auth.signup');

  return (
    <div className="flex flex-col min-h-screen bg-zinc-950 font-sans text-white">
      <section className="flex flex-col items-center justify-center flex-1 px-4 py-24">
        <span className="text-xs tracking-[0.25em] uppercase text-zinc-400 mb-4">
          {t('title').includes('Account') ? 'Get started' : t('title')}
        </span>

        <h1 className="text-5xl font-bold tracking-tight mb-2">{t('title')}</h1>

        <p className="text-zinc-400 mb-12 text-sm">{t('description')}</p>

        <UserSignupForm />
      </section>
    </div>
  );
};

export default SignupPage;
