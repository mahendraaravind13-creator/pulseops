import { createContext, useContext, useState } from 'react';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    // Persist login across page refresh
    const saved = localStorage.getItem('pulseops_user');
    return saved ? JSON.parse(saved) : null;
  });

  const login = (email, companyName, tenantId, subscriptionStatus) => {
    const userData = { email, companyName, tenantId, subscriptionStatus };
    setUser(userData);
    localStorage.setItem('pulseops_user', JSON.stringify(userData));
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem('pulseops_user');
  };

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}