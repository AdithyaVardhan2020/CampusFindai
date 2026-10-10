/**
 * CampusFind AI - Configuration
 * Environment-adaptable API configuration for local and deployed setups.
 */
window.CAMPUSFIND_CONFIG = {
  apiBaseUrl: window.API_BASE_URL || '/api/v1',
  appName: 'CampusFind AI',
  similarityThreshold: 0.60,
  defaultCategories: [
    'Bags',
    'Phones',
    'Laptops',
    'Electronics',
    'Wallets',
    'Keys',
    'ID Cards',
    'Books',
    'Clothing',
    'Accessories',
    'Other'
  ]
};
