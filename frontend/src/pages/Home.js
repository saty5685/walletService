import React from 'react';
import { Link } from 'react-router-dom';

function Home() {
  return (
    <>
      {/* Hero Section */}
      <section className="hero">
        <div className="container">
          <div className="hero-content">
            <h1>Premium <span>Laundry</span> & Dry Cleaning at Your Doorstep</h1>
            <p>Professional garment care with free pickup & delivery. We handle your clothes with the utmost care using eco-friendly products.</p>
            <div className="hero-buttons">
              <a href="https://wa.me/919876543210?text=Hi!%20I%20would%20like%20to%20schedule%20a%20free%20pickup." className="btn btn-whatsapp" target="_blank" rel="noopener noreferrer">
                💬 Chat on WhatsApp
              </a>
              <a href="https://wa.me/919876543210?text=Hi!%20I%20want%20to%20schedule%20a%20free%20pickup." className="btn btn-primary" target="_blank" rel="noopener noreferrer">
                🚚 Schedule Free Pickup
              </a>
            </div>
          </div>
          <div className="hero-image">
            <div className="garment-grid">
              <div className="garment-card">
                <span className="icon">👔</span>
                <p>Shirts & Formals</p>
              </div>
              <div className="garment-card">
                <span className="icon">👗</span>
                <p>Dresses & Sarees</p>
              </div>
              <div className="garment-card">
                <span className="icon">🧥</span>
                <p>Jackets & Coats</p>
              </div>
              <div className="garment-card">
                <span className="icon">👟</span>
                <p>Shoes & Sneakers</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Services Preview */}
      <section className="services-section">
        <div className="container">
          <div className="section-title">
            <h2>Our Services</h2>
            <p>Professional care for every fabric and garment</p>
          </div>
          <div className="services-grid">
            <div className="service-card">
              <span className="service-icon">🧺</span>
              <h3>Laundry Service</h3>
              <p>Complete wash, dry, and fold service for your everyday clothes. Fresh and clean every time.</p>
            </div>
            <div className="service-card">
              <span className="service-icon">✨</span>
              <h3>Dry Cleaning</h3>
              <p>Expert dry cleaning for delicate fabrics and special garments using premium solvents.</p>
            </div>
            <div className="service-card">
              <span className="service-icon">👟</span>
              <h3>Shoe Cleaning</h3>
              <p>Restore your shoes to their original glory with our professional shoe cleaning service.</p>
            </div>
            <div className="service-card">
              <span className="service-icon">🧹</span>
              <h3>Carpet Dry Cleaning</h3>
              <p>Deep cleaning for carpets and rugs. Remove stains, allergens, and odors effectively.</p>
            </div>
            <div className="service-card">
              <span className="service-icon">🪟</span>
              <h3>Curtain Dry Cleaning</h3>
              <p>Refresh your curtains without removing them. We handle all fabric types carefully.</p>
            </div>
            <div className="service-card">
              <span className="service-icon">🧤</span>
              <h3>Leather Cleaning</h3>
              <p>Specialized cleaning and conditioning for leather jackets, bags, and accessories.</p>
            </div>
          </div>
          <div style={{ textAlign: 'center', marginTop: '40px' }}>
            <Link to="/services" className="btn btn-outline">View All Services →</Link>
          </div>
        </div>
      </section>

      {/* How It Works */}
      <section className="how-it-works">
        <div className="container">
          <div className="section-title">
            <h2>How It Works</h2>
            <p>Simple 4-step process for hassle-free laundry</p>
          </div>
          <div className="steps-grid">
            <div className="step-card">
              <div className="step-number">1</div>
              <h3>Schedule Pickup</h3>
              <p>Book a free pickup via WhatsApp or call us directly</p>
            </div>
            <div className="step-card">
              <div className="step-number">2</div>
              <h3>We Collect</h3>
              <p>Our delivery partner picks up your clothes from your doorstep</p>
            </div>
            <div className="step-card">
              <div className="step-number">3</div>
              <h3>Expert Care</h3>
              <p>Your garments are professionally cleaned with premium products</p>
            </div>
            <div className="step-card">
              <div className="step-number">4</div>
              <h3>Delivered Fresh</h3>
              <p>Clean, ironed clothes delivered back to you within 48 hours</p>
            </div>
          </div>
        </div>
      </section>

      {/* CTA */}
      <section style={{ padding: '60px 0', background: 'linear-gradient(135deg, #1a73e8, #1557b0)', textAlign: 'center', color: 'white' }}>
        <div className="container">
          <h2 style={{ fontSize: '32px', marginBottom: '15px' }}>Ready to Experience Fresh Clothes?</h2>
          <p style={{ fontSize: '18px', marginBottom: '30px', opacity: 0.9 }}>Schedule a free pickup today and get 20% off on your first order!</p>
          <a href="https://wa.me/919876543210?text=Hi!%20I%20want%20to%20schedule%20my%20first%20free%20pickup." className="btn btn-whatsapp" target="_blank" rel="noopener noreferrer">
            Schedule Free Pickup →
          </a>
        </div>
      </section>
    </>
  );
}

export default Home;
