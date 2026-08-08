import React from 'react';
import { Link } from 'react-router-dom';

function Footer() {
  return (
    <footer className="footer">
      <div className="container">
        <div className="footer-grid">
          <div className="footer-col">
            <h3>🧺 FreshPress</h3>
            <p>Premium laundry & dry cleaning services with free pickup and delivery at your doorstep.</p>
            <p style={{ marginTop: '15px' }}>📞 +91 98765 43210</p>
            <p>✉️ hello@freshpress.in</p>
          </div>
          <div className="footer-col">
            <h3>Our Services</h3>
            <Link to="/services">Laundry Service</Link>
            <Link to="/services">Dry Cleaning</Link>
            <Link to="/services">Shoe Cleaning</Link>
            <Link to="/services">Carpet Cleaning</Link>
            <Link to="/services">Curtain Cleaning</Link>
            <Link to="/services">Leather Cleaning</Link>
            <Link to="/services">Steam Ironing</Link>
          </div>
          <div className="footer-col">
            <h3>Quick Links</h3>
            <Link to="/pricing">Pricing</Link>
            <Link to="/blogs">Blogs</Link>
            <Link to="/store-locator">Store Locator</Link>
            <Link to="/admin/login">Admin Login</Link>
          </div>
          <div className="footer-col">
            <h3>Store Hours</h3>
            <p>Monday - Saturday</p>
            <p>8:00 AM - 9:00 PM</p>
            <p style={{ marginTop: '10px' }}>Sunday</p>
            <p>9:00 AM - 6:00 PM</p>
          </div>
        </div>
        <div className="footer-bottom">
          <p>&copy; {new Date().getFullYear()} FreshPress Laundry & Dry Cleaning. All rights reserved.</p>
        </div>
      </div>
    </footer>
  );
}

export default Footer;
