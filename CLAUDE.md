# StampX — Project Documentation

## Purpose
Multi-tenant digital loyalty card SaaS. Local shops register and get a branded Apple Wallet stamp card. Customers download the card and collect stamps. When they hit the stamp goal a reward is created. The shop redeems it at the counter.

---

## Tech Stack
| Layer | Technology |
|---|---|
| Backend API | Spring Boot 3.2, Java 21 |
| Database | PostgreSQL 16 (Docker locally, Railway in prod) |
| ORM / migrations | Spring Data JPA + Hibernate, Flyway 10 |
| Security | Spring Security 6 — session auth + API-key auth |
| QR generation | ZXing 3.5.3 |
| Pass generation | Node.js 20 + passkit-generator 4.x |
| APNs push | node-apn 2.x |
| Build tool | Maven 3.9 |

---

## Folder Structure
```
StampX/
├── CLAUDE.md
├── .env.example
├── .gitignore
├── docker-compose.yml
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/stampx/
│       │   ├── StampXApplication.java
│       │   ├── config/          # SecurityConfig, CorsConfig, AppConfig, ApiKeyAuthFilter
│       │   ├── controller/      # AuthController, DashboardController, StampController,
│       │   │                    #   PassController, AppleWalletController, AdminController
│       │   ├── dto/             # Java 21 records — never expose entities directly
│       │   ├── exception/       # NotFoundException, ConflictException, GlobalExceptionHandler
│       │   ├── model/           # Shop, Customer, StampEvent, Reward (JPA entities)
│       │   ├── repository/      # Spring Data JPA repositories — always scoped by shopId
│       │   └── service/         # ShopService, CustomerService, StampService, PassService,
│       │                        #   WalletService (interface), AppleWalletService,
│       │                        #   QRCodeService, DashboardService
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           ├── application-prod.yml
│           └── db/migration/    # V1–V4 Flyway SQL files
└── pass-service/
    ├── Dockerfile
    ├── index.js
    ├── package.json
    ├── model/
    │   └── pass.json            # passkit-generator template
    └── certs/                   # GITIGNORED — Apple certs go here at deploy time
```

---

## Database Schema

### shops
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | gen_random_uuid() |
| name | VARCHAR(255) | |
| slug | VARCHAR(100) UNIQUE | URL-safe identifier |
| logo_url | TEXT | |
| primary_color | VARCHAR(7) | hex e.g. #3B82F6 |
| stamp_goal | INTEGER | stamps needed for reward |
| reward_description | TEXT | |
| email | VARCHAR(255) UNIQUE | login credential |
| password_hash | VARCHAR(255) | BCrypt strength 12 |
| api_key | VARCHAR(255) UNIQUE | SHA-256(rawUUID) hex — see API Key section |
| created_at | TIMESTAMP | DEFAULT NOW() |

### customers
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| shop_id | UUID FK→shops | ON DELETE CASCADE |
| pass_serial | VARCHAR(255) UNIQUE | used to identify the pass |
| device_library_id | VARCHAR(255) | set when Apple Wallet registers |
| push_token | VARCHAR(255) | APNs token |
| stamp_count | INTEGER | DEFAULT 0; reset to 0 after reward |
| created_at | TIMESTAMP | |

### stamp_events
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| shop_id | UUID FK→shops | |
| customer_id | UUID FK→customers | |
| stamped_at | TIMESTAMP | DEFAULT NOW() |

### rewards
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| shop_id | UUID FK→shops | |
| customer_id | UUID FK→customers | |
| redeemed_at | TIMESTAMP | NULL = pending |

---

## API Endpoints

### Auth (session-based)
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | /api/auth/register | none | Register shop; returns rawApiKey once |
| POST | /api/auth/login | none | Sets session cookie |
| POST | /api/auth/logout | session | Clears session |
| GET | /api/auth/me | session | Returns current shop |

### Dashboard (session-based)
| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | /api/dashboard/stats | session | totalCustomers, stampsToday, pendingRewardsCount |
| GET | /api/dashboard/rewards/pending | session | List unredeemed rewards |
| POST | /api/dashboard/rewards/{id}/redeem | session | Mark reward redeemed |
| GET | /api/dashboard/qrcode | session | PNG QR code bytes |

### Public (no auth)
| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | /api/shops/{slug} | none | Public shop info |
| GET | /api/pass/download/{slug} | none | Download .pkpass; creates Customer row |

### Stamp (API key)
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | /api/stamp/{passSerial} | X-Api-Key header | Increment stamp, create reward if goal hit |

### Apple Wallet Web Service
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | /v1/devices/{deviceLibraryId}/registrations/{passTypeId}/{serial} | none | Register device |
| DELETE | /v1/devices/{deviceLibraryId}/registrations/{passTypeId}/{serial} | none | Unregister device |
| GET | /v1/passes/{passTypeId}/{serial} | none | Get latest pass bytes |
| GET | /v1/devices/{deviceLibraryId}/registrations/{passTypeId} | none | List updated serials |
| POST | /v1/log | none | Apple Wallet diagnostic logs |

