# 📚 Beginner's Study Guide - Change-My.com

**Welcome!** This guide will help you understand how Change-My.com works from the ground up. By the end, you'll have a comprehensive understanding of every component and how they work together.

---

## 🎯 Learning Objectives

By completing this study guide, you will understand:
- ✅ The overall architecture and how components communicate
- ✅ How the frontend handles user interactions
- ✅ How the backend processes image conversions
- ✅ How authentication and security work
- ✅ How payments are processed
- ✅ How the database stores and retrieves data
- ✅ How to deploy and maintain the application

---

## 📋 Prerequisites

Before starting, you should have basic knowledge of:
- JavaScript/TypeScript
- React basics
- Java basics
- SQL fundamentals
- HTTP requests/responses
- Command line usage

**Don't worry if you're not an expert!** This guide will help you learn as you go.

---

## 🗺️ Study Path (4-6 Weeks)

### **Week 1: Understanding the Big Picture**

#### Day 1-2: Project Overview & Setup
1. **Read the README**
   - Start with [README.md](README.md)
   - Understand what the app does and its key features
   - Review the tech stack diagram

2. **Study the Architecture**
   - Read [DEPLOYMENT-GUIDE.md](DEPLOYMENT-GUIDE.md)
   - Understand the 3-tier architecture:
     ```
     Frontend (Next.js) → Backend (Spring Boot) → Database (PostgreSQL)
     ```
   - Draw your own architecture diagram on paper

3. **Setup Your Development Environment**
   - Follow [SETUP.md](SETUP.md)
   - Get the app running locally
   - **Exercise**: Successfully run both frontend and backend
   - **Checkpoint**: Can you access the app at `localhost:3000`?

#### Day 3-4: Exploring the Codebase
1. **Frontend Structure**
   ```
   frontend/
   ├── app/              ← Next.js 13+ App Router
   ├── components/       ← React components
   ├── lib/              ← Utilities and helpers
   └── public/           ← Static assets
   ```
   - **Exercise**: Open each folder and list what files you find
   - **Task**: Find where the homepage is defined (hint: `app/page.tsx`)

2. **Backend Structure**
   ```
   backend/src/main/java/com/changemy/
   ├── controller/       ← API endpoints
   ├── service/          ← Business logic
   ├── repository/       ← Database access
   ├── model/            ← Data models
   └── config/           ← Configuration
   ```
   - **Exercise**: Open each folder and identify the purpose of each file
   - **Task**: Find the main application entry point (hint: `ChangeMyApplication.java`)

#### Day 5-7: Understanding Data Flow
1. **Follow a Request from Start to Finish**
   - Pick a simple action: "User uploads an image"
   - Trace it through the codebase:
     1. User clicks upload button (Frontend)
     2. Frontend sends HTTP request (API call)
     3. Backend receives request (Controller)
     4. Backend processes image (Service)
     5. Backend saves to database (Repository)
     6. Backend returns response
     7. Frontend displays result

2. **Exercise**: Create a flowchart of this process
3. **Read**: Understanding HTTP methods (GET, POST, PUT, DELETE)

---

### **Week 2: Frontend Deep Dive**

#### Day 1-2: Next.js & React Fundamentals
1. **Study Key Concepts**
   - What is Next.js?
   - What is Server-Side Rendering (SSR)?
   - What is the App Router?
   - What are React Components?

2. **Key Files to Study**
   - `frontend/app/layout.tsx` - Root layout
   - `frontend/app/page.tsx` - Homepage
   - `frontend/app/convert/page.tsx` - Main conversion page

3. **Exercise**:
   - Change the homepage title
   - Add a new paragraph of text
   - Refresh the page and see your changes
   - **Checkpoint**: Can you make UI changes successfully?

#### Day 3-4: Components & UI
1. **Study Components**
   - `frontend/components/ImageUpload.tsx` - File upload component
   - `frontend/components/FormatSelector.tsx` - Format selection
   - `frontend/components/ConversionResult.tsx` - Result display

2. **For Each Component, Understand**:
   - What props does it accept?
   - What state does it manage?
   - What does it render?
   - How does it handle user interactions?

