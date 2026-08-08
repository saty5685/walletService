const express = require('express');
const router = express.Router();
const Offer = require('../models/Offer');
const auth = require('../middleware/auth');

// Get active offers
router.get('/', async (req, res) => {
  try {
    const offers = await Offer.find({ active: true }).sort({ createdAt: -1 });
    res.json(offers);
  } catch (err) {
    res.status(500).json({ message: 'Server error' });
  }
});

// Create offer (admin only)
router.post('/', auth, async (req, res) => {
  try {
    const offer = new Offer(req.body);
    await offer.save();
    res.status(201).json(offer);
  } catch (err) {
    res.status(500).json({ message: 'Server error' });
  }
});

// Update offer (admin only)
router.put('/:id', auth, async (req, res) => {
  try {
    const offer = await Offer.findByIdAndUpdate(req.params.id, req.body, { new: true });
    if (!offer) return res.status(404).json({ message: 'Offer not found' });
    res.json(offer);
  } catch (err) {
    res.status(500).json({ message: 'Server error' });
  }
});

// Delete offer (admin only)
router.delete('/:id', auth, async (req, res) => {
  try {
    await Offer.findByIdAndDelete(req.params.id);
    res.json({ message: 'Offer deleted' });
  } catch (err) {
    res.status(500).json({ message: 'Server error' });
  }
});

module.exports = router;
