import Link from 'next/link';

const PrivacyPage = () => {
  return (
    <div className="flex flex-col min-h-screen bg-zinc-950 font-sans text-white">
      <section className="flex flex-col items-center flex-1 px-4 py-24">
        <div className="w-full max-w-2xl">
          <Link
            href="/signup"
            className="text-xs tracking-[0.2em] uppercase text-zinc-400 hover:text-zinc-300 transition-colors mb-8 inline-block"
          >
            ← Back to sign up
          </Link>

          <span className="text-xs tracking-[0.25em] uppercase text-zinc-400 mb-4 block">
            Legal
          </span>
          <h1 className="text-4xl font-bold tracking-tight mb-2">Privacy Notice</h1>
          <p className="text-zinc-400 mb-10 text-sm">Last updated: 18 May 2026</p>

          <div className="flex flex-col gap-8 text-sm text-zinc-300 leading-relaxed">
            <section>
              <h2 className="text-base font-semibold text-white mb-2">
                1. Who is responsible for your data?
              </h2>
              <p>
                Team NL-14 (UCLL Integration Project) is the data controller for the personal data
                you provide when using this application. This application is developed for
                educational purposes as part of the IT Integration Project at UCLL University
                College.
              </p>
            </section>

            <section>
              <h2 className="text-base font-semibold text-white mb-2">
                2. What data do we collect?
              </h2>
              <p>When you create an account, we collect and store the following personal data:</p>
              <ul className="list-disc list-inside mt-2 space-y-1 text-zinc-400">
                <li>Username</li>
                <li>First name and last name</li>
                <li>Email address</li>
                <li>Password (stored as a secure bcrypt hash — never in plain text)</li>
                <li>Account creation timestamp</li>
              </ul>
            </section>

            <section>
              <h2 className="text-base font-semibold text-white mb-2">
                3. Why do we process your data?
              </h2>
              <p>Your data is processed solely for the following purposes:</p>
              <ul className="list-disc list-inside mt-2 space-y-1 text-zinc-400">
                <li>
                  <strong className="text-zinc-200">Account management</strong> — to create,
                  identify, and authenticate your account.
                </li>
                <li>
                  <strong className="text-zinc-200">Security</strong> — to protect the application
                  and prevent unauthorised access to ESP32 control commands.
                </li>
              </ul>
              <p className="mt-2">
                The legal basis for this processing is your explicit consent (GDPR Art. 6(1)(a)),
                given at the time of registration.
              </p>
            </section>

            <section>
              <h2 className="text-base font-semibold text-white mb-2">
                4. How long do we keep your data?
              </h2>
              <p>
                Your personal data is retained for as long as your account exists. When you delete
                your account, all associated personal data is permanently removed from our systems.
              </p>
            </section>

            <section>
              <h2 className="text-base font-semibold text-white mb-2">
                5. Who has access to your data?
              </h2>
              <p>
                Your data is stored in a PostgreSQL database hosted on the UCLL OKD cloud platform.
                It is not shared with or sold to any third parties.
              </p>
            </section>

            <section>
              <h2 className="text-base font-semibold text-white mb-2">
                6. Your rights under the GDPR
              </h2>
              <p>
                Under the General Data Protection Regulation (GDPR), you have the following rights:
              </p>
              <ul className="list-disc list-inside mt-2 space-y-1 text-zinc-400">
                <li>
                  <strong className="text-zinc-200">Right of access</strong> — you can request a
                  copy of the personal data we hold about you.
                </li>
                <li>
                  <strong className="text-zinc-200">Right to rectification</strong> — you can
                  request correction of inaccurate data.
                </li>
                <li>
                  <strong className="text-zinc-200">Right to erasure</strong> — you can permanently
                  delete your account and all associated data at any time from the dashboard.
                </li>
                <li>
                  <strong className="text-zinc-200">Right to withdraw consent</strong> — you may
                  withdraw your consent at any time by deleting your account.
                </li>
              </ul>
            </section>

            <section>
              <h2 className="text-base font-semibold text-white mb-2">
                7. Cookies and session storage
              </h2>
              <p>
                This application uses an <code className="text-zinc-400">authToken</code> HTTP-only
                cookie for session authentication. It is used exclusively to verify your identity
                and expires after 1 hour. No tracking or advertising cookies are used.
              </p>
            </section>

            <section>
              <h2 className="text-base font-semibold text-white mb-2">8. Contact</h2>
              <p>
                If you have questions about this privacy notice or wish to exercise your rights,
                contact the project team via the UCLL educational platform.
              </p>
            </section>
          </div>
        </div>
      </section>
    </div>
  );
};

export default PrivacyPage;
