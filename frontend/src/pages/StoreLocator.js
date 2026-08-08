import React from 'react';

function StoreLocator() {
  return (
    <section className="store-locator">
      <div className="container">
        <div className="section-title">
          <h2>Find Our Store</h2>
          <p>Visit us or schedule a free pickup from your location</p>
        </div>
        <div className="map-container">
          <iframe
            title="Store Location"
            src="https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d3504.233093403447!2d77.20901871508096!3d28.613939982424!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x390cfd5b347eb62d%3A0x52c2b7494e204dce!2sNew%20Delhi%2C%20Delhi!5e0!3m2!1sen!2sin!4v1600000000000!5m2!1sen!2sin"
            allowFullScreen=""
            loading="lazy"
          ></iframe>
        </div>
        <div className="store-info">
          <div className="store-info-card">
            <div className="info-icon">📍</div>
            <h3>Address</h3>
            <p>123, Main Market, Sector 15<br />New Delhi - 110001</p>
          </div>
          <div className="store-info-card">
            <div className="info-icon">📞</div>
            <h3>Phone</h3>
            <p>+91 98765 43210</p>
            <p>+91 11 2345 6789</p>
          </div>
          <div className="store-info-card">
            <div className="info-icon">🕐</div>
            <h3>Working Hours</h3>
            <p>Mon - Sat: 8:00 AM - 9:00 PM</p>
            <p>Sunday: 9:00 AM - 6:00 PM</p>
          </div>
          <div className="store-info-card">
            <div className="info-icon">🚚</div>
            <h3>Free Pickup & Delivery</h3>
            <p>Within 5 km radius</p>
            <p>Schedule via WhatsApp</p>
          </div>
        </div>
      </div>
    </section>
  );
}

export default StoreLocator;
