import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { toast } from 'react-toastify';
import axios from 'axios';

const API = process.env.REACT_APP_API_URL || 'http://localhost:5000/api';

function AdminDashboard() {
  const { token, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('pricing');
  const [pricingItems, setPricingItems] = useState([]);
  const [blogs, setBlogs] = useState([]);
  const [offers, setOffers] = useState([]);

  // Form states
  const [pricingForm, setPricingForm] = useState({ garment: '', laundry: '', dryCleaning: '', steamIroning: '', category: 'men' });
  const [blogForm, setBlogForm] = useState({ title: '', content: '', excerpt: '' });
  const [offerForm, setOfferForm] = useState({ title: '', description: '', code: '', discount: '' });
  const [editingId, setEditingId] = useState(null);

  const headers = { Authorization: 'Bearer ' + token };

  useEffect(() => {
    if (!isAuthenticated) { navigate('/admin/login'); return; }
    fetchData();
  }, [isAuthenticated, navigate]);

  const fetchData = async () => {
    try {
      const [p, b, o] = await Promise.all([
        axios.get(`${API}/pricing`),
        axios.get(`${API}/blogs`),
        axios.get(`${API}/offers`)
      ]);
      setPricingItems(p.data);
      setBlogs(b.data);
      setOffers(o.data);
    } catch (err) {
      console.error('Failed to fetch data');
    }
  };

  // Pricing CRUD
  const handlePricingSubmit = async (e) => {
    e.preventDefault();
    try {
      if (editingId) {
        await axios.put(`${API}/pricing/${editingId}`, pricingForm, { headers });
        toast.success('Pricing updated!');
      } else {
        await axios.post(`${API}/pricing`, pricingForm, { headers });
        toast.success('Pricing item added!');
      }
      setPricingForm({ garment: '', laundry: '', dryCleaning: '', steamIroning: '', category: 'men' });
      setEditingId(null);
      fetchData();
    } catch (err) {
      toast.error('Failed to save pricing');
    }
  };

  const deletePricing = async (id) => {
    if (!window.confirm('Delete this item?')) return;
    try {
      await axios.delete(`${API}/pricing/${id}`, { headers });
      toast.success('Deleted!');
      fetchData();
    } catch (err) { toast.error('Delete failed'); }
  };

  // Blog CRUD
  const handleBlogSubmit = async (e) => {
    e.preventDefault();
    try {
      if (editingId) {
        await axios.put(`${API}/blogs/${editingId}`, blogForm, { headers });
        toast.success('Blog updated!');
      } else {
        await axios.post(`${API}/blogs`, blogForm, { headers });
        toast.success('Blog published!');
      }
      setBlogForm({ title: '', content: '', excerpt: '' });
      setEditingId(null);
      fetchData();
    } catch (err) { toast.error('Failed to save blog'); }
  };

  const deleteBlog = async (id) => {
    if (!window.confirm('Delete this blog?')) return;
    try {
      await axios.delete(`${API}/blogs/${id}`, { headers });
      toast.success('Deleted!');
      fetchData();
    } catch (err) { toast.error('Delete failed'); }
  };

  // Offer CRUD
  const handleOfferSubmit = async (e) => {
    e.preventDefault();
    try {
      if (editingId) {
        await axios.put(`${API}/offers/${editingId}`, offerForm, { headers });
        toast.success('Offer updated!');
      } else {
        await axios.post(`${API}/offers`, offerForm, { headers });
        toast.success('Offer added!');
      }
      setOfferForm({ title: '', description: '', code: '', discount: '' });
      setEditingId(null);
      fetchData();
    } catch (err) { toast.error('Failed to save offer'); }
  };

  const deleteOffer = async (id) => {
    if (!window.confirm('Delete this offer?')) return;
    try {
      await axios.delete(`${API}/offers/${id}`, { headers });
      toast.success('Deleted!');
      fetchData();
    } catch (err) { toast.error('Delete failed'); }
  };

  if (!isAuthenticated) return null;

  return (
    <div className="admin-container">
      <h2 style={{ marginBottom: '20px' }}>📊 Admin Dashboard</h2>
      <div className="admin-tabs">
        <button className={`admin-tab ${activeTab === 'pricing' ? 'active' : ''}`} onClick={() => { setActiveTab('pricing'); setEditingId(null); }}>Pricing</button>
        <button className={`admin-tab ${activeTab === 'blogs' ? 'active' : ''}`} onClick={() => { setActiveTab('blogs'); setEditingId(null); }}>Blogs</button>
        <button className={`admin-tab ${activeTab === 'offers' ? 'active' : ''}`} onClick={() => { setActiveTab('offers'); setEditingId(null); }}>Offers</button>
      </div>

      {/* Pricing Tab */}
      {activeTab === 'pricing' && (
        <div>
          <form onSubmit={handlePricingSubmit} style={{ background: '#f8f9fa', padding: '20px', borderRadius: '8px', marginBottom: '30px' }}>
            <h3 style={{ marginBottom: '15px' }}>{editingId ? 'Edit' : 'Add'} Pricing Item</h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '15px' }}>
              <div className="form-group">
                <label>Garment</label>
                <input value={pricingForm.garment} onChange={e => setPricingForm({...pricingForm, garment: e.target.value})} required />
              </div>
              <div className="form-group">
                <label>Laundry (₹)</label>
                <input type="number" value={pricingForm.laundry} onChange={e => setPricingForm({...pricingForm, laundry: e.target.value})} />
              </div>
              <div className="form-group">
                <label>Dry Cleaning (₹)</label>
                <input type="number" value={pricingForm.dryCleaning} onChange={e => setPricingForm({...pricingForm, dryCleaning: e.target.value})} />
              </div>
              <div className="form-group">
                <label>Steam Ironing (₹)</label>
                <input type="number" value={pricingForm.steamIroning} onChange={e => setPricingForm({...pricingForm, steamIroning: e.target.value})} />
              </div>
              <div className="form-group">
                <label>Category</label>
                <select value={pricingForm.category} onChange={e => setPricingForm({...pricingForm, category: e.target.value})}>
                  <option value="men">Men</option>
                  <option value="women">Women</option>
                  <option value="household">Household</option>
                  <option value="accessories">Accessories</option>
                </select>
              </div>
            </div>
            <button type="submit" className="btn-submit" style={{ width: 'auto', padding: '10px 30px', marginTop: '10px' }}>
              {editingId ? 'Update' : 'Add Item'}
            </button>
          </form>
          {pricingItems.map(item => (
            <div key={item._id} className="admin-item">
              <div>
                <strong>{item.garment}</strong> ({item.category}) — L:₹{item.laundry} | DC:₹{item.dryCleaning} | SI:₹{item.steamIroning}
              </div>
              <div className="admin-item-actions">
                <button className="btn-edit" onClick={() => { setEditingId(item._id); setPricingForm(item); }}>Edit</button>
                <button className="btn-delete" onClick={() => deletePricing(item._id)}>Delete</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Blogs Tab */}
      {activeTab === 'blogs' && (
        <div>
          <form onSubmit={handleBlogSubmit} style={{ background: '#f8f9fa', padding: '20px', borderRadius: '8px', marginBottom: '30px' }}>
            <h3 style={{ marginBottom: '15px' }}>{editingId ? 'Edit' : 'Write New'} Blog</h3>
            <div className="form-group">
              <label>Title</label>
              <input value={blogForm.title} onChange={e => setBlogForm({...blogForm, title: e.target.value})} required />
            </div>
            <div className="form-group">
              <label>Excerpt (short description)</label>
              <input value={blogForm.excerpt} onChange={e => setBlogForm({...blogForm, excerpt: e.target.value})} required />
            </div>
            <div className="form-group">
              <label>Content</label>
              <textarea value={blogForm.content} onChange={e => setBlogForm({...blogForm, content: e.target.value})} required rows="8" />
            </div>
            <button type="submit" className="btn-submit" style={{ width: 'auto', padding: '10px 30px' }}>
              {editingId ? 'Update Blog' : 'Publish Blog'}
            </button>
          </form>
          {blogs.map(blog => (
            <div key={blog._id} className="admin-item">
              <div><strong>{blog.title}</strong></div>
              <div className="admin-item-actions">
                <button className="btn-edit" onClick={() => { setEditingId(blog._id); setBlogForm(blog); }}>Edit</button>
                <button className="btn-delete" onClick={() => deleteBlog(blog._id)}>Delete</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Offers Tab */}
      {activeTab === 'offers' && (
        <div>
          <form onSubmit={handleOfferSubmit} style={{ background: '#f8f9fa', padding: '20px', borderRadius: '8px', marginBottom: '30px' }}>
            <h3 style={{ marginBottom: '15px' }}>{editingId ? 'Edit' : 'Add'} Offer</h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '15px' }}>
              <div className="form-group">
                <label>Title</label>
                <input value={offerForm.title} onChange={e => setOfferForm({...offerForm, title: e.target.value})} required />
              </div>
              <div className="form-group">
                <label>Description</label>
                <input value={offerForm.description} onChange={e => setOfferForm({...offerForm, description: e.target.value})} required />
              </div>
              <div className="form-group">
                <label>Coupon Code</label>
                <input value={offerForm.code} onChange={e => setOfferForm({...offerForm, code: e.target.value})} />
              </div>
              <div className="form-group">
                <label>Discount</label>
                <input value={offerForm.discount} onChange={e => setOfferForm({...offerForm, discount: e.target.value})} placeholder="e.g., 20% OFF" />
              </div>
            </div>
            <button type="submit" className="btn-submit" style={{ width: 'auto', padding: '10px 30px', marginTop: '10px' }}>
              {editingId ? 'Update Offer' : 'Add Offer'}
            </button>
          </form>
          {offers.map(offer => (
            <div key={offer._id} className="admin-item">
              <div><strong>{offer.title}</strong> — {offer.description} {offer.code && `(Code: ${offer.code})`}</div>
              <div className="admin-item-actions">
                <button className="btn-edit" onClick={() => { setEditingId(offer._id); setOfferForm(offer); }}>Edit</button>
                <button className="btn-delete" onClick={() => deleteOffer(offer._id)}>Delete</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default AdminDashboard;
