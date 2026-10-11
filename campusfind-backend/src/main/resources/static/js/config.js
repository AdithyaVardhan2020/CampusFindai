/**
 * CampusFind AI - Frontend Configuration
 * Environment-adaptable API configuration for local development and Vercel hosting.
 *
 * NOTE FOR VERCEL DEPLOYMENT:
 * Pure static frontends do NOT inherit server-side environment variables into browser JS.
 * This configuration reliably resolves the backend URL through:
 * 1. Runtime override via window.CAMPUSFIND_BACKEND_URL or window.API_BASE_URL
 * 2. Persistent browser override via localStorage ('CAMPUSFIND_API_BASE_URL')
 * 3. Domain detection: If on *.vercel.app, defaults to RENDER_BACKEND_URL
 * 4. Localhost: Defaults to same-origin relative '/api/v1'
 * 5. Console utility: Call setBackendUrl('https://<your-render-name>.onrender.com/api/v1')
 */
(function() {
  // Default Render production backend endpoint (connected to live Render service)
  const RENDER_BACKEND_URL = 'https://campusfind-backend-wary.onrender.com/api/v1';

  let resolvedApiBaseUrl = '/api/v1';

  if (typeof window !== 'undefined' && window.location) {
    const hostname = window.location.hostname;
    const storedBase = localStorage.getItem('CAMPUSFIND_API_BASE_URL');

    if (storedBase && storedBase.trim() !== '') {
      resolvedApiBaseUrl = storedBase.trim();
    } else if (window.CAMPUSFIND_BACKEND_URL && window.CAMPUSFIND_BACKEND_URL.trim() !== '') {
      resolvedApiBaseUrl = window.CAMPUSFIND_BACKEND_URL.trim();
    } else if (window.API_BASE_URL && window.API_BASE_URL.trim() !== '') {
      resolvedApiBaseUrl = window.API_BASE_URL.trim();
    } else if (hostname && (hostname.endsWith('vercel.app') || (!hostname.includes('localhost') && !hostname.includes('127.0.0.1')))) {
      // Deployed statically on Vercel: route directly to Render backend
      resolvedApiBaseUrl = RENDER_BACKEND_URL;
    }
  }

  window.CAMPUSFIND_CONFIG = {
    apiBaseUrl: resolvedApiBaseUrl,
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

  /**
   * Helper utility for evaluators and developers to switch backend target
   * directly from browser console without redeployment.
   * Usage: setBackendUrl('https://your-service.onrender.com/api/v1')
   * Reset: setBackendUrl(null)
   */
  window.setBackendUrl = function(url) {
    if (!url || !url.trim()) {
      localStorage.removeItem('CAMPUSFIND_API_BASE_URL');
      console.log('[CampusFind AI] Reset API Base URL to default:', resolvedApiBaseUrl);
    } else {
      localStorage.setItem('CAMPUSFIND_API_BASE_URL', url.trim());
      console.log('[CampusFind AI] Set API Base URL to:', url.trim());
    }
    window.location.reload();
  };

  if (typeof console !== 'undefined' && console.log) {
    console.log('[CampusFind AI] Active API Base URL:', window.CAMPUSFIND_CONFIG.apiBaseUrl);
  }
})();