3. **Exercise**:
   - Add a console.log in the ImageUpload component
   - Upload an image and check the browser console
   - See what data is logged

#### Day 5-7: API Integration & State Management
1. **Study API Calls**
   - `frontend/lib/api.ts` - API client functions
   - How does the frontend call the backend?
   - What is `fetch()` or `axios`?

2. **Follow an API Call**:
   ```typescript
   User Action → Component → API Function → HTTP Request → Backend
   ```

3. **Exercise**:
   - Find where images are uploaded to the backend
   - Add console.logs to track the request
   - Upload an image and watch the network tab in DevTools

4. **Study Authentication**
   - `frontend/app/api/auth/[...nextauth]/route.ts` - NextAuth setup
   - How does Google OAuth work?
   - How are sessions managed?

---

### **Week 3: Backend Deep Dive**

#### Day 1-2: Spring Boot Fundamentals
1. **Study Key Concepts**
   - What is Spring Boot?
   - What is Dependency Injection?
   - What is an MVC pattern?
   - What are REST APIs?

2. **Key Files to Study**
   - `backend/src/main/java/com/changemy/ChangeMyApplication.java` - Main entry
   - `backend/src/main/resources/application.properties` - Configuration

3. **Exercise**:
   - Change the server port to 8081
   - Restart the backend
   - **Checkpoint**: Can you access the API at `localhost:8081`?

#### Day 3-4: Controllers & Endpoints
1. **Study Controllers**
   - `backend/src/main/java/com/changemy/controller/ImageController.java`
   - `backend/src/main/java/com/changemy/controller/UserController.java`

2. **For Each Endpoint, Understand**:
   - What URL path does it handle?
   - What HTTP method does it use?
   - What request body does it expect?
   - What response does it return?

3. **Exercise**:
   - List all API endpoints in a table:
     | Method | Path | Purpose |
     |--------|------|---------|
     | POST | /api/convert | Convert image |

4. **Test with Postman or cURL**:
   ```bash
   curl -X GET http://localhost:8080/api/health
   ```

#### Day 5-7: Services & Business Logic
1. **Study Services**
   - `backend/src/main/java/com/changemy/service/ImageService.java`
   - `backend/src/main/java/com/changemy/service/UserService.java`

2. **Understand the Flow**:
   ```
   Controller → Service → Repository → Database
   ```

3. **Deep Dive: Image Conversion**
   - How does ImageMagick work?
   - How are temporary files managed?
   - How are errors handled?

4. **Exercise**:
   - Add a log statement in ImageService
   - Convert an image and check the console
   - See the conversion process in action

---

### **Week 4: Database, Security & Payments**

#### Day 1-2: Database & JPA
1. **Study Models/Entities**
   - `backend/src/main/java/com/changemy/model/User.java`
   - `backend/src/main/java/com/changemy/model/Conversion.java`

2. **Understanding JPA Annotations**:
   - `@Entity` - Marks a class as a database table
   - `@Id` - Primary key
   - `@Column` - Table column
   - `@ManyToOne`, `@OneToMany` - Relationships

3. **Study Repositories**
   - `backend/src/main/java/com/changemy/repository/UserRepository.java`
   - `backend/src/main/java/com/changemy/repository/ConversionRepository.java`

4. **Exercise**:
   - Connect to your local PostgreSQL database
   - Run `SELECT * FROM users;`
   - See the actual data structure
   - **Checkpoint**: Can you view database tables?

#### Day 3-4: Authentication & Security
1. **Study Security Configuration**
   - `backend/src/main/java/com/changemy/config/SecurityConfig.java`
   - Read [SECURITY-GUIDE.md](SECURITY-GUIDE.md)

2. **Understand Key Concepts**:
   - What is OAuth 2.0?
   - How does JWT work?
   - What is CORS and why is it needed?
   - What is CSRF protection?

3. **Follow the Authentication Flow**:
   ```
   1. User clicks "Sign in with Google"
   2. Redirected to Google OAuth
   3. User grants permission
   4. Google returns authorization code
   5. Backend exchanges code for tokens
   6. Backend creates session
   7. User is logged in
   ```

