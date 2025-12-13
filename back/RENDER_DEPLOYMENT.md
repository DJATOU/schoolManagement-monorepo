# Deploying Backend to Render (Free Tier)

This guide explains how to deploy the Spring Boot backend with PostgreSQL on Render's free tier.

## Prerequisites

- A Render account (sign up at https://render.com)
- This repository pushed to GitHub/GitLab/Bitbucket

## Step 1: Create a PostgreSQL Database

1. Log in to your Render dashboard
2. Click "New +" and select "PostgreSQL"
3. Configure the database:
   - **Name**: `school-management-db` (or your preferred name)
   - **Database**: `schoolmanagement` (or your preferred database name)
   - **User**: Auto-generated (Render will create this)
   - **Region**: Choose the closest to your users
   - **Instance Type**: Select "Free"
4. Click "Create Database"
5. Wait for the database to be provisioned (usually takes 1-2 minutes)
6. Once ready, note down the following connection details (found in the "Info" section):
   - **Internal Database URL** (starts with `jdbc:postgresql://...`)
   - **Username**
   - **Password**

## Step 2: Create a Web Service for the Backend

1. From the Render dashboard, click "New +" and select "Web Service"
2. Connect your repository:
   - Select your Git provider (GitHub, GitLab, or Bitbucket)
   - Authorize Render to access your repositories
   - Select the `schoolManagement-monorepo` repository
3. Configure the web service:
   - **Name**: `school-management-api` (or your preferred name)
   - **Region**: Same as your database for better performance
   - **Branch**: `main` (or your deployment branch)
   - **Root Directory**: `back`
   - **Runtime**: `Java`
   - **Build Command**: `./mvnw clean package -DskipTests`
   - **Start Command**: `java -Dserver.port=$PORT -Dspring.profiles.active=prod -jar target/schoolManagement-0.0.1-SNAPSHOT.jar`

## Step 3: Configure Environment Variables

In the "Environment" section of your web service configuration, add the following environment variables:

### Required Database Variables

Click "Add Environment Variable" for each of these:

| Key | Value | Description |
|-----|-------|-------------|
| `SPRING_DATASOURCE_URL` | (from Step 1) | The Internal Database URL from your Render PostgreSQL instance |
| `SPRING_DATASOURCE_USERNAME` | (from Step 1) | Database username |
| `SPRING_DATASOURCE_PASSWORD` | (from Step 1) | Database password |
| `SPRING_PROFILES_ACTIVE` | `prod` | Activates production profile |

### Optional Configuration Variables

| Key | Value | Description |
|-----|-------|-------------|
| `CORS_ALLOWED_ORIGINS` | `https://your-app.vercel.app` | Your Vercel frontend URL (update after deploying frontend) |
| `SERVER_BASE_URL` | `https://school-management-api.onrender.com` | Your Render backend URL (update with your actual URL) |
| `UPLOAD_DIR` | `/var/data/uploads` | Directory for file uploads on Render |

**Important Notes:**
- Replace `your-app.vercel.app` with your actual Vercel domain after deploying the frontend
- Replace `school-management-api.onrender.com` with your actual Render web service URL
- For multiple allowed origins, separate with commas: `https://app1.vercel.app,https://app2.vercel.app`

## Step 4: Deploy

1. Click "Create Web Service"
2. Render will automatically:
   - Clone your repository
   - Run the build command
   - Start your application
   - Provide you with a URL like `https://school-management-api.onrender.com`

## Step 5: Verify Deployment

1. Wait for the deployment to complete (check the "Logs" tab)
2. Once the status shows "Live", test your API:
   ```bash
   curl https://your-service-name.onrender.com/api/students
   ```
3. You should receive a JSON response (empty array if no data yet)

## Step 6: Update CORS Configuration

After deploying your frontend to Vercel:

1. Go to your Render web service dashboard
2. Navigate to "Environment" tab
3. Update the `CORS_ALLOWED_ORIGINS` variable with your Vercel URL
4. Click "Save Changes"
5. Render will automatically redeploy with the new configuration

## Important Notes for Free Tier

### Limitations
- Free tier services spin down after 15 minutes of inactivity
- First request after inactivity may take 30-60 seconds (cold start)
- Free PostgreSQL databases have storage limits (1GB)
- Free web services have limited build hours per month

### Recommendations
- Use the production profile (`prod`) which has:
  - `hibernate.ddl-auto=validate` (doesn't auto-modify schema)
  - Minimal logging to reduce resource usage
  - Optimized settings for production

### Keeping Your Service Active
If you want to reduce cold starts:
- Use a service like UptimeRobot or cron-job.org to ping your API every 10-14 minutes
- Note: This uses your service hours, so monitor your usage

## Troubleshooting

### Build Fails
- Check the "Logs" tab for error messages
- Ensure Java 21 is specified in your `pom.xml`
- Verify the build command is correct

### Application Won't Start
- Check environment variables are set correctly
- Verify database connection details
- Check logs for startup errors

### Database Connection Errors
- Ensure you're using the Internal Database URL (not External)
- Verify username and password are correct
- Check that the database is in the same region as your web service

### CORS Errors
- Update `CORS_ALLOWED_ORIGINS` with your actual Vercel URL
- Ensure there are no trailing slashes in the URL
- For testing, you can temporarily allow all origins with `*` (not recommended for production)

## Updating Your Deployment

Render automatically deploys when you push to your configured branch:

1. Make changes to your code locally
2. Commit and push to your repository:
   ```bash
   git add .
   git commit -m "Your commit message"
   git push origin main
   ```
3. Render will automatically detect the changes and redeploy

## Local Development vs Production

Your configuration now supports both:

**Local Development** (default):
```bash
# Just run normally - uses local PostgreSQL
./mvnw spring-boot:run
```

**Production** (Render):
- Uses environment variables for database connection
- Activated via `SPRING_PROFILES_ACTIVE=prod`
- No code changes needed between environments

## Next Steps

1. Deploy your Angular frontend to Vercel (see `/front/VERCEL_DEPLOYMENT.md`)
2. Update the `CORS_ALLOWED_ORIGINS` environment variable with your Vercel URL
3. Update the frontend's `environment.prod.ts` with your Render backend URL
4. Test the full stack integration

## Support

- Render Documentation: https://render.com/docs
- Render Community: https://community.render.com
- Spring Boot Documentation: https://spring.io/guides
