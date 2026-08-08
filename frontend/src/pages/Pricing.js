import React, { useState, useEffect } from 'react';
import axios from 'axios';

const API = process.env.REACT_APP_API_URL || 'http://localhost:5000/api';

function Pricing() {
  const [items, setItems] = useState([]);
  const [activeCategory, setActiveCategory] = useState('men');

  useEffect(() => {
    axios.get(`${API}/pricing`).then(res => setItems(res.data)).catch(() => {
      // Use fallback data if API unavailable
      setItems(getFallbackData());
    });
  }, []);

  const categories = [
    { key: 'men', label: "Men's Wear" },
    { key: 'women', label: "Women's Wear" },
    { key: 'household', label: 'Household' },
    { key: 'accessories', label: 'Accessories' },
  ];

  const filtered = items.filter(i => i.category === activeCategory);

  return (
    <section className="pricing-section">
      <div className="container">
        <div className="section-title">
          <h2>Our Pricing</h2>
          <p>Transparent pricing with no hidden charges</p>
        </div>
        <div className="pricing-tabs">
          {categories.map(cat => (
            <button key={cat.key} className={`pricing-tab ${activeCategory === cat.key ? 'active' : ''}`} onClick={() => setActiveCategory(cat.key)}>
              {cat.label}
            </button>
          ))}
        </div>
        <table className="pricing-table">
          <thead>
            <tr>
              <th>Garment</th>
              <th>Laundry (₹)</th>
              <th>Dry Cleaning (₹)</th>
              <th>Steam Ironing (₹)</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((item, idx) => (
              <tr key={idx}>
                <td>{item.garment}</td>
                <td>{item.laundry || '-'}</td>
                <td>{item.dryCleaning || '-'}</td>
                <td>{item.steamIroning || '-'}</td>
              </tr>
            ))}
            {filtered.length === 0 && (
              <tr><td colSpan="4" style={{ textAlign: 'center', padding: '30px' }}>No items in this category</td></tr>
            )}
          </tbody>
        </table>
        <p style={{ textAlign: 'center', marginTop: '20px', color: '#666', fontSize: '14px' }}>
          * Prices are indicative. Final pricing may vary based on garment condition and special treatments required.
        </p>
      </div>
    </section>
  );
}

function getFallbackData() {
  return [
    { garment: 'Shirt', laundry: 30, dryCleaning: 60, steamIroning: 20, category: 'men' },
    { garment: 'Trousers', laundry: 40, dryCleaning: 70, steamIroning: 25, category: 'men' },
    { garment: 'Suit (2 piece)', laundry: 0, dryCleaning: 250, steamIroning: 80, category: 'men' },
    { garment: 'Blazer', laundry: 0, dryCleaning: 200, steamIroning: 60, category: 'men' },
    { garment: 'T-Shirt', laundry: 25, dryCleaning: 50, steamIroning: 15, category: 'men' },
    { garment: 'Jeans', laundry: 45, dryCleaning: 80, steamIroning: 30, category: 'men' },
    { garment: 'Jacket', laundry: 0, dryCleaning: 300, steamIroning: 80, category: 'men' },
    { garment: 'Kurta', laundry: 35, dryCleaning: 80, steamIroning: 25, category: 'men' },
    { garment: 'Saree', laundry: 60, dryCleaning: 150, steamIroning: 50, category: 'women' },
    { garment: 'Blouse', laundry: 25, dryCleaning: 50, steamIroning: 15, category: 'women' },
    { garment: 'Salwar Suit', laundry: 50, dryCleaning: 120, steamIroning: 40, category: 'women' },
    { garment: 'Lehenga', laundry: 0, dryCleaning: 600, steamIroning: 150, category: 'women' },
    { garment: 'Dress', laundry: 50, dryCleaning: 120, steamIroning: 40, category: 'women' },
    { garment: 'Kurti', laundry: 30, dryCleaning: 70, steamIroning: 20, category: 'women' },
    { garment: 'Bedsheet (Single)', laundry: 40, dryCleaning: 80, steamIroning: 30, category: 'household' },
    { garment: 'Bedsheet (Double)', laundry: 60, dryCleaning: 100, steamIroning: 40, category: 'household' },
    { garment: 'Curtain (per piece)', laundry: 80, dryCleaning: 150, steamIroning: 50, category: 'household' },
    { garment: 'Carpet (per sq ft)', laundry: 0, dryCleaning: 25, steamIroning: 0, category: 'household' },
    { garment: 'Blanket', laundry: 100, dryCleaning: 200, steamIroning: 0, category: 'household' },
    { garment: 'Shoes (per pair)', laundry: 0, dryCleaning: 250, steamIroning: 0, category: 'accessories' },
    { garment: 'Leather Bag', laundry: 0, dryCleaning: 400, steamIroning: 0, category: 'accessories' },
    { garment: 'Leather Jacket', laundry: 0, dryCleaning: 500, steamIroning: 0, category: 'accessories' },
  ];
}

export default Pricing;
