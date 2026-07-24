import { useState } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';

export default function RegisterPage() {
  const [email, setEmail]           = useState('');
  const [password, setPassword]     = useState('');
  const [companyName, setCompanyName] = useState('');
  const [error, setError]           = useState('');
  const [loading, setLoading]       = useState(false);

  // After successful registration, we show the API key screen
  const [registered, setRegistered] = useState(false);
  const [apiKeyData, setApiKeyData] = useState(null);
  const [copied, setCopied]         = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await axios.post(
        'http://localhost:8080/api/v1/auth/register',
        { email, password, companyName }
      );

      // Registration succeeded — show the API key screen
      setApiKeyData(response.data);
      setRegistered(true);

    } catch (err) {
      const message = err.response?.data?.error || 'Registration failed. Please try again.';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const copyApiKey = () => {
    navigator.clipboard.writeText(apiKeyData.apiKey);
    setCopied(true);
    setTimeout(() => setCopied(false), 3000);
  };

  // ── API KEY SCREEN ─────────────────────────────────────────
  // Shown ONCE after registration
  // Company must copy this key — they cannot retrieve it again
  if (registered && apiKeyData) {
    return (
      <div className="min-h-screen bg-gray-950 flex flex-col justify-center items-center p-8">
        <div className="w-full max-w-lg">

          {/* Success header */}
          <div className="flex items-center gap-2 mb-8">
            <div className="w-8 h-8 rounded-full bg-teal-500"/>
            <span className="text-2xl font-bold text-white">
              Pulse<span className="text-teal-400">Ops</span>
            </span>
          </div>

          <div className="bg-gray-900 border border-gray-800 rounded-xl p-8">

            {/* Green success banner */}
            <div className="flex items-center gap-3 bg-teal-900/30 border border-teal-700
                            rounded-lg px-4 py-3 mb-6">
              <span className="text-teal-400 text-xl">✓</span>
              <div>
                <p className="text-teal-400 font-semibold text-sm">Account created successfully</p>
                <p className="text-teal-300/70 text-xs">
                  Welcome to PulseOps, {apiKeyData.companyName}
                </p>
              </div>
            </div>

            {/* API key section */}
            <h2 className="text-white font-bold text-lg mb-2">Your API Key</h2>
            <p className="text-gray-400 text-sm mb-4 leading-relaxed">
              Copy this key now. <span className="text-red-400 font-semibold">
              It will never be shown again.</span> Your monitoring agent uses this key
              to authenticate with PulseOps.
            </p>

            <div className="bg-gray-800 border border-gray-700 rounded-lg p-4 mb-4">
              <div className="flex items-center justify-between gap-3">
                <code className="text-teal-400 text-sm font-mono break-all flex-1">
                  {apiKeyData.apiKey}
                </code>
                <button
                  onClick={copyApiKey}
                  className={`flex-shrink-0 px-3 py-1.5 rounded text-xs font-semibold
                              transition-colors ${copied
                                ? 'bg-teal-600 text-white'
                                : 'bg-gray-700 text-gray-300 hover:bg-gray-600'}`}>
                  {copied ? '✓ Copied' : 'Copy'}
                </button>
              </div>
            </div>

            {/* Tenant ID */}
            <div className="bg-gray-800/50 rounded-lg px-4 py-3 mb-6">
              <p className="text-gray-500 text-xs mb-1">Your Tenant ID</p>
              <code className="text-gray-300 text-sm">{apiKeyData.tenantId}</code>
            </div>

            {/* Quick start instructions */}
            <div className="border border-gray-800 rounded-lg p-4 mb-6">
              <p className="text-gray-400 text-xs font-semibold uppercase tracking-wide mb-3">
                Quick Start — Run this on your server
              </p>
              <div className="bg-gray-900 rounded p-3 font-mono text-xs text-gray-300 space-y-1">
                <p className="text-gray-500"># Download the PulseOps agent</p>
                <p>pip install pulseops-agent</p>
                <p className="mt-2 text-gray-500"># Start monitoring your server</p>
                <p>pulseops-agent start \</p>
                <p className="pl-4">--api-key <span className="text-teal-400">{apiKeyData.apiKey}</span> \</p>
                <p className="pl-4">--service-name my-app \</p>
                <p className="pl-4">--ingestor https://api.pulseops.io</p>
              </div>
            </div>

            {/* Go to dashboard button */}
            <Link
              to="/login"
              className="block w-full bg-teal-500 hover:bg-teal-400 text-gray-900
                         font-semibold rounded-lg px-4 py-3 text-center transition-colors">
              Continue to Login →
            </Link>

          </div>
        </div>
      </div>
    );
  }

  // ── REGISTRATION FORM ──────────────────────────────────────
  return (
    <div className="min-h-screen bg-gray-950 flex flex-col justify-center items-center p-8">
      <div className="w-full max-w-md">
        <div className="flex items-center gap-2 mb-8">
          <div className="w-8 h-8 rounded-full bg-teal-500"/>
          <span className="text-2xl font-bold text-white">
            Pulse<span className="text-teal-400">Ops</span>
          </span>
        </div>

        <h1 className="text-2xl font-bold text-white mb-2">Create your account</h1>
        <p className="text-gray-400 text-sm mb-8">
          Start monitoring your infrastructure with AI
        </p>

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="block text-sm text-gray-300 mb-2">Company name</label>
            <input
              value={companyName}
              onChange={(e) => setCompanyName(e.target.value)}
              required
              className="w-full bg-gray-800 border border-gray-700 rounded-lg px-4 py-3
                         text-white placeholder-gray-500 focus:outline-none
                         focus:border-teal-500 transition-colors"
              placeholder="Acme Corp"/>
          </div>
          <div>
            <label className="block text-sm text-gray-300 mb-2">Email address</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="w-full bg-gray-800 border border-gray-700 rounded-lg px-4 py-3
                         text-white placeholder-gray-500 focus:outline-none
                         focus:border-teal-500 transition-colors"
              placeholder="you@company.com"/>
          </div>
          <div>
            <label className="block text-sm text-gray-300 mb-2">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="w-full bg-gray-800 border border-gray-700 rounded-lg px-4 py-3
                         text-white placeholder-gray-500 focus:outline-none
                         focus:border-teal-500 transition-colors"
              placeholder="••••••••"/>
          </div>

          {error && (
            <div className="bg-red-900/30 border border-red-700 rounded-lg px-4 py-3
                            text-red-400 text-sm">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-teal-500 hover:bg-teal-400 disabled:bg-teal-800
                       text-gray-900 font-semibold rounded-lg px-4 py-3 transition-colors">
            {loading ? 'Creating account...' : 'Create account'}
          </button>
        </form>

        <p className="text-gray-500 text-sm text-center mt-6">
          Already have an account?{' '}
          <Link to="/login" className="text-teal-400 hover:text-teal-300">Sign in</Link>
        </p>
      </div>
    </div>
  );
}