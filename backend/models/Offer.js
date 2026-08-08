const mongoose = require('mongoose');

const offerSchema = new mongoose.Schema({
  title: { type: String, required: true },
  description: { type: String, required: true },
  code: { type: String },
  discount: { type: String },
  active: { type: Boolean, default: true },
  expiresAt: { type: Date }
}, { timestamps: true });

module.exports = mongoose.model('Offer', offerSchema);
