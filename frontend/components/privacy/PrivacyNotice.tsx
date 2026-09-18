'use client';

import React, { useState } from 'react';

interface PrivacyNoticeProps {
  onConsentChange: (consented: boolean) => void;
  hasError?: boolean;
  errorMessage?: string;
}

const PrivacyNotice: React.FC<PrivacyNoticeProps> = ({
  onConsentChange,
  hasError = false,
  errorMessage,
}) => {
  const [consentGiven, setConsentGiven] = useState(false);

  const handleConsentChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const checked = e.target.checked;
    setConsentGiven(checked);
    onConsentChange(checked);
  };

  return (
    <div className="w-full bg-zinc-900 border border-zinc-800 rounded-lg p-4">
      {/* Privacy Notice Summary */}
      <div className="mb-4">
        <h3 className="text-sm font-semibold text-white mb-2">Data Protection & Privacy</h3>
        <p className="text-xs text-zinc-400 leading-relaxed">
          By creating an account, you agree that we will collect and process your personal data
          (username, email, first name, and last name) to provide and improve our services. Your
          data is protected and used only as described in our privacy policy.
        </p>
      </div>

      {/* Consent Checkbox */}
      <div
        className={`flex items-start gap-3 p-3 rounded border ${
          hasError ? 'border-red-500 bg-red-950 bg-opacity-20' : 'border-zinc-700 bg-zinc-950'
        }`}
      >
        <input
          id="privacyConsent"
          type="checkbox"
          checked={consentGiven}
          onChange={handleConsentChange}
          aria-required="true"
          aria-invalid={hasError}
          aria-describedby={hasError && errorMessage ? 'privacyConsent-error' : undefined}
          className="w-4 h-4 mt-0.5 accent-blue-500 cursor-pointer"
        />
        <label htmlFor="privacyConsent" className="flex-1 text-xs text-zinc-300 cursor-pointer">
          <span className="text-white font-medium">I understand and consent</span> to the collection
          and processing of my personal data as described above
        </label>
      </div>

      {hasError && errorMessage && (
        <div id="privacyConsent-error" className="text-red-400 text-xs mt-2">
          {errorMessage}
        </div>
      )}
    </div>
  );
};

export default PrivacyNotice;
