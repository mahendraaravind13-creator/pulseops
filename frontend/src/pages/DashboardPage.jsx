import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import axiosInstance from '../api/axiosConfig';
import axios from 'axios'; // <-- NEW: Imported standard axios to talk to the Ingestor

const STATUS_COLORS = {
  OPEN: 'bg-red-500/20 text-red-400 border-red-500/30',
  ACKNOWLEDGED: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
  RESOLVED: 'bg-teal-500/20 text-teal-400 border-teal-500/30',
  ESCALATED: 'bg-purple-500/20 text-purple-400 border-purple-500/30',
};

function Navbar({ openCount }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const handleLogout = () => { logout(); navigate('/login'); };

  return (
    <nav className="bg-gray-900 border-b border-gray-800 px-6 py-4 flex items-center justify-between">
      <div className="flex items-center gap-3">
        <div className="w-8 h-8 rounded-full bg-teal-500 flex items-center justify-center">
          <div className="w-3 h-3 rounded-full bg-gray-900"/>
        </div>
        <span className="text-xl font-bold text-white">Pulse<span className="text-teal-400">Ops</span></span>
      </div>
      <div className="text-gray-400 text-sm font-medium uppercase tracking-wider">
        {user?.companyName || 'Global'} Operations Centre
      </div>
      <div className="flex items-center gap-4">
        <div className="relative">
          <div className="w-8 h-8 bg-gray-800 rounded-full flex items-center justify-center border border-gray-700">
            <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/>
            </svg>
          </div>
          {openCount > 0 && (
            <span className="absolute -top-1 -right-1 w-4 h-4 bg-red-500 rounded-full text-xs text-white flex items-center justify-center">
              {openCount}
            </span>
          )}
        </div>
        <button onClick={handleLogout} className="text-sm text-gray-400 hover:text-white border border-gray-700 hover:border-gray-500 px-3 py-1.5 rounded-lg transition-colors">
          Logout
        </button>
      </div>
    </nav>
  );
}

Navbar.propTypes = {
  openCount: PropTypes.number,
};

function StatCard({ label, value, color = 'text-white' }) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
      <div className="text-gray-500 text-xs uppercase tracking-wider mb-2">{label}</div>
      <div className={`text-3xl font-bold ${color}`}>{value}</div>
    </div>
  );
}

StatCard.propTypes = {
  label: PropTypes.string.isRequired,
  value: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  color: PropTypes.string,
};

