import { getRequestConfig } from 'next-intl/server';

export default getRequestConfig(async ({ requestLocale }) => {
  const locale = (await requestLocale) || '';

  return {
    messages: (await import(`../public/locale/${locale}/common.json`)).default,
    locale,
  };
});