4. **Exercise**:
   - Sign in with Google
   - Check browser cookies/local storage
   - Find where the session token is stored

#### Day 5-7: Payment Processing
1. **Study Stripe Integration**
   - `backend/src/main/java/com/changemy/controller/PaymentController.java`
   - `frontend/app/billing/page.tsx`

2. **Understand the Payment Flow**:
   ```
   1. User clicks "Subscribe"
   2. Frontend creates Stripe checkout session
   3. User enters payment details on Stripe
   4. Stripe processes payment
   5. Stripe webhook notifies backend
   6. Backend updates user subscription
   7. User gets access to pro features
   ```

3. **Study Webhooks**
   - What is a webhook?
   - How does the backend verify Stripe webhooks?
   - What happens when payment succeeds/fails?

4. **Exercise**:
   - Use Stripe test mode
   - Complete a test subscription
   - Check database for updated subscription status

---

### **Week 5: Testing & Quality Assurance**

#### Day 1-3: Understanding Tests
1. **Read Testing Documentation**
   - [TESTING-PLAN.md](TESTING-PLAN.md)
   - [TESTING-IMPLEMENTATION-SUMMARY.md](TESTING-IMPLEMENTATION-SUMMARY.md)

2. **Study Frontend Tests**
   - `frontend/__tests__/` folder
   - Understand Jest and React Testing Library

3. **Study Backend Tests**
   - `backend/src/test/java/` folder
   - Understand JUnit and Mockito

4. **For Each Test, Understand**:
   - What is being tested?
   - What is the expected behavior?
   - What assertions are being made?

5. **Exercise**:
   ```bash
   # Run frontend tests
   cd frontend
   npm test

   # Run backend tests
   cd backend
   ./mvnw test
   ```

#### Day 4-7: Writing Your Own Tests
1. **Write a Simple Frontend Test**
   ```typescript
   // Test that homepage renders
   test('homepage renders title', () => {
     render(<HomePage />)
     expect(screen.getByText('Change-My.com')).toBeInTheDocument()
   })
   ```

2. **Write a Simple Backend Test**
   ```java
   @Test
   public void testHealthEndpoint() {
     // Test that health check returns OK
   }
   ```

3. **Exercise**:
   - Add a test for a new feature
   - Run the tests and make sure they pass
   - **Checkpoint**: Can you write and run tests?

---

### **Week 6: Deployment & DevOps**

#### Day 1-2: Understanding Deployment
1. **Study Deployment Options**
   - [VERCEL-DEPLOYMENT.md](VERCEL-DEPLOYMENT.md) - Frontend
   - [RAILWAY-DEPLOYMENT.md](RAILWAY-DEPLOYMENT.md) - Backend option 1
   - [RENDER-DEPLOYMENT.md](RENDER-DEPLOYMENT.md) - Backend option 2

2. **Understand Key Concepts**:
   - What is CI/CD?
   - What is a production environment?
   - What are environment variables?
   - What is the difference between development and production?

3. **Study Environment Configuration**
   - `frontend/.env.local` vs `.env.production`
   - `backend/application.properties` vs `application-prod.properties`

#### Day 3-4: Docker & Containerization (Optional)
1. **Study Docker Files** (if present)
   - `Dockerfile` for frontend
   - `Dockerfile` for backend

2. **Understand Containers**:
   - What is Docker?
   - What is a container image?
   - How does it differ from a virtual machine?

#### Day 5-7: Deploy Your Own Instance
1. **Deploy Frontend to Vercel**
   - Follow [VERCEL-DEPLOYMENT.md](VERCEL-DEPLOYMENT.md)
   - Get your own live URL

2. **Deploy Backend to Railway/Render**
   - Follow deployment guide
   - Connect to your own database

3. **Configure Everything**
   - Set environment variables
   - Test the full flow end-to-end
   - **Final Checkpoint**: Do you have a working deployed app?

---

## 🎓 Final Project: Build Your Own Feature

Now that you understand the entire system, build something new!

### **Project Ideas** (Choose One):
1. **Add a new image format** (e.g., AVIF, TIFF)
2. **Add image resize functionality**
3. **Add a conversion history page**
4. **Add email notifications when conversion is complete**
5. **Add dark mode toggle**

