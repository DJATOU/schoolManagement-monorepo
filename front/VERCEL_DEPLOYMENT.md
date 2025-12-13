# Deploying Frontend to Vercel (Free Tier)

This guide explains how to deploy the Angular frontend to Vercel's free tier as a static site.

## Prerequisites

- A Vercel account (sign up at https://vercel.com)
- Your repository pushed to GitHub/GitLab/Bitbucket
- Backend already deployed to Render (see `/back/RENDER_DEPLOYMENT.md`)

## Step 1: Update Environment Configuration

Before deploying, update the production API URL:

1. Open `src/environments/environment.prod.ts`
2. Replace `https://your-backend.onrender.com` with your actual Render backend URL
   - Example: `https://school-management-api.onrender.com`
3. Commit and push this change:
   ```bash
   git add src/environments/environment.prod.ts
   git commit -m "Update production API URL"
   git push origin main
   ```

## Step 2: Deploy to Vercel

### Option A: Deploy via Vercel Dashboard (Recommended for first-time users)

1. Log in to [Vercel Dashboard](https://vercel.com/dashboard)
2. Click "Add New..." → "Project"
3. Import your repository:
   - Click "Import" next to your `schoolManagement-monorepo` repository
   - If not visible, click "Import Git Repository" and authorize Vercel to access your repos
4. Configure the project:

   **Framework Preset**: Other (or leave as detected)

   **Root Directory**: Click "Edit" and select `front` (this is critical!)

   **Build and Output Settings**:
   - **Build Command**: `npm run build` or `ng build`
   - **Output Directory**: `dist/school-management-front/browser`
   - **Install Command**: `npm install` (default)

5. Click "Deploy"
6. Wait for deployment to complete (usually 2-5 minutes)
7. Vercel will provide you with a URL like: `https://your-project.vercel.app`

### Option B: Deploy via Vercel CLI

1. Install Vercel CLI globally:
   ```bash
   npm install -g vercel
   ```

2. Navigate to the frontend directory:
   ```bash
   cd front
   ```

3. Login to Vercel:
   ```bash
   vercel login
   ```

4. Deploy:
   ```bash
   vercel
   ```

5. Follow the prompts:
   - Set up and deploy? **Y**
   - Which scope? Choose your account
   - Link to existing project? **N**
   - What's your project's name? `school-management-front` (or your choice)
   - In which directory is your code located? **.**
   - Want to override the settings? **Y**
     - Build Command: `npm run build`
     - Output Directory: `dist/school-management-front/browser`
     - Development Command: `npm start`

6. For production deployment:
   ```bash
   vercel --prod
   ```

## Step 3: Verify Deployment

1. Visit your Vercel URL (e.g., `https://your-project.vercel.app`)
2. The Angular application should load
3. Test the connection to your backend:
   - Try accessing a feature that calls the API (e.g., student list)
   - Open browser DevTools (F12) → Network tab to check API calls

## Step 4: Update Backend CORS Configuration

Now that your frontend is deployed, update the backend to allow requests from your Vercel URL:

1. Go to your [Render Dashboard](https://dashboard.render.com)
2. Select your backend web service
3. Navigate to "Environment" tab
4. Update or add the `CORS_ALLOWED_ORIGINS` environment variable:
   ```
   CORS_ALLOWED_ORIGINS=https://your-project.vercel.app
   ```
   - For both local dev and production:
   ```
   CORS_ALLOWED_ORIGINS=http://localhost:4200,https://your-project.vercel.app
   ```
5. Click "Save Changes"
6. Render will automatically redeploy with the new CORS settings

## Step 5: Configure Custom Domain (Optional)

If you have a custom domain:

1. In Vercel Dashboard, go to your project
2. Click "Settings" → "Domains"
3. Add your custom domain (e.g., `app.yourdomain.com`)
4. Follow Vercel's instructions to update your DNS settings
5. Update `CORS_ALLOWED_ORIGINS` in Render to include your custom domain

## Important Notes for Free Tier

### Vercel Free Tier Includes:
- Unlimited deployments
- Automatic HTTPS
- Global CDN
- 100 GB bandwidth per month
- Preview deployments for every push

### Automatic Deployments

Vercel automatically deploys when you push to your repository:

- **Production Branch** (e.g., `main`): Deploys to your production URL
- **Other Branches**: Creates preview deployments with unique URLs
- **Pull Requests**: Automatically creates preview deployments

### Build Configuration

The project is configured with:

- **Node Version**: Vercel auto-detects from package.json
- **Framework**: Angular 17
- **Build Command**: `npm run build` (runs `ng build` with production config)
- **Output**: Static files in `dist/school-management-front/browser`

### Environment Variables (if needed)

If you need to set environment variables at build time:

1. Go to Project Settings → Environment Variables
2. Add variables (e.g., `API_URL` if you want to override at build time)
3. Note: For this project, the API URL is already configured in `environment.prod.ts`

## Troubleshooting

### Build Fails

**Error: "Command failed"**
- Check the build logs in Vercel dashboard
- Verify `package.json` dependencies are correct
- Test the build locally:
  ```bash
  cd front
  npm install
  npm run build
  ```

**Error: "Output directory not found"**
- Verify the output directory is set to `dist/school-management-front/browser`
- Check `angular.json` for the correct output path

### Application Loads But API Calls Fail

**CORS Errors**:
- Verify `CORS_ALLOWED_ORIGINS` in Render includes your Vercel URL
- Check for typos in the URL (no trailing slashes)
- Ensure protocol matches (https://)

**404 Errors on API Calls**:
- Verify `environment.prod.ts` has the correct Render backend URL
- Check that the backend is running on Render
- Test the backend directly: `curl https://your-backend.onrender.com/api/students`

**Network Errors**:
- Check if backend is in "sleeping" state (free tier spins down after 15 min)
- Wait 30-60 seconds for backend to wake up
- Check Render logs for backend errors

### Routing Issues (404 on Page Refresh)

If you get 404 errors when refreshing pages:

1. Create a `vercel.json` file in the `front` directory:
   ```json
   {
     "routes": [
       {
         "src": "/(.*)",
         "dest": "/index.html"
       }
     ]
   }
   ```
2. Commit and push:
   ```bash
   git add vercel.json
   git commit -m "Add Vercel routing config"
   git push
   ```

## Local Development vs Production

Your configuration supports both environments:

**Local Development**:
```bash
cd front
npm install
npm start
# Uses environment.ts → http://localhost:8080
```

**Production Build (to test locally)**:
```bash
cd front
npm run build
# Uses environment.prod.ts → your Render backend URL
# Output: dist/school-management-front/browser
```

To serve the production build locally:
```bash
npx http-server dist/school-management-front/browser -p 4200
```

## Updating Your Deployment

### Automatic Updates (Recommended)

Simply push to your repository:
```bash
git add .
git commit -m "Your changes"
git push origin main
```
Vercel automatically detects and deploys the changes.

### Manual Deployment via CLI

```bash
cd front
vercel --prod
```

## Preview Deployments

For testing changes before production:

1. Create a new branch:
   ```bash
   git checkout -b feature/new-feature
   ```
2. Make your changes and push:
   ```bash
   git add .
   git commit -m "Add new feature"
   git push origin feature/new-feature
   ```
3. Vercel automatically creates a preview deployment
4. Test the preview URL before merging to main

## Environment URLs Summary

After deployment, you'll have:

| Environment | Frontend URL | Backend URL |
|-------------|--------------|-------------|
| **Local Dev** | http://localhost:4200 | http://localhost:8080 |
| **Production** | https://your-project.vercel.app | https://your-backend.onrender.com |

## Next Steps

1. Test the full application with real data
2. Configure a custom domain (optional)
3. Set up monitoring for errors (Vercel provides analytics)
4. Consider upgrading to paid tier for:
   - More bandwidth
   - Better performance
   - Priority support

## Support Resources

- Vercel Documentation: https://vercel.com/docs
- Angular Deployment Guide: https://angular.io/guide/deployment
- Vercel Community: https://github.com/vercel/vercel/discussions
- Angular Material: https://material.angular.io

## Security Checklist

Before going to production:

- [ ] Backend CORS is configured with your Vercel URL (not wildcard `*`)
- [ ] HTTPS is enabled (Vercel does this automatically)
- [ ] API endpoints are secured (authentication if needed)
- [ ] Environment variables are not exposed in client-side code
- [ ] Error messages don't expose sensitive information
