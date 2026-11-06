# Group Blend backend

Group Blend uses its **own Supabase project**, separate from Listen Together. Two projects keep each feature inside its own free-tier quota and keep one product's traffic or data from affecting the other.

| Feature | Project | Credential on the Android build |
|---|---|---|
| Group Blend | dedicated Blend project | `KATMUSIC_BLEND_SUPABASE_URL` + publishable/anon key |
| Listen Together | dedicated Jam project | none; the Go server holds the service-role key |

Never put a service-role/secret key in the Android app. The client uses the publishable key with Supabase Auth and row-level security.

## Setup

1. Create a Supabase project for Group Blend only.
2. Open the SQL Editor and run `server/blend/supabase/migrations/0001_blend_schema.sql`.
3. In Authentication, choose whether email confirmation is required.
4. Add the project URL and publishable/anon key to the root `.env` (ignored by git), `local.properties`, or the repository's GitHub Actions secrets:

```properties
KATMUSIC_BLEND_SUPABASE_URL=https://YOUR-BLEND-PROJECT.supabase.co
KATMUSIC_BLEND_SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLISHABLE_OR_ANON_KEY
```

5. Rebuild the app. The Group Blend screen reports when cloud configuration is missing.

## What the migration provides

- `blend_profiles` with compact taste profiles and row-level security.
- `blends`, `blend_members` and hashed invitation codes.
- Authenticated `create_blend`, `join_blend` and `leave_blend` functions.
- Membership limits and owner-only cleanup.

## Local-only fallback

Blend matching still works offline from the on-device taste profile. Cloud accounts, shared membership and cross-device profile sync require the configuration above.