### Admin
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | /api/admin/onboard | X-Admin-Secret header | Manually create shop |

---

## Environment Variables
```
DB_URL=jdbc:postgresql://localhost:5432/stampx
DB_USERNAME=stampx
DB_PASSWORD=changeme
SESSION_SECRET=change-this-very-long-secret
ADMIN_SECRET=change-this-admin-secret
PASS_SERVICE_URL=http://pass-service:3001
APPLE_PASS_TYPE_IDENTIFIER=pass.com.yourcompany.stampx
APPLE_TEAM_IDENTIFIER=XXXXXXXXXX
BASE_URL=http://localhost:8080
SPRING_PROFILES_ACTIVE=dev
```

---

## Multi-Tenancy
- **shop_id always comes from the authenticated principal** — never from the request body or URL for sensitive operations.
- All repository queries accept `shopId` as an explicit parameter.
- No `findAll()` calls anywhere in the codebase.
- Service layer is the enforcement boundary: controllers pass `shop.getId()` extracted from `SecurityContextHolder`.

---

## API Key Design
Raw UUID key is generated on registration and returned **once** — never stored or logged.
The stored value is `SHA-256(rawKey)` as a lowercase hex string.
On each `/api/stamp/*` request, `ApiKeyAuthFilter` computes `SHA-256(incoming header value)` and does `WHERE api_key = ?`. This is secure because:
1. The raw key is 128-bit UUID — resistant to brute force even without a salt.
2. SHA-256 is deterministic so the DB lookup works with a simple index.

---

## Apple Wallet Integration

### Pass Download Flow
1. Customer visits `GET /api/pass/download/{slug}`
2. Backend generates a UUID `passSerial`, creates a `Customer` row, calls pass-service `/generate`
3. pass-service builds a `.pkpass` file using `passkit-generator`
4. Backend streams the `.pkpass` back to the browser
5. iOS opens it in Wallet; Wallet calls the web service endpoints to register the device

### Stamp Flow (end-to-end)
1. Merchant scans customer's pass QR or NFC — the QR encodes the `passSerial`
2. Merchant's POS/app sends `POST /api/stamp/{passSerial}` with `X-Api-Key` header
3. `StampService.stampCustomer`:
   - Increments `stamp_count`
   - Saves a `StampEvent`
   - If `stamp_count >= stamp_goal`: creates `Reward`, resets `stamp_count` to 0
   - After transaction commits: sends APNs push via pass-service `/push`
4. Apple Wallet receives silent push → calls `GET /v1/passes/{passTypeId}/{serial}`
5. Backend calls pass-service `/generate` with new stamp_count → returns updated pass bytes

### Apple Wallet Web Service Notes
- `GET /v1/devices/{deviceLibraryId}/registrations/{passTypeId}` MUST return **204** (not 200 with empty array) when no passes are updated — Apple's client breaks otherwise.
- `GET /v1/passes/{passTypeId}/{serial}` should include a `Last-Modified` header.
- The `authenticationToken` in the pass equals `passSerial` for simplicity. Validate the `Authorization: ApplePass {token}` header in production.
- Certificates go in `pass-service/certs/` (gitignored). Required: `signerCert.pem`, `signerKey.pem`, `wwdr.pem`. For APNs: `AuthKey.p8`.

---

## CORS Configuration
Configured via `CorsConfig.java` (`WebMvcConfigurer` / `CorsConfigurationSource`):
- **Allowed origins:** `http://localhost:4200`, `https://your-frontend.vercel.app`
- **Allowed methods:** GET, POST, DELETE, OPTIONS
- **Allowed headers:** `*`
- **Allow credentials:** `true` (required for session cookie)

---

## How to Run Locally

### Prerequisites
- Docker Desktop running
- Copy `.env.example` → `.env` and fill values
- Add Apple certs to `pass-service/certs/` (optional for non-pass features)

```bash
docker-compose up --build
```

Services start on:
- Backend: http://localhost:8080
- pass-service: http://localhost:3001
- PostgreSQL: localhost:5432

### Quick smoke test
```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","slug":"test","email":"a@b.com","password":"password123","stampGoal":5,"rewardDescription":"Free coffee","primaryColor":"#3B82F6","logoUrl":""}'

# Login (saves cookie)
curl -c c.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"a@b.com","password":"password123"}'

curl -b c.txt http://localhost:8080/api/dashboard/stats
```

---

## Deployment (Railway)
- Push to GitHub; connect repo in Railway dashboard.
- Add all env vars from `.env.example` in Railway's variable panel.
- Railway auto-detects the `backend/Dockerfile` (multi-stage Maven → JRE Alpine).
- PostgreSQL: provision a Railway Postgres plugin; set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
- pass-service: deploy as a second Railway service; share `APPLE_*` vars.

---

## Development Rules
- **Never** use `ddl-auto=create` or `ddl-auto=update`. Schema changes require a new Flyway migration file.
- **Never** call `findAll()` on any repository — always scope by `shopId`.
- **Never** expose JPA entities in API responses — map to DTOs.
- **Always** add a new `VN__description.sql` file for schema changes, incrementing N.
- Update this file when adding major features or endpoints.
