import Link from 'next/link';
import { useTranslations } from 'next-intl';

export default function Home() {
  const t = useTranslations('home');

  return (
    <div className="flex flex-col min-h-screen bg-zinc-950 font-sans text-white">
      <section className="flex flex-col justify-center px-16 gap-6 min-h-[600px]">
        <span className="text-xs tracking-[0.25em] uppercase text-zinc-400">
          {t('hero.label')}
        </span>
        <h1 className="text-6xl xl:text-7xl font-bold tracking-tight leading-none">
          {t('hero.title')}
          <br />
          <span className="text-zinc-400 font-light">{t('hero.subtitle')}</span>
        </h1>
        <p className="text-zinc-400 text-lg max-w-sm leading-relaxed">{t('hero.description')}</p>
        <p className="text-zinc-400 text-sm max-w-xs">{t('hero.subtext')}</p>
        <Link
          href="#about"
          className="w-fit mt-2 px-8 py-3 rounded-full border border-zinc-700 hover:border-zinc-400 hover:text-white text-zinc-400 text-sm tracking-wide transition-all duration-200"
        >
          {t('hero.cta')}
        </Link>
      </section>

      <section id="about" className="grid grid-cols-2 min-h-[60vh] border-t border-zinc-800">
        <div className="flex flex-col justify-center px-16 py-24 gap-4 border-r border-zinc-800">
          <span className="text-xs tracking-[0.25em] uppercase text-zinc-400">
            {t('about.label')}
          </span>
          <h2 className="text-4xl font-semibold tracking-tight">{t('about.title')}</h2>
          <p className="text-zinc-400 leading-relaxed">{t('about.description')}</p>
          <p className="text-zinc-400 text-sm leading-relaxed">{t('about.subtext')}</p>
        </div>
        <div className="flex flex-col justify-center px-16 py-24 gap-4">
          <span className="text-xs tracking-[0.25em] uppercase text-zinc-400">
            {t('project.label')}
          </span>
          <h2 className="text-4xl font-semibold tracking-tight">{t('project.title')}</h2>
          <p className="text-zinc-400 leading-relaxed">{t('project.description')}</p>
          <p className="text-zinc-400 text-sm leading-relaxed">{t('project.subtext')}</p>
        </div>
      </section>

      <section className="grid grid-cols-2 min-h-[60vh] border-t border-zinc-800">
        <div className="flex flex-col justify-center px-16 py-24 gap-4 border-r border-zinc-800">
          <span className="text-xs tracking-[0.25em] uppercase text-zinc-400">
            {t('team.label')}
          </span>
          <h2 className="text-4xl font-semibold tracking-tight">{t('team.title')}</h2>
          <p className="text-zinc-400 leading-relaxed">{t('team.description')}</p>
        </div>
      </section>
    </div>
  );
}
