/**
 * CampusFind AI - Interactive Client Scripts
 */

document.addEventListener('DOMContentLoaded', () => {
  // 1. Mobile Menu Toggle
  const mobileToggle = document.getElementById('mobileNavToggle');
  const navContainer = document.querySelector('.navbar');
  if (mobileToggle && navContainer) {
    mobileToggle.addEventListener('click', () => {
      navContainer.classList.toggle('mobile-nav-active');
    });
  }

  // 2. Flash Message Auto-Dismiss & Close
  document.querySelectorAll('.flash-alert').forEach((alert) => {
    const closeBtn = alert.querySelector('.flash-close');
    if (closeBtn) {
      closeBtn.addEventListener('click', () => {
        alert.style.opacity = '0';
        setTimeout(() => alert.remove(), 250);
      });
    }

    // Auto dismiss after 6 seconds
    setTimeout(() => {
      if (alert && alert.parentElement) {
        alert.style.opacity = '0';
        setTimeout(() => alert.remove(), 250);
      }
    }, 6000);
  });

  // 3. Image Upload Dropzone & Live Preview
  const imageInput = document.getElementById('imageUploadInput');
  const previewContainer = document.getElementById('imagePreviewContainer');
  const previewImage = document.getElementById('imagePreviewElement');
  const dropzonePrompt = document.getElementById('dropzonePrompt');

  if (imageInput && previewContainer && previewImage) {
    imageInput.addEventListener('change', function () {
      const file = this.files[0];
      if (file) {
        const reader = new FileReader();
        reader.onload = function (e) {
          previewImage.src = e.target.result;
          previewContainer.style.display = 'block';
          if (dropzonePrompt) dropzonePrompt.style.display = 'none';
        };
        reader.readAsDataURL(file);
      }
    });

    // Drag-over styling
    const dropzone = document.getElementById('uploadDropzone');
    if (dropzone) {
      ['dragenter', 'dragover'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
          e.preventDefault();
          dropzone.classList.add('drag-active');
        }, false);
      });
      ['dragleave', 'drop'].forEach(eventName => {
        dropzone.addEventListener(eventName, (e) => {
          e.preventDefault();
          dropzone.classList.remove('drag-active');
        }, false);
      });
      dropzone.addEventListener('drop', (e) => {
        const dt = e.dataTransfer;
        const files = dt.files;
        if (files.length) {
          imageInput.files = files;
          const event = new Event('change');
          imageInput.dispatchEvent(event);
        }
      });
    }
  }

  // 4. Tab Switching for Dashboard
  const tabButtons = document.querySelectorAll('.tab-btn');
  const tabPanes = document.querySelectorAll('.tab-pane');

  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetId = btn.getAttribute('data-target');
      
      tabButtons.forEach(b => b.classList.remove('active'));
      tabPanes.forEach(p => p.style.display = 'none');

      btn.classList.add('active');
      const targetPane = document.getElementById(targetId);
      if (targetPane) {
        targetPane.style.display = 'block';
      }
    });
  });

  // 5. Form Confirmation Prompts
  document.querySelectorAll('form[data-confirm]').forEach(form => {
    form.addEventListener('submit', (e) => {
      const message = form.getAttribute('data-confirm') || 'Are you sure?';
      if (!confirm(message)) {
        e.preventDefault();
      }
    });
  });
});
