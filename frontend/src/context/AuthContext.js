import React, { createContext, useState, useContext, useEffect } from 'react';

const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
  const [token, setToken] = useState(localStorage.getItem('adminToken'));
  const [admin, setAdmin] = useState(null);

  useEffect(() => {
    if (token) {
      setAdmin({ loggedIn: true });
    }
  }, [token]);

  const login = (newToken, adminData) => {
    localStorage.setItem('adminToken', newToken);
    setToken(newToken);
    setAdmin(adminData);
  };

  const logout = () => {
    localStorage.removeItem('adminToken');
    setToken(null);
    setAdmin(null);
  };

  return (
    <AuthContext.Provider value={{ token, admin, login, logout, isAuthenticated: !!token }}>
      {children}
    </AuthContext.Provider>
  );
};
