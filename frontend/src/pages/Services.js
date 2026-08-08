import React from 'react';

function Services() {
  const services = [
    {
      icon: '🧺', title: 'Laundry Service',
      desc: 'Complete wash, dry, and fold service for everyday garments. We use premium detergents that are gentle on fabrics yet tough on stains. All clothes are sorted by color and fabric type for optimal care.',
      features: ['Wash & Fold', 'Color Sorting', 'Stain Treatment', 'Fresh Fragrance']
    },
    {
      icon: '✨', title: 'Dry Cleaning Service',
      desc: 'Professional dry cleaning using eco-friendly solvents for delicate and premium garments. Ideal for suits, sarees, lehengas, and designer wear that cannot be washed traditionally.',
      features: ['Premium Solvents', 'Delicate Fabric Care', 'Stain Removal', 'Professional Pressing']
    },
    {
      icon: '👟', title: 'Shoe Cleaning Service',
      desc: 'Specialized shoe cleaning for all types - sneakers, formal shoes, leather boots, and suede. We restore your footwear to near-new condition with deep cleaning and deodorizing.',
      features: ['Deep Cleaning', 'Deodorizing', 'Color Restoration', 'Sole Cleaning']
    },
    {
      icon: '🧹', title: 'Carpet Dry Cleaning',
      desc: 'Professional carpet and rug cleaning that removes deep-seated dirt, allergens, and stubborn stains. We use specialized equipment for thorough cleaning without damaging fibers.',
      features: ['Deep Extraction', 'Stain Removal', 'Allergen Removal', 'Fiber Protection']
    },
    {
      icon: '🪟', title: 'Curtain Dry Cleaning',
      desc: 'Expert curtain cleaning service that removes dust, allergens, and stains while preserving the fabric quality and color. We handle all curtain types including blackout and sheer fabrics.',
      features: ['Dust Removal', 'Color Preservation', 'Anti-Allergen', 'Crease-Free Finish']
    },
    {
      icon: '🧤', title: 'Leather Cleaning Service',
      desc: 'Specialized cleaning and conditioning for all leather items including jackets, bags, shoes, and accessories. Our process cleans, conditions, and protects leather for lasting quality.',
      features: ['Gentle Cleaning', 'Conditioning', 'Color Restoration', 'Waterproofing']
    },
    {
      icon: '♨️', title: 'Steam Ironing Service',
      desc: 'Professional steam ironing that gives your clothes a crisp, wrinkle-free finish. Perfect for formal wear, linen, and cotton garments that need a polished appearance.',
      features: ['Wrinkle-Free', 'Sanitizing Steam', 'Crease Setting', 'Gentle on Fabric']
    },
  ];

  return (
    <section className="services-section" style={{ paddingTop: '60px' }}>
      <div className="container">
        <div className="section-title">
          <h2>Our Services</h2>
          <p>Professional garment care solutions for every need</p>
        </div>
        <div className="services-grid">
          {services.map((service, idx) => (
            <div key={idx} className="service-card" style={{ textAlign: 'left' }}>
              <span className="service-icon" style={{ display: 'block', textAlign: 'center' }}>{service.icon}</span>
              <h3 style={{ textAlign: 'center' }}>{service.title}</h3>
              <p>{service.desc}</p>
              <ul style={{ marginTop: '15px', paddingLeft: '20px', fontSize: '14px', color: '#555' }}>
                {service.features.map((f, i) => <li key={i} style={{ marginBottom: '5px' }}>✓ {f}</li>)}
              </ul>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

export default Services;
