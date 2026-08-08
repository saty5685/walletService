const express = require('express');
const router = express.Router();
const PricingItem = require('../models/Pricing');
const auth = require('../middleware/auth');

// Get all pricing items
router.get('/', async (req, res) => {
  try {
    const items = await PricingItem.find().sort({ category: 1, garment: 1 });
    res.json(items);
  } catch (err) {
    res.status(500).json({ message: 'Server error' });
  }
});

// Get by category
router.get('/category/:category', async (req, res) => {
  try {
    const items = await PricingItem.find({ category: req.params.category });
    res.json(items);
  } catch (err) {
    res.status(500).json({ message: 'Server error' });
  }
});

// Create pricing item (admin only)
router.post('/', auth, async (req, res) => {
  try {
    const item = new PricingItem(req.body);
    await item.save();
    res.status(201).json(item);
  } catch (err) {
    res.status(500).json({ message: 'Server error' });
  }
});

// Update pricing item (admin only)
router.put('/:id', auth, async (req, res) => {
  try {
    const item = await PricingItem.findByIdAndUpdate(req.params.id, req.body, { new: true });
    if (!item) return res.status(404).json({ message: 'Item not found' });
    res.json(item);
  } catch (err) {
    res.status(500).json({ message: 'Server error' });
  }
});

// Delete pricing item (admin only)
router.delete('/:id', auth, async (req, res) => {
  try {
    await PricingItem.findByIdAndDelete(req.params.id);
    res.json({ message: 'Item deleted' });
  } catch (err) {
    res.status(500).json({ message: 'Server error' });
  }
});

// Seed pricing data
router.post('/seed', async (req, res) => {
  try {
    const count = await PricingItem.countDocuments();
    if (count > 0) return res.json({ message: 'Pricing data already exists' });

    const seedData = [
      { garment: 'Shirt', laundry: 30, dryCleaning: 60, steamIroning: 20, category: 'men' },
      { garment: 'Trousers', laundry: 40, dryCleaning: 70, steamIroning: 25, category: 'men' },
      { garment: 'Suit (2 piece)', laundry: 0, dryCleaning: 250, steamIroning: 80, category: 'men' },
      { garment: 'Blazer', laundry: 0, dryCleaning: 200, steamIroning: 60, category: 'men' },
      { garment: 'T-Shirt', laundry: 25, dryCleaning: 50, steamIroning: 15, category: 'men' },
      { garment: 'Jeans', laundry: 45, dryCleaning: 80, steamIroning: 30, category: 'men' },
      { garment: 'Jacket', laundry: 0, dryCleaning: 300, steamIroning: 80, category: 'men' },
      { garment: 'Kurta', laundry: 35, dryCleaning: 80, steamIroning: 25, category: 'men' },
      { garment: 'Sherwani', laundry: 0, dryCleaning: 500, steamIroning: 100, category: 'men' },
      { garment: 'Saree', laundry: 60, dryCleaning: 150, steamIroning: 50, category: 'women' },
      { garment: 'Blouse', laundry: 25, dryCleaning: 50, steamIroning: 15, category: 'women' },
      { garment: 'Salwar Suit', laundry: 50, dryCleaning: 120, steamIroning: 40, category: 'women' },
      { garment: 'Lehenga', laundry: 0, dryCleaning: 600, steamIroning: 150, category: 'women' },
      { garment: 'Dress', laundry: 50, dryCleaning: 120, steamIroning: 40, category: 'women' },
      { garment: 'Skirt', laundry: 35, dryCleaning: 70, steamIroning: 25, category: 'women' },
      { garment: 'Top', laundry: 25, dryCleaning: 50, steamIroning: 15, category: 'women' },
      { garment: 'Kurti', laundry: 30, dryCleaning: 70, steamIroning: 20, category: 'women' },
      { garment: 'Bedsheet (Single)', laundry: 40, dryCleaning: 80, steamIroning: 30, category: 'household' },
      { garment: 'Bedsheet (Double)', laundry: 60, dryCleaning: 100, steamIroning: 40, category: 'household' },
      { garment: 'Curtain (per piece)', laundry: 80, dryCleaning: 150, steamIroning: 50, category: 'household' },
      { garment: 'Carpet (per sq ft)', laundry: 0, dryCleaning: 25, steamIroning: 0, category: 'household' },
      { garment: 'Blanket', laundry: 100, dryCleaning: 200, steamIroning: 0, category: 'household' },
      { garment: 'Pillow Cover', laundry: 20, dryCleaning: 40, steamIroning: 10, category: 'household' },
      { garment: 'Shoes (per pair)', laundry: 0, dryCleaning: 250, steamIroning: 0, category: 'accessories' },
      { garment: 'Leather Bag', laundry: 0, dryCleaning: 400, steamIroning: 0, category: 'accessories' },
      { garment: 'Leather Jacket', laundry: 0, dryCleaning: 500, steamIroning: 0, category: 'accessories' },
      { garment: 'Tie', laundry: 0, dryCleaning: 50, steamIroning: 20, category: 'accessories' },
      { garment: 'Scarf/Stole', laundry: 30, dryCleaning: 60, steamIroning: 20, category: 'accessories' },
    ];

    await PricingItem.insertMany(seedData);
    res.json({ message: 'Pricing data seeded successfully' });
  } catch (err) {
    res.status(500).json({ message: 'Server error' });
  }
});

module.exports = router;
