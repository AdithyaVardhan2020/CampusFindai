/**
 * CampusFind AI - Unified REST API Client
 * Wraps fetch calls with automatic HTTP Basic headers, timeout handling,
 * and unified error parsing.
 */
const api = {
  get baseUrl() {
    return window.CAMPUSFIND_CONFIG ? window.CAMPUSFIND_CONFIG.apiBaseUrl : '/api/v1';
  },

  async request(path, options = {}) {
    const url = path.startsWith('http') ? path : `${this.baseUrl}${path.startsWith('/') ? '' : '/'}${path}`;
    const headers = {
      'Accept': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
      ...options.headers
    };

    if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(options.body);
    }

    const authHeader = window.auth ? window.auth.getBasicHeader() : null;
    if (authHeader && !headers['Authorization']) {
      headers['Authorization'] = authHeader;
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), options.timeout || 12000);

    try {
      const response = await fetch(url, {
        ...options,
        headers,
        signal: controller.signal
      });
      clearTimeout(timeoutId);

      if (response.status === 204) {
        return null;
      }

      const contentType = response.headers.get('content-type') || '';
      const isJson = contentType.includes('application/json');
      const data = isJson ? await response.json().catch(() => ({})) : await response.text();

      if (!response.ok) {
        let message = `Request failed (HTTP ${response.status})`;
        if (typeof data === 'object' && data !== null) {
          if (data.fieldErrors) {
            message = Object.entries(data.fieldErrors)
              .map(([f, msg]) => `${f}: ${msg}`)
              .join('; ');
          } else if (data.message) {
            message = data.message;
          }
        }

        if (response.status === 401) {
          message = 'Authentication required or session expired. Please sign in.';
        } else if (response.status === 403) {
          message = 'Access denied. You do not have permission for this resource.';
        } else if (response.status === 503) {
          message = data.message || 'AI Matching Service is currently unavailable. Please try again shortly.';
        }

        const err = new Error(message);
        err.status = response.status;
        err.data = data;
        throw err;
      }

      return data;
    } catch (err) {
      clearTimeout(timeoutId);
      if (err.name === 'AbortError') {
        throw new Error('Network request timed out. Please check your connection or service status.');
      }
      throw err;
    }
  },

  get(path, options = {}) {
    return this.request(path, { ...options, method: 'GET' });
  },

  post(path, data, options = {}) {
    return this.request(path, { ...options, method: 'POST', body: data });
  },

  patch(path, data, options = {}) {
    return this.request(path, { ...options, method: 'PATCH', body: data });
  },

  delete(path, options = {}) {
    return this.request(path, { ...options, method: 'DELETE' });
  },

  // Health APIs
  checkHealth() {
    return this.get('/health');
  },

  checkAiHealth() {
    return this.get('/reports/ai-health');
  },

  // Reports APIs
  getReports(params = {}) {
    const query = new URLSearchParams();
    if (params.type) query.append('type', params.type.toUpperCase());
    if (params.status) query.append('status', params.status.toUpperCase());
    const queryString = query.toString();
    return this.get(`/reports${queryString ? '?' + queryString : ''}`);
  },

  getReport(id) {
    return this.get(`/reports/${id}`);
  },

  createReport(data) {
    return this.post('/reports', data);
  },

  updateReportStatus(id, status) {
    return this.patch(`/reports/${id}/status`, { status: status.toUpperCase() });
  },

  deleteReport(id) {
    return this.delete(`/reports/${id}`);
  },

  getMatches(reportId) {
    return this.get(`/reports/${reportId}/matches`);
  },

  attachEmbedding(reportId, imageBase64) {
    return this.post(`/reports/${reportId}/embedding`, { image_base64: imageBase64 });
  },

  // Admin APIs
  getAdminUsers() {
    return this.get('/admin/users');
  },

  getAdminReports() {
    return this.get('/admin/reports');
  }
};

window.api = api;