function AgentTimeline({ incident, onClose }) {
  const STEP_LABELS = [
    'Diagnosing anomaly with Gemini',
    'Confidence gate check',
    'Safety check via Gemini',
    'Executing remediation action',
    'Verifying service recovery',
    'Generating post-mortem report',
  ];

  const getStepStatus = (i) => {
    if (incident.status === 'RESOLVED') return 'done';
    if (incident.status === 'ESCALATED') {
      if (i <= 2) return 'done';
      return 'escalated';
    }
    if (incident.status === 'ACKNOWLEDGED') {
      if (i <= 3) return 'done';
      return 'pending';
    }
    if (incident.status === 'OPEN') {
      if (i === 0) return 'done';
      return 'pending';
    }
    return 'done';
  };

  const getStepResult = (i) => {
    switch(i) {
      case 0: return { text: incident.rootCause, extra: `Confidence: ${incident.confidenceScore}%` };
      case 1: return { text: incident.confidenceScore >= 70 ? '✅ Passed — proceeding to autonomous action' : '❌ Failed — confidence too low, escalating to human' };
      case 2: return { text: 'Risk evaluated by Gemini safety agent' };
      case 3: return { text: incident.recommendedAction };
      case 4: return { text: incident.status === 'RESOLVED' ? '✅ Service metrics returned to normal' : '❌ Recovery failed — manual review required' };
      case 5: return { text: incident.resolutionNote || 'Pending...' };
      default: return { text: '' };
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-black/50" onClick={onClose}/>
      <div className="relative w-full max-w-lg bg-gray-900 border-l border-gray-700 h-full overflow-y-auto shadow-2xl">
        <div className="p-6 border-b border-gray-800 flex items-start justify-between">
          <div>
            <div className="text-xs text-gray-500 mb-1">Incident #{incident.id}</div>
            <h2 className="text-lg font-bold text-white">{incident.serviceName}</h2>
            <span className={`inline-block mt-2 text-xs px-2 py-1 rounded border ${STATUS_COLORS[incident.status]}`}>
              {incident.status}
            </span>
          </div>
          <button onClick={onClose} className="text-gray-500 hover:text-white text-xl font-bold">✕</button>
        </div>

        <div className="p-6">
          <h3 className="text-sm font-semibold text-teal-400 uppercase tracking-wider mb-4">Agent Thought Process</h3>
          <div className="space-y-4">
            {STEP_LABELS.map((label, i) => {
              const status = getStepStatus(i);
              const result = getStepResult(i);
              return (
                <div key={i} className="flex gap-4">
                  <div className="flex flex-col items-center">
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold border-2 flex-shrink-0
                      ${status === 'done' ? 'bg-teal-500/20 border-teal-500 text-teal-400' :
                        status === 'escalated' ? 'bg-red-500/20 border-red-500 text-red-400' :
                        'bg-gray-800 border-gray-600 text-gray-500'}`}>
                      {status === 'done' ? '✓' : status === 'escalated' ? '!' : i + 1}
                    </div>
                    {i < STEP_LABELS.length - 1 && <div className="w-px h-4 bg-gray-700 mt-1"/>}
                  </div>
                  <div className="flex-1 pb-2">
                    <div className="text-sm font-medium text-gray-300 mb-1">Step {i + 1} — {label}</div>
                    {status !== 'pending' ? (
                      <div className={`text-xs rounded-lg p-3 bg-gray-800 ${i === 5 ? 'border border-gray-700 text-gray-300 italic' : 'text-gray-400'}`}>
                        {result.text}
                        {result.extra && <div className="mt-1 text-teal-400 font-semibold">{result.extra}</div>}
                      </div>
                    ) : (
                      <div className="text-xs text-gray-600 italic">Waiting...</div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <div className="p-6 border-t border-gray-800">
          <div className="text-xs text-gray-500 mb-1">Created</div>
          <div className="text-sm text-gray-300">{new Date(incident.createdAt).toLocaleString()}</div>
          {incident.status === 'RESOLVED' && (
            <div className="mt-4 bg-teal-500/10 border border-teal-500/30 rounded-lg p-3">
              <div className="text-xs text-teal-400 font-semibold mb-1">🎉 Resolved Autonomously</div>
              <div className="text-xs text-gray-400">{incident.resolutionNote}</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
AgentTimeline.propTypes = {
  incident: PropTypes.shape({
    id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    rootCause: PropTypes.string,
    confidenceScore: PropTypes.number,
    recommendedAction: PropTypes.string,
    status: PropTypes.string,
    resolutionNote: PropTypes.string,
    createdAt: PropTypes.string,
    serviceName: PropTypes.string,
  }).isRequired,
  onClose: PropTypes.func.isRequired,
};

export default function DashboardPage() {
  const { user } = useAuth();
  const [incidents, setIncidents] = useState([]);
  const [liveMetrics, setLiveMetrics] = useState([]); 
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);

  const fetchData = async () => {
    if (!user?.tenantId) return;

    try {
      const [incRes, metRes] = await Promise.all([
        // Incidents fetch from Analyzer (via axiosInstance)
        axiosInstance.get(`/api/v1/incidents/tenant/${user.tenantId}`),
        // Live Metrics fetch directly from Ingestor on port 8080
        axios.get(`http://localhost:8080/api/v1/metrics/latest/tenant/${user.tenantId}`).catch((e) => {
          console.error("Failed to fetch live metrics from port 8080:", e);
          return { data: [] };
        }) 
      ]);
      setIncidents(incRes.data);
      setLiveMetrics(metRes.data);
      setLastUpdated(new Date());
    } catch (err) {
      console.error('Failed to fetch dashboard data', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (user?.tenantId) {
      fetchData();
      const interval = setInterval(fetchData, 5000); 
      return () => clearInterval(interval);
    }
  }, [user]);

  const open = incidents.filter(i => i.status === 'OPEN').length;
  const resolved = incidents.filter(i => i.status === 'RESOLVED').length;
  const avgConfidence = incidents.length
    ? Math.round(incidents.reduce((a, b) => a + b.confidenceScore, 0) / incidents.length)
    : 0;

  const recentIncidents = [...incidents].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 8);

  return (
    <div className="min-h-screen bg-gray-950">
      <Navbar openCount={open} />

      <div className="p-6 space-y-6">
        {/* Stats */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard label="Total Incidents" value={incidents.length} />
          <StatCard label="Open Now" value={open} color={open > 0 ? 'text-red-400' : 'text-teal-400'} />
          <StatCard label="Resolved" value={resolved} color="text-teal-400" />
          <StatCard label="Avg AI Confidence" value={`${avgConfidence}%`} color="text-teal-400" />
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Service health */}
          <div className="lg:col-span-2">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-white font-semibold">Service Health</h2>
              {lastUpdated && <span className="text-gray-600 text-xs">Updated {lastUpdated.toLocaleTimeString()}</span>}
            </div>
            {loading ? (
              <div className="bg-gray-900 border border-gray-800 rounded-xl p-8 text-center text-gray-600">
                Loading services...
              </div>
            ) : liveMetrics.length === 0 ? (
              <div className="bg-gray-900 border border-gray-800 rounded-xl p-8 text-center">
                <div className="text-gray-500 text-sm">No live metrics detected.</div>
                <div className="text-gray-600 text-xs mt-2">Start your simulator to see services appear here.</div>
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                
                {liveMetrics.map(svc => {
                  const svcIncidents = incidents.filter(i => i.serviceName === svc.serviceName);
                  const latest = [...svcIncidents].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))[0];
                  
                  return (
                    <div key={svc.serviceName}
                      onClick={() => latest && setSelected(latest)}
                      className="bg-gray-900 border border-gray-800 hover:border-gray-600 rounded-xl p-5 cursor-pointer transition-all flex flex-col justify-between">
                      <div>
                        <div className="flex items-center justify-between mb-3">
                          <span className="text-white font-medium text-sm">{svc.serviceName}</span>
                          <div className={`flex items-center gap-2`}>
                            <div className={`w-2.5 h-2.5 rounded-full ${
                              svc.status === 'CRITICAL' ? 'bg-red-500 animate-pulse' :
                              svc.status === 'WARNING' ? 'bg-yellow-500' : 'bg-teal-500'}`}/>
                            <span className={`text-xs font-semibold ${
                              svc.status === 'CRITICAL' ? 'text-red-400' :
                              svc.status === 'WARNING' ? 'text-yellow-400' : 'text-teal-400'}`}>
                              {svc.status}
                            </span>
                          </div>
                        </div>
                        <div className="text-xs text-gray-500 mb-2">Total incidents: {svcIncidents.length}</div>
                      </div>
                      
                      {/* Metric Readouts */}
                      <div className="flex justify-between items-end border-t border-gray-800 pt-3 mt-2">
                        <div className="text-xs text-gray-400">
                          <span className="block text-gray-500 text-[10px] uppercase">CPU</span>
                          {svc.cpu ? Number(svc.cpu).toFixed(1) : 0}%
                        </div>
                        <div className="text-xs text-gray-400">
                          <span className="block text-gray-500 text-[10px] uppercase">Memory</span>
                          {svc.memory ? Number(svc.memory).toFixed(1) : 0}%
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Live incident feed */}
          <div>
            <h2 className="text-white font-semibold mb-4">Live Incident Feed</h2>
            <div className="space-y-3">
              {recentIncidents.length === 0 ? (
                <div className="bg-gray-900 border border-gray-800 rounded-xl p-6 text-center text-gray-600 text-sm">
                  No incidents yet
                </div>
              ) : recentIncidents.map(inc => (
                <div key={inc.id}
                  onClick={() => setSelected(inc)}
                  className="bg-gray-900 border border-gray-800 hover:border-gray-600 rounded-xl p-4 cursor-pointer transition-all">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-white text-sm font-medium">{inc.serviceName}</span>
                    <span className={`text-xs px-2 py-0.5 rounded border ${STATUS_COLORS[inc.status]}`}>
                      {inc.status}
                    </span>
                  </div>
                  <div className="text-xs text-gray-500 truncate">{inc.rootCause?.slice(0, 55)}...</div>
                  <div className="text-xs text-gray-700 mt-1">{new Date(inc.createdAt).toLocaleTimeString()}</div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Incidents table */}
        <div>
          <h2 className="text-white font-semibold mb-4">All Incidents</h2>
          <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-800">
                  <th className="text-left text-gray-500 text-xs uppercase tracking-wider px-5 py-3">ID</th>
                  <th className="text-left text-gray-500 text-xs uppercase tracking-wider px-5 py-3">Service</th>
                  <th className="text-left text-gray-500 text-xs uppercase tracking-wider px-5 py-3 hidden md:table-cell">Root Cause</th>
                  <th className="text-left text-gray-500 text-xs uppercase tracking-wider px-5 py-3">Confidence</th>
                  <th className="text-left text-gray-500 text-xs uppercase tracking-wider px-5 py-3">Status</th>
                  <th className="text-left text-gray-500 text-xs uppercase tracking-wider px-5 py-3 hidden lg:table-cell">Time</th>
                </tr>
              </thead>
              <tbody>
                {recentIncidents.map((inc, idx) => (
                  <tr key={inc.id}
                    onClick={() => setSelected(inc)}
                    className={`border-b border-gray-800/50 hover:bg-gray-800/50 cursor-pointer transition-colors ${idx % 2 === 0 ? '' : 'bg-gray-900/50'}`}>
                    <td className="px-5 py-3 text-gray-500 font-mono">#{inc.id}</td>
                    <td className="px-5 py-3 text-white font-medium">{inc.serviceName}</td>
                    <td className="px-5 py-3 text-gray-400 hidden md:table-cell max-w-xs truncate">{inc.rootCause?.slice(0, 60)}...</td>
                    <td className="px-5 py-3">
                      <span className={`font-semibold ${inc.confidenceScore >= 80 ? 'text-teal-400' : inc.confidenceScore >= 70 ? 'text-yellow-400' : 'text-red-400'}`}>
                        {inc.confidenceScore}%
                      </span>
                    </td>
                    <td className="px-5 py-3">
                      <span className={`text-xs px-2 py-1 rounded border ${STATUS_COLORS[inc.status]}`}>
                        {inc.status}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-gray-600 hidden lg:table-cell">{new Date(inc.createdAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {incidents.length === 0 && (
              <div className="text-center py-12 text-gray-600">No incidents in the database yet.</div>
            )}
          </div>
        </div>
      </div>

      {selected && <AgentTimeline incident={selected} onClose={() => setSelected(null)} />}
    </div>
  );
}