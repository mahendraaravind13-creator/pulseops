import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import axios from 'axios';

export default function LoginPage() {
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [error, setError]       = useState('');
  const [loading, setLoading]   = useState(false);
  const { login }               = useAuth();
  const navigate                = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await axios.post(
        'http://localhost:8080/api/v1/auth/login',
        { email, password }
      );

      const { tenantId, companyName, subscriptionStatus } = response.data;

      // Store in auth context — dashboard reads from here
      login(email, companyName, tenantId, subscriptionStatus);
      navigate('/dashboard');

    } catch (err) {
      const message = err.response?.data?.error || 'Login failed. Please check your credentials.';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-950 flex">

      {/* Left panel */}
      <div className="hidden lg:flex w-1/2 bg-gray-900 flex-col justify-center
                      items-center p-12 border-r border-gray-800">
        <div className="max-w-md text-center">
          <div className="flex items-center justify-center gap-3 mb-8">
            <div className="w-10 h-10 rounded-full bg-teal-500 flex items-center justify-center">
              <div className="w-4 h-4 rounded-full bg-gray-900"/>
            </div>
            <span className="text-3xl font-bold text-white">
              Pulse<span className="text-teal-400">Ops</span>
            </span>
          </div>
          <div className="relative h-16 mb-8">
            <svg viewBox="0 0 300 60" className="w-full">
              <polyline
                points="0,30 40,30 55,30 65,8 75,52 85,8 95,52 105,30 150,30 165,30 175,18 185,42 195,18 205,42 215,30 260,30 300,30"
                fill="none" stroke="#2dd4bf" strokeWidth="2"
                strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          <h2 className="text-2xl font-bold text-white mb-4">
            Autonomous AI Infrastructure Monitoring
          </h2>
          <p className="text-gray-400 text-sm leading-relaxed">
            Zero human intervention required. PulseOps detects, diagnoses, and resolves
            infrastructure incidents automatically using a 6-step Gemini AI agent.
          </p>
          <div className="mt-8 grid grid-cols-2 gap-4 text-left">
            {[
              { label: '6-Step AI Agent',   desc: 'Gemini-powered diagnosis' },
              { label: 'Auto-Remediation',  desc: 'Safe action execution' },
              { label: 'Real-time Kafka',   desc: 'Live metric streaming' },
              { label: 'Multi-tenant',      desc: 'Isolated per organisation' },
            ].map((f) => (
              <div key={f.label}
                   className="bg-gray-800 rounded-lg p-3 border border-gray-700">
                <div className="text-teal-400 text-xs font-semibold mb-1">{f.label}</div>
                <div className="text-gray-400 text-xs">{f.desc}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Right panel — login form */}
      <div className="flex-1 flex flex-col justify-center items-center p-8">
        <div className="w-full max-w-md">
          <div className="lg:hidden flex items-center gap-2 mb-8">
            <div className="w-8 h-8 rounded-full bg-teal-500"/>
            <span className="text-2xl font-bold text-white">
              Pulse<span className="text-teal-400">Ops</span>
            </span>
          </div>
          <h1 className="text-2xl font-bold text-white mb-2">Welcome back</h1>
          <p className="text-gray-400 text-sm mb-8">Sign in to your operations dashboard</p>

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-sm text-gray-300 mb-2">Email address</label>
              <input
                type="email" value={email}
                onChange={(e) => setEmail(e.target.value)} required
                className="w-full bg-gray-800 border border-gray-700 rounded-lg px-4 py-3
                           text-white placeholder-gray-500 focus:outline-none
                           focus:border-teal-500 transition-colors"
                placeholder="you@company.com"/>
            </div>
            <div>
              <label className="block text-sm text-gray-300 mb-2">Password</label>
              <input
                type="password" value={password}
                onChange={(e) => setPassword(e.target.value)} required
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
              type="submit" disabled={loading}
              className="w-full bg-teal-500 hover:bg-teal-400 disabled:bg-teal-800
                         text-gray-900 font-semibold rounded-lg px-4 py-3 transition-colors">
              {loading ? 'Signing in...' : 'Sign in'}
            </button>
          </form>

          <p className="text-gray-500 text-sm text-center mt-6">
            Dont have an account?{' '}
            <Link to="/register" className="text-teal-400 hover:text-teal-300">Sign up</Link>
          </p>
        </div>
      </div>
    </div>
  );
}