# VoicePing Dispatch Console Manual

## Roles
- Admin: full system setup, event configuration, users, invites, system settings.
- Dispatch: manage roster approvals and monitor teams/channels.

## First login (bootstrap admin)
1. Open `http://localhost:8080`.
2. Log in with the bootstrap credentials from `.env.test.example`.
3. You will be required to set:
   - Email
   - Display name
   - Strong password (12+ characters)
4. After saving, you are redirected to the Events page.

## Admin guide

### Create an event
1. Open the Events page.
2. Select **Create Event**.
3. Enter name and limits, then **Create**.
4. Select the event to open the console.

### Configure event settings
1. In the console, open the settings (gear icon).
2. Open **Event** tab.
3. Update name, approval requirement, and limits.
4. Save.

### Create teams and channels
1. Open **Teams** tab to add teams.
2. Open **Channels** tab to add:
   - Admin channels (event-wide)
   - Team channels (tied to a team)

### Add users
1. Open **Users** tab.
2. Create a user (email + temporary password).
3. Assign the user to the event and choose role (User or Dispatch).
4. Optionally require password reset for the next login.

### Approve pending users
1. In the main roster panel, pending users are shown with **Approve**.
2. Approving activates the user and updates channel membership.

### Create invites
1. Open **Invites** tab.
2. Optionally set:
   - Team
   - Default channels
   - Expiry minutes
   - Max uses
   - Email
3. Click **Create invite** and share the token or email.

### System settings
1. Open **System** tab.
2. Update SMTP settings for email invites and password resets.

## Dispatch guide

### Open an event
1. Log in and select an event.
2. The console opens with roster, teams, and channels.

### Approve users
1. Check the roster panel for pending entries.
2. Click **Approve** to activate.

### View settings
1. Open the settings (gear icon).
2. Toggle roster/teams/channels visibility.
3. Adjust density and sound preference.

## Troubleshooting
- **Login hangs or fails**: verify control-plane is reachable and Redis is running.
- **Redirected to first-run**: you must complete profile setup before accessing the console.
- **No events visible**: admin must create an event before dispatch users can access it.
- **Invite emails not sent**: ensure SMTP settings are configured in System tab.
- **Loopback or playback errors**: some browsers emit empty Opus frames on stop; the UI skips invalid packets. Ensure both users are active in the channel and use Chrome for transmit.
