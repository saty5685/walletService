# FreshPress - Laundry & Dry Cleaning Website

A full-stack MERN (MongoDB, Express, React, Node.js) web application for a laundry and dry cleaning store.

## Features

### Customer-Facing
- **Home Page** — Hero section with WhatsApp chat and Schedule Free Pickup buttons
- **Services** — Detailed pages for all 7 services (Laundry, Dry Cleaning, Shoe Cleaning, Carpet Cleaning, Curtain Cleaning, Leather Cleaning, Steam Ironing)
- **Pricing** — Dynamic pricing table with category tabs (Men, Women, Household, Accessories)
- **Blog** — Blog section with articles on garment care
- **Store Locator** — Google Maps integration showing store location
- **WhatsApp Integration** — Floating WhatsApp button for instant chat
- **Offer Banner** — Top offer bar displaying current promotions

### Admin Panel
- **Admin Login** — Secure JWT-based authentication
- **Manage Pricing** — Add, edit, delete pricing items
- **Write Blogs** — Create and manage blog posts
- **Manage Offers** — Add promotional offers displayed on the top banner

## Tech Stack
- **Frontend:** React 18, React Router, Axios, React Toastify
- **Backend:** Node.js, Express.js, MongoDB, Mongoose
- **Auth:** JWT (JSON Web Tokens), bcrypt
- **Styling:** Custom CSS with responsive design

## Getting Started

### Prerequisites
- Node.js (v16+)
- MongoDB (local or Atlas)

### Backend Setup
```bash
cd backend
cp .env.example .env    # Edit with your MongoDB URI and JWT secret
npm install
npm run dev
```

### Frontend Setup
```bash
cd frontend
npm install
npm start
```

### Seed Initial Data
After starting the backend, run these API calls:
```bash
# Create admin user
curl -X POST http://localhost:5000/api/auth/seed

# Seed pricing data
curl -X POST http://localhost:5000/api/pricing/seed
```

### Admin Credentials (default)
- Email: `admin@laundry.com`
- Password: `admin123`

## Project Structure
```
├── backend/
│   ├── models/         # MongoDB schemas (Admin, Pricing, Blog, Offer)
│   ├── routes/         # API routes (auth, pricing, blogs, offers)
│   ├── middleware/     # JWT auth middleware
│   └── server.js       # Express app entry point
├── frontend/
│   ├── public/         # Static HTML
│   └── src/
│       ├── components/ # Navbar, Footer, WhatsApp button
│       ├── pages/      # Home, Services, Pricing, Blogs, Store Locator
│       ├── admin/      # Admin Login & Dashboard
│       └── context/    # Auth context
└── README.md
```

## Customization
- Update WhatsApp number in `frontend/src/components/WhatsAppButton.js` and `Home.js`
- Update store address/map in `frontend/src/pages/StoreLocator.js`
- Update brand colors in `frontend/src/index.css` (`:root` variables)
- Update store contact info in `frontend/src/components/Footer.js`
