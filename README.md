# ServiZone

A marketplace for local home services: customers browse and book verified professionals, and
professionals publish services, set their working hours and track earnings. Everything is
server-rendered with Spring Boot and Thymeleaf, stored in MongoDB, and paid for **in cash after the
job**.

Built as a single Maven module with feature-based packages.

- Java 17 · Spring Boot 4.1.1 · Spring AI 2.0.1 · Spring Data MongoDB · Thymeleaf · Spring Security
- 199 Java sources, 55 templates, 20 static assets, 49 tests

---

## Contents

- [Running it](#running-it)
- [Configuration](#configuration)
- [First administrator](#first-administrator)
- [Architecture](#architecture)
- [Features](#features)
- [Payment is cash only](#payment-is-cash-only)
- [Caching, without Redis](#caching-without-redis)
- [The assistant](#the-assistant)
- [Uploads and storage](#uploads-and-storage)
- [Security](#security)
- [Routes](#routes)
- [Tests](#tests)
- [Verification scripts](#verification-scripts)
- [Committed credentials](#committed-credentials)

---

## Running it

### With Docker

```bash
docker compose up --build
# the marketplace is on http://localhost:8080
```

Add a local language model for the assistant:

```bash
docker compose --profile ai up --build
docker compose --profile ai run --rm ollama pull llama3.2   # once, the model is not bundled
```

The compose file contains no secrets of its own; every value falls back to the default in
`application.yml`. Put overrides in a `.env` file next to `docker-compose.yml`.

### Without Docker

```bash
mvn spring-boot:run                    # dev profile, templates uncached
SPRING_PROFILES_ACTIVE=prod mvn clean package && java -jar target/market-0.0.1-SNAPSHOT.jar
```

MongoDB Atlas and Gmail are reached over the network, so a local run needs only Java 17 and Maven.

---

## Configuration

Everything is an environment variable with a working default in
`src/main/resources/application.yml`. Nothing is mandatory for a local run.

| Variable | Default | What it does |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` enables template caching, secure cookies and quiet error pages |
| `SERVER_PORT` | `8080` | HTTP port |
| `MONGODB_URI` | Atlas cluster (see below) | Database connection string |
| `MAIL_HOST` | `smtp.gmail.com` | SMTP host |
| `MAIL_PORT` | `587` | SMTP port, with STARTTLS |
| `MAIL_USERNAME` | `rashedulislam.raze@gmail.com` | Sending account |
| `MAIL_PASSWORD` | Gmail app password (see below) | SMTP credential |
| `MAIL_FROM` | same as username | Envelope sender for verification and reset mail |
| `CONTACT_EMAIL` | same as username | Where the contact form is delivered |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NAME` | blank | Bootstrap administrator, see below |
| `STORAGE_PATH` | `./storage` | Root for uploaded images |
| `APP_BASE_URL` | `http://localhost:8080` | Used in the links inside emails |
| `APP_NAME` | `ServiZone` | Shown in the navbar, page titles and mail |
| `APP_TIMEZONE` | `Asia/Dhaka` | Working hours, "today" and booking dates |
| `OTP_SECRET` | `dev-only-otp-secret-change-me` | HMAC key for one-time codes — **change this** |
| `PLATFORM_FEE_PERCENT` | `5` | Platform fee added on top of the professional's price |
| `CHATBOT_ENABLED` | `true` | Set `false` to answer only from the built-in rules |
| `CHATBOT_TIMEOUT_SECONDS` | `12` | How long to wait for the model before falling back |
| `CHATBOT_FAILURE_THRESHOLD` | `1` | Failed attempts in a row before the model is left alone |
| `CHATBOT_COOLDOWN_SECONDS` | `120` | How long before the model is tried again |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Where the local model is |
| `OLLAMA_CHAT_MODEL` | `llama3.2` | Model name |

Beyond configuration, four business rules are also editable from `/admin/settings` at runtime
rather than from the file: the platform fee percentage, the cancellation cut-off, the minimum
notice for a same-day booking and the time-slot granularity.

### MongoDB Atlas

The default URI points at a shared Atlas cluster. To use your own, create a free M0 cluster, add
your user, and allow your IP or `0.0.0.0/0` for a development cluster, then set `MONGODB_URI`.

Indexes are created on startup (`spring.data.mongodb.auto-index-creation: true`), so a fresh
database needs no manual setup. The database is used as-is: there is no migration step and no
schema file, because MongoDB is the schema.

### Mail

Gmail needs an **app password**, not the account password: turn on 2-Step Verification, then create
one under Google Account → Security → App passwords. `MAIL_USERNAME` is the Gmail address and
`MAIL_PASSWORD` is the 16-character app password.

`management.health.mail.enabled` is `false` so a wrong mail credential does not mark the whole
application unhealthy. A send failure is logged; it does not take the site down.

---

## First administrator

There is no default administrator and no default password anywhere in the repository. On startup,
and **only when the database contains no user with the ADMIN role**, the bootstrap seeder creates
one:

```bash
ADMIN_EMAIL=admin@servizone.test ADMIN_PASSWORD='Admin#2026pass' ADMIN_NAME='Platform Admin' \
  mvn spring-boot:run
```

It will not promote an existing non-admin account to administrator, and it will not overwrite an
administrator that is already there. Leave `ADMIN_EMAIL` and `ADMIN_PASSWORD` blank in production
and create the first administrator through a one-off command instead.

---

## Architecture

Feature-based packages under `com.example.marketplace`. A package owns its controller, service,
DTOs, model and repository, and nothing reaches into another feature's repository.

```
admin         dashboard, users, workers, services, bookings, categories, reviews, reports, settings
auth          login, registration, OTP verification, password reset
booking       availability-aware booking, state machine, pricing, cancellation
category      service categories
chatbot       the assistant: controller, rule answers, model tools, page template
common        cross-cutting: enums, exceptions, security helpers, shared utilities
config        security, caching, web
favorite      saved services
home          homepage, about, FAQ, contact form
media         file storage
notification  in-app notifications and email
otp           one-time codes, rate limited and HMAC signed
payment       cash payment recording; deliberately the only payment method
review        reviews written after a completed booking
schedule      professional working hours and free slots
security      authentication, authorisation, argument filters
servicecatalog  listings, search, public pages
user          accounts, customer dashboard and profile
worker        professional profile, services, images, schedule, bookings, dashboard, earnings
```

The rules the code is written to:

- **No business logic in controllers.** A controller validates input, calls one service and names a
  view. Every decision — pricing, availability, whether a state change is legal — lives in a service.
- **DTOs at every boundary.** Controllers never hand a Mongo entity to a template, and templates
  never reach a repository.
- **Constructor injection only.** No field injection, no `@Autowired` on a field.
- **One central error handler.** `GlobalExceptionHandler` maps exceptions to pages and status codes;
  a stack trace never reaches a visitor in `prod`.
- **Centralised cache names.** `CacheNames` is the only place a cache is named, and eviction goes
  through the services that own the data.

---

## Features

**Public** — homepage, searchable catalogue with filters (category, area, price range, rating,
distance from your location, sort order), service detail with confirmed time slots, professional
profiles, category pages, standalone search, about, FAQ, contact form, and the assistant.

**Customers** — account and profile photo, saved services, booking with a confirmed slot,
cancellation, bookings list, dashboard, reviews after a completed booking, notifications.

**Professionals** — profile and verification, service listings with photos and ordering, weekly
working hours and blocked time, incoming bookings to accept, reject, start, complete and cancel,
mark cash received, reviews received, earnings dashboard.

**Administrators** — dashboard, professional approval queue and moderation, listing suspension,
booking oversight including marking cash received, user suspension, category management, review
moderation, reports and settings.

---

## Payment is cash only

There is no payment gateway, no card form, no wallet and no webhook. `payment/` contains exactly
one implementation, `CashPaymentService`, which records that money changed hands in person.

This is enforced, not just documented:

- The total shown to a customer is the professional's price plus the platform fee, calculated in
  `PriceCalculator` and never accepted from the browser.
- A booking is only marked paid when a professional confirms they received the cash.
- The assistant is told in its system prompt that payment is cash only, and the rule-based answers
  say so unconditionally.

---

## Caching, without Redis

Caching is Spring's `@EnableCaching` over an in-memory `ConcurrentMapCacheManager`. There is no
Redis, and no other external cache.

Four caches are registered in `CacheNames`:

| Cache | Holds |
| --- | --- |
| `categories` | the active category list, read on nearly every page |
| `popularServices` | the homepage carousel |
| `workerProfiles` | public professional profiles |
| `serviceDetails` | a single service page by slug |

`ServiceVisibilitySync` and the write paths evict the affected keys, so a suspended listing or a
changed schedule does not stay visible.

Two deliberate omissions: **availability is never cached**, because a stale free slot turns into a
double booking, and neither are OTPs, password state or booking state.

A `ConcurrentMapCacheManager` lives and dies with the JVM, so a multi-instance deployment gives each
instance its own cache. That is correct but not shared, and it is the one thing to replace if this
is ever scaled horizontally — `CacheConfig.cacheManager()` is the single method to swap.

---

## The assistant

`/chat` answers questions about services, prices, categories and how booking works. It is open to
everyone; nobody has to register to ask a question. The conversation lives in the HTTP session and
goes away with it.

It has two answers, and the second one is the important part.

**With a model.** If a local Ollama instance is reachable, the question goes to it with three tools
attached:

| Tool | Does |
| --- | --- |
| `searchServices` | searches the catalogue through `ServiceSearchService` |
| `describeService` | one service's price, duration and what the package includes |
| `listCategories` | the live category list with slugs |

Every tool delegates to the same public services the web pages use, so the assistant can never see
anything a visitor could not already see, and no tool touches MongoDB directly. Its brief forbids
inventing a price, a name, a rating or a phone number, and forbids stating when a professional is
free — availability changes, so it is only shown as confirmed slots on the service page.

**Without one.** A local model may be stopped, still loading weights, or simply not installed. So
every question is answerable without it. `ChatbotRuleService` matches the intent by keyword and
answers from live data and configuration: payment questions get the cash-only answer, availability
questions get sent to the service page, fee and cancellation questions get the configured numbers,
and anything else becomes a catalogue search with the question wording stripped away first.

The model gets a hard time limit, run on a daemon thread. Anything slower, unreachable or broken is
logged and answered from the rules. A page that shows an error is worse than a page that answers
plainly.

Those timeouts are not enough on their own. A refused connection to a local model is retried with a
back-off, so without more than that every single question would wait out the full timeout and a
twenty second answer to "hello" reads as a broken site. So `ModelCircuitBreaker` counts the failures
and, after the first one, sends the questions straight to the rules for two minutes. It closes on its
own afterwards, so a model that starts later is picked up without a restart. The result is that at
most one question is ever slow.

Three details worth knowing:

- The listing cards under an answer are re-read from the catalogue, never taken from the model's
  wording. A card cannot show a price the catalogue does not have.
- A rule-based answer is labelled **Standard answer** on the page. The site does not pretend a
  template is a language model.

`ChatbotRuleServiceTest` pins all of this: cash only, availability never quoted, prices from real
listings, no raw user ids, and an unknown question producing "try another word" rather than a guess.
`ModelCircuitBreakerTest` pins that the assistant stops retrying an absent model and recovers when it
comes back.

---

## Uploads and storage

`LocalFileStorageService` writes to `{STORAGE_PATH}/public/{service-images|profile-images}`.

- File names are random UUIDs; the original name is never used.
- Only `jpg`, `png` and `webp` are accepted, checked on the extension after decoding.
- Every resolved path is verified to stay inside the storage root, so `..` cannot escape it.
- Uploads are capped at 5 MB per file and 25 MB per request.
- The public half is served by a resource handler under `/uploads/public/**`.

The volume in `docker-compose.yml` is what keeps these files across a container replacement.
Without it, replacing the image loses every photo.

---

## Security

Deny by default: `SecurityConfig` permits an explicit list of public routes and requires
authentication for everything else, with `/admin/**`, `/worker/**` and the customer routes
additionally required to carry the matching role.

| | |
| --- | --- |
| Passwords | BCrypt, cost 12 |
| Password reset | one-time code by email, HMAC signed, 10 minute expiry, 5 attempts, 5 codes per hour, 60 second resend cooldown |
| Brute force | 5 failed logins locks an account for 15 minutes |
| CSRF | enabled everywhere; logout is POST only |
| Sessions | 30 minutes, HTTP-only, `SameSite=Lax`, `Secure` in `prod` |
| Header hardening | `X-Content-Type-Options`, `X-Frame-Options: DENY`, strict-origin referrer policy |
| Errors | `GlobalExceptionHandler`; stack traces never reach a visitor in `prod` |
| Uploads | type and size checked, names randomised, paths confined |
| Actuator | only `health` and `info` exposed; details `never` in `prod` |

---

## Routes

103 request mappings across 29 controllers.

| Area | Routes |
| --- | --- |
| Public | `/`, `/services`, `/services/{slug}`, `/search`, `/categories`, `/categories/{slug}`, `/workers`, `/workers/{slug}`, `/about`, `/faq`, `/contact`, `/chat` |
| Account | `/login`, `/register`, `/verify-otp`, `/resend-otp`, `/forgot-password`, `/reset-password`, `/logout` |
| Customer | `/customer/dashboard`, `/customer/profile`, `/customer/bookings`, `/booking/new`, `/booking`, `/booking/{id}`, `/booking/{id}/cancel`, `/favorites`, `/notifications`, `/review/{bookingId}` |
| Professional | `/worker/dashboard`, `/worker/profile`, `/worker/services`, `/worker/services/{id}`, `/worker/services/{id}/images`, `/worker/schedule`, `/worker/bookings`, `/worker/reviews` |
| Admin | `/admin`, `/admin/users`, `/admin/workers`, `/admin/workers/queue`, `/admin/services`, `/admin/bookings`, `/admin/categories`, `/admin/reviews`, `/admin/reports`, `/admin/settings` |
| JSON | `/api/services/{slug}/slots`, `/api/favorites/{serviceId}/toggle`, `/api/favorites/state`, `/api/notifications/summary` |
| Ops | `/actuator/health`, `/actuator/info` |

---

## Tests

```bash
mvn test        # 49 tests
```

| Suite | Covers |
| --- | --- |
| `ChatbotRuleServiceTest` | the guarantees the assistant makes: cash only, availability never quoted, prices from real listings, no id leakage, no invented match |
| `ChatbotControllerTest` | the chat page renders empty and full, history survives a rejected question, suggestions come from configuration, and `/chat` is reachable without signing in |
| `CustomerDashboardControllerTest` | every card's populated and empty branch |
| `WorkerDashboardControllerTest` | earnings aggregation figures |
| `PriceCalculatorTest` | fee arithmetic and rounding |
| `SlotCalculatorTest` | slot generation against working hours and blocks |
| `BookingLockServiceTest` | concurrent booking protection |
| `ModelCircuitBreakerTest` | the assistant stops retrying an absent model, and recovers when it returns |
| `ReviewServiceTest` | averages and display names |
| `MarketplaceApplicationTests` | the context loads |

Web-layer tests are `@WebMvcTest` slices. They import the application's own `SecurityConfig` rather
than switching filters off, so route access is tested without repeating the route list.

---

## Verification scripts

Plain Python, no dependencies. They exist because "the build is green" and "the site works" are
different claims.

```bash
python3 check-links.py         # every link on every reachable page resolves
python3 check-pages.py <email> <password> <path>...   # authenticated pages render
python3 check-detail-pages.py <email> <password>       # admin detail pages, with real ids
python3 check-chat.py "how much is a math teacher?"     # ask the assistant something
```

`check-links.py` currently reports **110 routes, 0 broken links**.

---

## Committed credentials

`application.yml` and `docker-compose.yml` contain a real MongoDB Atlas URI and a real Gmail app
password, so the project runs on a clone with no setup. That is a development convenience and it is
**not** how this should be deployed.

Before running anywhere shared:

1. Rotate the Atlas database password and put the new URI in a secret store or `MONGODB_URI`.
2. Revoke the Gmail app password and issue a new one, then set `MAIL_PASSWORD`.
3. Set `OTP_SECRET` to a long random string. The committed value is a placeholder.
4. Set `ADMIN_PASSWORD` for the bootstrap administrator, or leave both admin variables blank and
   create the account another way.
5. Move both files to `${ENV_VAR}` only, with no default.

MongoDB Atlas enforces its own network allow-list, so restricting the cluster to known addresses
limits the blast radius of the committed URI in the meantime.