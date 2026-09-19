import createMiddleware from 'next-intl/middleware';

export default createMiddleware({
  locales: ['nl', 'en'],
  defaultLocale: 'nl',
});

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico|images).*)'],
};
