/**
 * CampusFind AI - UI Controller & Helper Utilities
 */

const app = {
  init() {
    this.setupNavbar();
    this.setupMobileMenu();
    this.setupGlobalFlashClose();
    this.setupPasswordToggles();
    this.setupChatbot();
    this.setupDynamicApiLinks();
  },

  setupDynamicApiLinks() {
    const config = window.CAMPUSFIND_CONFIG;
    if (config && config.apiBaseUrl && config.apiBaseUrl.startsWith('http')) {
      document.querySelectorAll('a[href^="/api/v1"]').forEach(link => {
        const path = link.getAttribute('href').replace(/^\/api\/v1/, '');
        link.href = `${config.apiBaseUrl}${path}`;
      });
    }
  },

  setupNavbar() {
    const navActions = document.getElementById('navActions');
    if (!navActions) return;

    if (window.auth && window.auth.isAuthenticated()) {
      const user = window.auth.getUser();
      const initial = (user.fullName || user.email || 'U').charAt(0).toUpperCase();
      const isAdmin = window.auth.isAdmin();

      navActions.innerHTML = `
        <div class="user-menu" style="display: flex; align-items: center; gap: 0.65rem;">
          <div class="user-avatar-initials" style="width: 32px; height: 32px; border-radius: 50%; background: var(--primary-gradient); display: flex; align-items: center; justify-content: center; font-weight: 700; color: #fff; font-size: 0.85rem;">
            ${initial}
          </div>
          <span style="font-weight: 600; font-size: 0.9rem; color: #fff;">${this.escapeHtml(user.fullName || user.email)}</span>
          <span class="badge ${isAdmin ? 'badge-lost' : 'badge-found'}" style="font-size: 0.7rem; padding: 0.2rem 0.5rem;">
            ${isAdmin ? 'ADMIN' : 'STUDENT'}
          </span>
        </div>
        ${isAdmin ? `<a href="admin.html" class="btn btn-sm btn-secondary" id="adminNavBtn">⚙️ Admin</a>` : ''}
        <button type="button" class="btn btn-sm btn-outline" id="navLogoutBtn">Sign Out</button>
      `;

      const logoutBtn = document.getElementById('navLogoutBtn');
      if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
          window.auth.logout();
        });
      }
    } else {
      navActions.innerHTML = `
        <a href="login.html" class="btn btn-sm btn-outline" id="navLoginBtn">Sign In</a>
        <a href="register.html" class="btn btn-sm btn-primary" id="navRegisterBtn">Get Started</a>
      `;
    }
  },

  setupMobileMenu() {
    const toggle = document.getElementById('mobileNavToggle');
    const nav = document.querySelector('.navbar');
    if (toggle && nav) {
      toggle.addEventListener('click', () => {
        nav.classList.toggle('mobile-nav-active');
      });
    }
  },

  setupGlobalFlashClose() {
    document.querySelectorAll('.flash-alert').forEach(alert => {
      const close = alert.querySelector('.flash-close');
      if (close) {
        close.addEventListener('click', () => alert.remove());
      }
    });
  },

  setupPasswordToggles() {
    document.querySelectorAll('.btn-toggle-password').forEach(btn => {
      if (btn.dataset.initialized === 'true') return;
      btn.dataset.initialized = 'true';

      btn.addEventListener('click', (e) => {
        e.preventDefault();
        const targetId = btn.getAttribute('data-target');
        const input = targetId ? document.getElementById(targetId) : btn.parentElement.querySelector('input');
        if (!input) return;

        const isPassword = input.type === 'password';
        input.type = isPassword ? 'text' : 'password';

        const newLabel = isPassword ? 'Hide password' : 'Show password';
        btn.setAttribute('aria-label', newLabel);
        btn.setAttribute('title', newLabel);

        const iconShow = btn.querySelector('.eye-icon-show');
        const iconHide = btn.querySelector('.eye-icon-hide');
        if (iconShow && iconHide) {
          iconShow.style.display = isPassword ? 'none' : 'block';
          iconHide.style.display = isPassword ? 'block' : 'none';
        }

        input.focus();
      });
    });
  },

  showNotification(type, message, duration = 6000) {
    const container = document.getElementById('flashNotificationArea');
    if (!container) return;

    const alert = document.createElement('div');
    alert.className = `flash-alert flash-${type}`;
    alert.setAttribute('role', 'alert');
    alert.innerHTML = `
      <span>${this.escapeHtml(message)}</span>
      <button type="button" class="flash-close" aria-label="Close notification">&times;</button>
    `;

    const closeBtn = alert.querySelector('.flash-close');
    closeBtn.addEventListener('click', () => {
      alert.style.opacity = '0';
      setTimeout(() => alert.remove(), 250);
    });

    container.appendChild(alert);

    if (duration > 0) {
      setTimeout(() => {
        if (alert.parentElement) {
          alert.style.opacity = '0';
          setTimeout(() => alert.remove(), 250);
        }
      }, duration);
    }
  },

  escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  },

  formatDate(isoString) {
    if (!isoString) return 'Recent';
    try {
      const d = new Date(isoString);
      return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    } catch {
      return isoString;
    }
  },

  setupDropzone(dropzoneEl, fileInputEl, previewImgEl, previewContainerEl, onBase64Ready) {
    if (!dropzoneEl || !fileInputEl) return;

    const processFile = (file) => {
      if (!file || !file.type.startsWith('image/')) {
        this.showNotification('danger', 'Please select a valid image file (PNG, JPG, JPEG, WEBP).');
        return;
      }
      if (file.size > 16 * 1024 * 1024) {
        this.showNotification('danger', 'Image size must not exceed 16MB.');
        return;
      }

      const reader = new FileReader();
      reader.onload = (e) => {
        const rawDataUrl = e.target.result;

        // Auto-scale & optimize image to max 1024x1024 JPEG for responsive web performance and storage
        const img = new Image();
        img.onload = () => {
          try {
            const maxDim = 1024;
            let width = img.width;
            let height = img.height;

            if (width > maxDim || height > maxDim) {
              if (width > height) {
                height = Math.round((height * maxDim) / width);
                width = maxDim;
              } else {
                width = Math.round((width * maxDim) / height);
                height = maxDim;
              }
            }

            const canvas = document.createElement('canvas');
            canvas.width = width;
            canvas.height = height;
            const ctx = canvas.getContext('2d');
            ctx.drawImage(img, 0, 0, width, height);
            const optimizedDataUrl = canvas.toDataURL('image/jpeg', 0.85);

            if (previewImgEl) previewImgEl.src = optimizedDataUrl;
            if (previewContainerEl) previewContainerEl.style.display = 'block';
            const prompt = document.getElementById('dropzonePrompt');
            if (prompt) prompt.style.display = 'none';

            if (typeof onBase64Ready === 'function') {
              onBase64Ready(optimizedDataUrl);
            }
          } catch (canvasErr) {
            console.warn('Canvas optimization fallback:', canvasErr);
            if (previewImgEl) previewImgEl.src = rawDataUrl;
            if (previewContainerEl) previewContainerEl.style.display = 'block';
            const prompt = document.getElementById('dropzonePrompt');
            if (prompt) prompt.style.display = 'none';

            if (typeof onBase64Ready === 'function') {
              onBase64Ready(rawDataUrl);
            }
          }
        };

        img.onerror = () => {
          if (previewImgEl) previewImgEl.src = rawDataUrl;
          if (previewContainerEl) previewContainerEl.style.display = 'block';
          const prompt = document.getElementById('dropzonePrompt');
          if (prompt) prompt.style.display = 'none';

          if (typeof onBase64Ready === 'function') {
            onBase64Ready(rawDataUrl);
          }
        };

        img.src = rawDataUrl;
      };
      reader.readAsDataURL(file);
    };

    fileInputEl.addEventListener('change', (e) => {
      if (e.target.files && e.target.files[0]) {
        processFile(e.target.files[0]);
      }
    });

    ['dragenter', 'dragover'].forEach(name => {
      dropzoneEl.addEventListener(name, (e) => {
        e.preventDefault();
        dropzoneEl.classList.add('drag-active');
      });
    });

    ['dragleave', 'drop'].forEach(name => {
      dropzoneEl.addEventListener(name, (e) => {
        e.preventDefault();
        dropzoneEl.classList.remove('drag-active');
      });
    });

    dropzoneEl.addEventListener('drop', (e) => {
      if (e.dataTransfer && e.dataTransfer.files.length) {
        processFile(e.dataTransfer.files[0]);
      }
    });
  },

  setupChatbot() {
    if (document.getElementById('cfChatbotWidget')) return;

    const widget = document.createElement('div');
    widget.id = 'cfChatbotWidget';
    widget.innerHTML = `
      <!-- Launcher Button -->
      <button type="button" class="cf-chat-toggle-btn" id="cfChatToggleBtn" aria-label="Open CampusFind AI Assistant" title="CampusFind AI Assistant">
        <svg id="cfChatIconOpen" width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z"></path>
          <path d="M8 12h.01"></path>
          <path d="M12 12h.01"></path>
          <path d="M16 12h.01"></path>
        </svg>
        <svg id="cfChatIconClose" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" style="display: none;">
          <line x1="18" y1="6" x2="6" y2="18"></line>
          <line x1="6" y1="6" x2="18" y2="18"></line>
        </svg>
      </button>

      <!-- Chat Panel -->
      <div class="cf-chat-panel" id="cfChatPanel" role="dialog" aria-label="CampusFind AI Assistant" aria-hidden="true">
        <div class="cf-chat-header">
          <div class="cf-chat-header-title">
            <div class="cf-chat-header-icon">🤖</div>
            <div>
              <div class="cf-chat-header-name">CampusFind Assistant</div>
              <div class="cf-chat-header-sub">
                <span class="cf-chat-status-dot"></span> Online &bull; Groq AI
              </div>
            </div>
          </div>
          <button type="button" class="cf-chat-close-btn" id="cfChatCloseBtn" aria-label="Close Assistant">&times;</button>
        </div>

        <div class="cf-chat-messages" id="cfChatMessages">
          <div class="cf-chat-bubble cf-chat-bubble-assistant">Hello! 👋 I'm your CampusFind AI Assistant. I can help you report lost or found items, understand OpenCLIP visual matching, or navigate the campus catalog. How can I help you today?</div>

          <div class="cf-chat-suggestions" id="cfChatSuggestions">
            <div class="cf-chat-suggestions-title">Suggested questions:</div>
            <button type="button" class="cf-chat-suggestion-chip" data-question="How do I report a lost item?">📢 How do I report a lost item?</button>
            <button type="button" class="cf-chat-suggestion-chip" data-question="How do I search for found items?">📂 How do I search for found items?</button>
            <button type="button" class="cf-chat-suggestion-chip" data-question="How does AI photo matching work?">✨ How does AI photo matching work?</button>
            <button type="button" class="cf-chat-suggestion-chip" data-question="Where is the Campus Security desk?">🛡️ Where is the Campus Security desk?</button>
          </div>

          <div class="cf-chat-typing-indicator" id="cfChatTypingIndicator">
            <div class="cf-chat-typing-dot"></div>
            <div class="cf-chat-typing-dot"></div>
            <div class="cf-chat-typing-dot"></div>
          </div>
        </div>

        <div class="cf-chat-input-box">
          <textarea class="cf-chat-textarea" id="cfChatInput" placeholder="Ask a question..." rows="1" maxlength="1000"></textarea>
          <button type="button" class="cf-chat-send-btn" id="cfChatSendBtn" aria-label="Send message" title="Send message">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <line x1="22" y1="2" x2="11" y2="13"></line>
              <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
            </svg>
          </button>
        </div>
      </div>
    `;

    document.body.appendChild(widget);

    const toggleBtn = document.getElementById('cfChatToggleBtn');
    const closeBtn = document.getElementById('cfChatCloseBtn');
    const panel = document.getElementById('cfChatPanel');
    const iconOpen = document.getElementById('cfChatIconOpen');
    const iconClose = document.getElementById('cfChatIconClose');
    const messagesContainer = document.getElementById('cfChatMessages');
    const suggestionsContainer = document.getElementById('cfChatSuggestions');
    const typingIndicator = document.getElementById('cfChatTypingIndicator');
    const textarea = document.getElementById('cfChatInput');
    const sendBtn = document.getElementById('cfChatSendBtn');

    let history = [];
    let isSending = false;

    const togglePanel = (open) => {
      const isOpen = typeof open === 'boolean' ? open : !panel.classList.contains('cf-chat-open');
      if (isOpen) {
        panel.classList.add('cf-chat-open');
        panel.setAttribute('aria-hidden', 'false');
        iconOpen.style.display = 'none';
        iconClose.style.display = 'block';
        toggleBtn.setAttribute('aria-label', 'Close CampusFind AI Assistant');
        setTimeout(() => textarea.focus(), 150);
      } else {
        panel.classList.remove('cf-chat-open');
        panel.setAttribute('aria-hidden', 'true');
        iconOpen.style.display = 'block';
        iconClose.style.display = 'none';
        toggleBtn.setAttribute('aria-label', 'Open CampusFind AI Assistant');
      }
    };

    toggleBtn.addEventListener('click', () => togglePanel());
    closeBtn.addEventListener('click', () => togglePanel(false));

    const scrollToBottom = () => {
      messagesContainer.scrollTop = messagesContainer.scrollHeight;
    };

    const appendMessage = (role, text) => {
      const bubble = document.createElement('div');
      bubble.className = `cf-chat-bubble cf-chat-bubble-${role}`;
      bubble.textContent = text; // Safe text rendering (prevents XSS)
      messagesContainer.insertBefore(bubble, typingIndicator);
      scrollToBottom();
      return bubble;
    };

    const sendMessage = async (rawText) => {
      const text = (rawText || textarea.value).trim();
      if (!text || isSending) return;

      textarea.value = '';
      textarea.style.height = 'auto';

      if (suggestionsContainer) {
        suggestionsContainer.style.display = 'none';
      }

      appendMessage('user', text);
      isSending = true;
      sendBtn.disabled = true;
      typingIndicator.style.display = 'flex';
      scrollToBottom();

      try {
        const data = await window.api.sendChatMessage(text, history);
        const reply = data.reply || "I didn't receive a response. Please try again.";
        appendMessage('assistant', reply);

        history.push({ role: 'user', content: text });
        history.push({ role: 'assistant', content: reply });
        if (history.length > 6) {
          history = history.slice(history.length - 6);
        }

      } catch (err) {
        appendMessage('assistant', err.message || 'Unable to connect to the assistant. Please try again later.');
      } finally {
        isSending = false;
        sendBtn.disabled = false;
        typingIndicator.style.display = 'none';
        scrollToBottom();
        textarea.focus();
      }
    };

    sendBtn.addEventListener('click', () => sendMessage());

    textarea.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
      }
    });

    textarea.addEventListener('input', () => {
      textarea.style.height = 'auto';
      textarea.style.height = Math.min(textarea.scrollHeight, 100) + 'px';
    });

    // Handle suggestion chips
    document.querySelectorAll('.cf-chat-suggestion-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        const question = chip.getAttribute('data-question');
        if (question) {
          sendMessage(question);
        }
      });
    });
  }
};

document.addEventListener('DOMContentLoaded', () => {
  app.init();
});

window.app = app;
