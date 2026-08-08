import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';

const API = process.env.REACT_APP_API_URL || 'http://localhost:5000/api';

function Blogs() {
  const [blogs, setBlogs] = useState([]);

  useEffect(() => {
    axios.get(`${API}/blogs`).then(res => setBlogs(res.data)).catch(() => {
      setBlogs(getFallbackBlogs());
    });
  }, []);

  return (
    <section className="blog-section" style={{ paddingTop: '60px', minHeight: '70vh' }}>
      <div className="container">
        <div className="section-title">
          <h2>Our Blog</h2>
          <p>Tips, tricks, and insights on garment care</p>
        </div>
        <div className="blog-grid">
          {blogs.map((blog, idx) => (
            <div key={blog._id || idx} className="blog-card">
              <div className="blog-image">📝</div>
              <div className="blog-content">
                <h3>{blog.title}</h3>
                <p>{blog.excerpt}</p>
                <Link to={`/blogs/${blog._id || idx}`} className="read-more">Read More →</Link>
              </div>
            </div>
          ))}
          {blogs.length === 0 && (
            <p style={{ textAlign: 'center', gridColumn: '1/-1', color: '#666' }}>No blog posts yet. Check back soon!</p>
          )}
        </div>
      </div>
    </section>
  );
}

function getFallbackBlogs() {
  return [
    { _id: '1', title: 'How to Remove Stubborn Stains at Home', excerpt: 'Learn the best techniques to tackle common stains before bringing your garments to professionals.', content: 'Full article content here...' },
    { _id: '2', title: '5 Tips to Make Your Clothes Last Longer', excerpt: 'Simple habits that can extend the life of your favorite garments and save you money.', content: 'Full article content here...' },
    { _id: '3', title: 'Why Dry Cleaning is Essential for Suits', excerpt: 'Understand why professional dry cleaning is the best choice for your expensive suits and formal wear.', content: 'Full article content here...' },
  ];
}

export default Blogs;