### **Steps**:
1. **Plan Your Feature**
   - What will it do?
   - What components/services are needed?
   - Draw a flowchart

2. **Implement Frontend**
   - Create necessary components
   - Add UI elements
   - Handle user interactions

3. **Implement Backend**
   - Create/modify endpoints
   - Add business logic
   - Update database if needed

4. **Write Tests**
   - Frontend component tests
   - Backend unit tests
   - Integration tests

5. **Deploy & Test**
   - Deploy your changes
   - Test in production
   - Fix any bugs

6. **Document Your Work**
   - Write a README for your feature
   - Document any new API endpoints
   - Create a pull request (optional)

---

## 📚 Additional Learning Resources

### **Next.js & React**
- [Next.js Documentation](https://nextjs.org/docs)
- [React Documentation](https://react.dev)
- [TypeScript Handbook](https://www.typescriptlang.org/docs/)

### **Spring Boot & Java**
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Security](https://spring.io/guides/topicals/spring-security-architecture)
- [JPA/Hibernate](https://spring.io/guides/gs/accessing-data-jpa/)

### **Database**
- [PostgreSQL Tutorial](https://www.postgresqltutorial.com/)
- [SQL Basics](https://www.w3schools.com/sql/)

### **Authentication**
- [OAuth 2.0 Simplified](https://www.oauth.com/)
- [NextAuth.js Documentation](https://next-auth.js.org/)

### **Payments**
- [Stripe Documentation](https://stripe.com/docs)
- [Stripe Testing](https://stripe.com/docs/testing)

### **DevOps**
- [Docker Get Started](https://docs.docker.com/get-started/)
- [Git Basics](https://git-scm.com/book/en/v2/Getting-Started-About-Version-Control)

---

## ✅ Study Checklist

Track your progress:

### **Week 1: Big Picture**
- [ ] Read README and understand what the app does
- [ ] Set up development environment
- [ ] Run app locally
- [ ] Explored frontend and backend folder structure
- [ ] Traced a request from frontend to backend

### **Week 2: Frontend**
- [ ] Understand Next.js and React basics
- [ ] Made changes to UI components
- [ ] Studied API integration
- [ ] Understand authentication flow

### **Week 3: Backend**
- [ ] Understand Spring Boot basics
- [ ] Mapped all API endpoints
- [ ] Studied business logic in services
- [ ] Understand image conversion process

### **Week 4: Database & Security**
- [ ] Studied database models
- [ ] Connected to database and viewed data
- [ ] Understand authentication and security
- [ ] Understand payment processing flow

### **Week 5: Testing**
- [ ] Read testing documentation
- [ ] Ran all tests successfully
- [ ] Wrote a simple test
- [ ] Understand test coverage

### **Week 6: Deployment**
- [ ] Understand deployment options
- [ ] Deployed frontend to Vercel
- [ ] Deployed backend to Railway/Render
- [ ] Have a working live instance

### **Final Project**
- [ ] Planned a new feature
- [ ] Implemented frontend changes
- [ ] Implemented backend changes
- [ ] Wrote tests for new feature
- [ ] Deployed and tested in production

---

## 🤝 Getting Help

If you get stuck:

1. **Read the documentation** - Most answers are in the docs
2. **Check the code** - Add console.logs and debug
3. **Google the error** - Someone has probably had the same issue
4. **Ask questions** - Create an issue on GitHub

---

## 🎉 Congratulations!

If you've completed this guide, you now have:
- ✅ Deep understanding of full-stack development
- ✅ Experience with modern web technologies
- ✅ Knowledge of authentication and payments
- ✅ Deployment and DevOps skills
- ✅ Testing and quality assurance practices

**You're ready to build your own full-stack applications!** 🚀

---

## 📝 Notes Section

Use this space to write your own notes as you study:

### My Key Learnings:
```
[Write your notes here]
```

### Questions I Still Have:
```
[Write your questions here]
```

### Cool Things I Discovered:
```
[Write your discoveries here]
```

---

**Happy Learning! 📚✨**
