const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const rateLimit = require('express-rate-limit');
const Admin = require('../models/Admin');

const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 10,
  message: { message: 'Too many login attempts, please try again later.' }
});

// Login
router.post('/login', loginLimiter, async (req, res) => {
  try {
    const { email, password } = req.body;
    const admin = await Admin.findOne({ email });
    if (!admin) return res.status(400).json({ message: 'Invalid credentials' });

    const isMatch = await admin.comparePassword(password);
    if (!isMatch) return res.status(400).json({ message: 'Invalid credentials' });

    const token = jwt.sign(
      { id: admin._id, email: admin.email },
      process.env.JWT_SECRET || 'defaultsecret',
      { expiresIn: '7d' }
    );

    res.json({ token, admin: { id: admin._id, email: admin.email, name: admin.name } });
  } catch (err) {
    res.status(500).json({ message: 'Server error' });
  }
});

// Seed admin (run once)
router.post('/seed', async (req, res) => {
  try {
    const existing = await Admin.findOne({ email: 'admin@laundry.com' });
    if (existing) return res.json({ message: 'Admin already exists' });

    const admin = new Admin({
      email: 'admin@laundry.com',
      password: 'admin123',
      name: 'Store Admin'
    });
    await admin.save();
    res.json({ message: 'Admin created successfully' });
  } catch (err) {
    res.status(500).json({ message: 'Server error' });
  }
});

module.exports = router;
