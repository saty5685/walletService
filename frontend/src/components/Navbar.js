import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import axios from 'axios';

const API = process.env.REACT_APP_API_URL || 'http://localhost:5000/api';

function Navbar() {
  const { isAuthenticated, logout } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const [offers, setOffers] = useState([]);
  const [showOffer, setShowOffer] = useState(true);

  useEffect(() => {
    axios.get(`${API}/offers`).then(res => setOffers(res.data)).catch(() => {});
  }, []);

  return (
    <>
      {showOffer && offers.length > 0 && (
        <div className="offer-bar">
          🎉 {offers[0].title} - {offers[0].description} {offers[0].code && `| Code: ${offers[0].code}`}
          <button className="close-btn" onClick={() => setShowOffer(false)}>✕</button>
        </div>
      )}
      <nav className="navbar">
        <div className="container">
          <Link to="/" className="logo">
            🧺 Fresh<span>Press</span>
          </Link>
          <div className="hamburger" onClick={() => setMenuOpen(!menuOpen)}>
            <span></span><span></span><span></span>
          </div>
          <ul className={`nav-links ${menuOpen ? 'active' : ''}`}>
            <li><Link to="/" onClick={() => setMenuOpen(false)}>Home</Link></li>
            <li><Link to="/services" onClick={() => setMenuOpen(false)}>Services</Link></li>
            <li><Link to="/pricing" onClick={() => setMenuOpen(false)}>Pricing</Link></li>
            <li><Link to="/blogs" onClick={() => setMenuOpen(false)}>Blogs</Link></li>
            <li><Link to="/store-locator" onClick={() => setMenuOpen(false)}>Store Locator</Link></li>
            {isAuthenticated ? (
              <>
                <li><Link to="/admin/dashboard" onClick={() => setMenuOpen(false)}>Dashboard</Link></li>
                <li><button onClick={() => { logout(); setMenuOpen(false); }} className="btn btn-outline" style={{ padding: '8px 16px' }}>Logout</button></li>
              </>
            ) : (
              <li><Link to="/admin/login" className="btn-primary" onClick={() => setMenuOpen(false)}>Admin</Link></li>
            )}
          </ul>
        </div>
      </nav>
    </>
  );
}

export default Navbar;
