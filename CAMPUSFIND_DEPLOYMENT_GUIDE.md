# CampusFind AI — Step-by-Step Production Deployment Guide
**Zero-Budget Multi-Cloud Deployment: Render Free (Backend + ONNX AI) & Vercel Hobby (Frontend)**

---

## Overview

This guide provides clear, step-by-step instructions to deploy **CampusFind AI** to the cloud at **strictly ₹0 cost**, without requiring your personal computer to stay running.

### Architecture Summary:
- **Frontend**: Hosted on **Vercel Hobby** (Free static hosting, global CDN, 0 ms cold start).
- **Backend & AI**: Hosted on **Render Free** (ONE Spring Boot Docker web service with in-process ONNX Runtime Java OpenCLIP AI).
- **Database**: Existing **Aiven Cloud MySQL** (Free tier).
- **Chatbot**: Existing **Groq Cloud API** (Free tier).
- **Total Cost**: **₹0 / month**.

---

## Pre-Deployment Checklist

Before proceeding, ensure you have:
1. A GitHub account with access to repository `AdithyaVardhan2020/CampusFindai`.
2. A free account on [Render.com](https://render.com/).
3. A free account on [Vercel.com](https://vercel.com/).
4. Your Aiven MySQL database connection parameters (host, port, database, user, password).
5. Your Groq API key (`gsk_...`).

---

## STEP 1: Push Reviewed Code to GitHub

> [!IMPORTANT]
> Verify that no credentials or secret scripts (such as `aiven-env.ps1`) are staged before committing. The `.gitignore` has already been configured to protect these files.

1. Open your terminal in the project directory (`D:\CampusFindai`).
2. Check the git status to confirm only intended files are tracked:
   ```bash
   git status
   ```
3. Stage the integration changes:
   ```bash
   git add campusfind-backend/pom.xml
   git add campusfind-backend/src/
   git add campusfind-backend/models/openclip_vit_b32_int8.onnx
   git add campusfind-backend/Dockerfile
   git add campusfind-backend/.dockerignore
   git add render.yaml
   git add vercel.json
   git add CAMPUSFIND_PRODUCTION_READINESS_REPORT.md
   git add CAMPUSFIND_DEPLOYMENT_GUIDE.md
   ```
4. Commit the changes:
   ```bash
   git commit -m "feat: integrate embedded ONNX Runtime OpenCLIP AI and prepare Render/Vercel zero-budget deployment"
   ```
5. Push to your GitHub repository:
   ```bash
   git push origin master
   ```

---

## STEP 2: Deploy Spring Boot Docker Service to Render Free

1. Log in to [Render Dashboard](https://dashboard.render.com/).
2. Click **New +** in the top right corner and select **Web Service**.
3. Choose **Build and deploy from a Git repository** and connect `AdithyaVardhan2020/CampusFindai`.
4. Configure the service settings:
   - **Name**: `campusfind-backend`
   - **Region**: Oregon (US West) or Frankfurt (choose the region closest to your Aiven database).
   - **Branch**: `master`
   - **Root Directory**: `campusfind-backend`
   - **Runtime**: **Docker**
   - **Instance Type**: **Free** (512 MB RAM, 0.1 CPU).
5. Leave Dockerfile Path as `Dockerfile` (or `Dockerfile` inside `campusfind-backend`).

---

## STEP 3: Configure Render Environment Variables

In the Render service creation screen (or under **Environment** settings after creation), add the following environment variables:

| Key | Value / Instructions |
| :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://<your-aiven-host>:<port>/<dbname>?sslMode=REQUIRED` |
| `SPRING_DATASOURCE_USERNAME` | `<your-aiven-username>` |
| `SPRING_DATASOURCE_PASSWORD` | `<your-aiven-password>` |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME`| `com.mysql.cj.jdbc.Driver` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` (Protects existing Aiven records; never alters schema) |
| `CAMPUSFIND_AI_ONNX_ENABLED` | `true` |
| `CAMPUSFIND_AI_MODEL_PATH` | `/app/models/openclip_vit_b32_int8.onnx` |
| `CAMPUSFIND_AI_CONCURRENCY_LIMIT` | `3` |
| `CAMPUSFIND_CORS_ALLOWED_ORIGINS` | `https://<your-exact-subdomain>.vercel.app` (Spring Boot automatically preserves localhost development access) |
| `GROQ_API_KEY` | `<your-groq-api-key>` |
| `GROQ_MODEL_ID` | `llama-3.1-8b-instant` |
| `GROQ_API_URL` | `https://api.groq.com/openai/v1/chat/completions` |
| `CAMPUSFIND_ADMIN_EMAIL` | `admin@campus.edu` (or your preferred admin email) |
| `CAMPUSFIND_ADMIN_PASSWORD` | `<choose-a-strong-admin-password>` |
| `CAMPUSFIND_ADMIN_NAME` | `Campus Administrator` |
| `CAMPUSFIND_ADMIN_RESET_PASSWORD` | `false` |

Click **Create Web Service** (or **Save Changes**). Render will now build the Docker image, package the ONNX model, and launch Spring Boot.

---

## STEP 4: Verify Backend Health and AI Matching

Once the Render build completes and status changes to **Live**:

1. Note your Render service public URL (e.g., `https://campusfind-backend.onrender.com`).
2. Test the core health endpoint in your browser or via curl:
   ```bash
   curl -s https://campusfind-backend.onrender.com/api/v1/health
   ```
   *Expected Response:*
   ```json
   {
     "status": "UP",
     "database": {
       "status": "UP",
       "isAivenCloud": true,
       "type": "MySQL"
     }
   }
   ```
3. Test the in-process AI health endpoint:
   ```bash
   curl -s https://campusfind-backend.onrender.com/api/v1/reports/ai-health
   ```
   *Expected Response:*
   ```json
   {
     "status": "UP",
     "service": "campusfind-ai",
     "ai": {
       "available": true,
       "engine": "in-process-onnx-runtime",
       "model": "openclip_vit_b32_int8",
       "dimension": 512
     }
   }
   ```
   If both endpoints report `"status": "UP"`, your backend and embedded ONNX AI engine are fully operational!

---

## STEP 5: Deploy Static Frontend to Vercel Hobby

1. Log in to [Vercel Dashboard](https://vercel.com/).
2. Click **Add New...** and select **Project**.
3. Import your GitHub repository: `AdithyaVardhan2020/CampusFindai`.
4. In the project configuration screen:
   - **Framework Preset**: Other
   - **Root Directory**: Click *Edit* and select `campusfind-backend/src/main/resources/static` (or leave as root `./` if utilizing root `vercel.json`).
   - **Build Command**: Leave empty / disabled (pure static HTML/CSS/JS requires no Node build).
   - **Output Directory**: Leave empty / default.
5. Click **Deploy**. Vercel will deploy the static site globally across its edge network in approximately 15 seconds.

---

## STEP 6: Configure the Frontend API URL (No Build Step Required)

> [!NOTE]
> Static HTML/JS frontends on Vercel do **not** automatically expose Vercel environment variables to browser JavaScript. CampusFind AI handles this reliably through multiple complementary mechanisms:

1. **Automatic Domain Routing**:
   [`config.js`](file:///D:/CampusFindai/campusfind-backend/src/main/resources/static/js/config.js) checks `window.location.hostname`. When loaded on `*.vercel.app`, it routes requests to `https://campusfind-backend.onrender.com/api/v1`. If your Render service has a custom name, edit the `RENDER_BACKEND_URL` constant at the top of `config.js`.
2. **Vercel Reverse Proxy Rewrites**:
   Both [`vercel.json`](file:///D:/CampusFindai/vercel.json) files configure a reverse proxy rewrite:
   ```json
   { "source": "/api/v1/:path*", "destination": "https://campusfind-backend.onrender.com/api/v1/:path*" }
   ```
   This allows the browser to communicate same-origin with `/api/v1`, transparently forwarded to Render at the edge.
3. **Instant Browser Console Override**:
   To test with any arbitrary backend URL without redeploying code, open DevTools Console (`F12`) on your Vercel site and run:
   ```javascript
   setBackendUrl('https://<your-render-name>.onrender.com/api/v1');
   ```
   This persists the URL in `localStorage` and reloads the page. Call `setBackendUrl(null)` to reset.

---

## STEP 7: Configure Production CORS

1. Return to your [Render Dashboard](https://dashboard.render.com/) > `campusfind-backend` > **Environment**.
2. Set `CAMPUSFIND_CORS_ALLOWED_ORIGINS` to your exact Vercel production domain:
   ```text
   https://campusfindai.vercel.app
   ```
   *(Note: Local development origins `http://localhost:*` and `http://127.0.0.1:*` are automatically preserved by `SecurityConfig.java`, so local development and testing never break when you set a production domain).*
3. Save changes. Render performs a quick zero-downtime rolling restart to apply the updated origin.

---

## STEP 8: Test Complete Application Using Public URLs

1. Open your Vercel URL (`https://campusfindai.vercel.app`) in an incognito browser window.
2. Verify the homepage loads instantly (0 ms cold start).
3. Click **Sign In** and log in with your administrator credentials or register a new student account.
4. Browse existing reports on `browse.html` to verify reports and images load from Aiven MySQL.
5. Open the **Help Chatbot** widget and ask:
   *"How do I report a lost calculator?"*
   Confirm the Groq LLaMA 3.1 chatbot responds smoothly.

---

## STEP 9: Perform End-to-End AI Matching Test

1. Navigate to **Report Lost Item** (`create-report.html`).
2. Fill out the report form:
   - **Title**: *Blue Water Bottle*
   - **Category**: *Accessories*
   - **Location**: *Central Library 2nd Floor*
   - **Image**: Upload a photo of a blue bottle.
3. Submit the report.
4. Open the report details page (`detail.html?id=...`).
5. Check the **AI Match Recommendations** card.
6. Verify that the embedded ONNX engine analyzes the visual embedding, queries candidate found items in Aiven MySQL, and displays similarity percentages (e.g., `87.4% Match`).

---

## STEP 10: Verify Deployment After Render Cold Start

Because Render Free spins down services after 15 minutes of inactivity:
1. Allow the service to idle for 20 minutes (or trigger a manual restart in the Render dashboard).
2. Visit the Vercel frontend URL:
   - The UI loads immediately from Vercel's global CDN.
   - The first API call takes ~45 seconds while Render initializes the container and loads the ONNX model.
3. Confirm that upon waking up, the application works normally without timeouts or errors.

---

## Summary of Free-Tier Compliance

| Resource | Service | Monthly Cost | Usage Constraints |
| :--- | :--- | :---: | :--- |
| **Frontend CDN** | Vercel Hobby | **$0.00** | Unlimited static bandwidth for normal campus evaluation |
| **Backend & AI** | Render Free | **$0.00** | 750 free instance hours / month (1 service uses max 744 h/mo) |
| **Database** | Aiven MySQL Free | **$0.00** | Managed free tier, persistent storage |
| **Chatbot** | Groq Cloud API | **$0.00** | Free tier rate limits (14,400 requests / day) |
| **Total** | **All Services** | **₹0.00** | **100% Free Forever** |
