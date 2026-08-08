const mongoose = require('mongoose');

const pricingItemSchema = new mongoose.Schema({
  garment: { type: String, required: true },
  laundry: { type: Number, default: 0 },
  dryCleaning: { type: Number, default: 0 },
  steamIroning: { type: Number, default: 0 },
  category: { 
    type: String, 
    enum: ['men', 'women', 'household', 'accessories'],
    required: true 
  }
}, { timestamps: true });

module.exports = mongoose.model('PricingItem', pricingItemSchema);
