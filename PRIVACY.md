# Privacy policy

*Last updated: October 18, 2026*

Blocker is an open-source website blocker for Android. This policy explains what the app does
with your data. In short: **Blocker doesn't collect, store remotely, share or sell any data.**

## What the app processes, and where

**Website-name lookups (DNS).** To block websites, Blocker runs a local VPN on your phone that
receives the DNS lookups your apps make (for example "youtube.com"). Blocker checks each name
against your block list on the phone. Blocked names get a "no such domain" answer. Allowed names
are forwarded to your network's normal DNS server, or to Cloudflare (1.1.1.1) or Google (8.8.8.8)
DNS if that doesn't answer, exactly as your phone would do without Blocker. Lookups are not
logged, stored or sent anywhere else. No other traffic (page content, passwords, messages)
passes through the VPN.

**Settings guard (accessibility service).** If you turn it on, the guard watches only the Settings
app, the system VPN dialogs and the app installer. It checks whether a screen that would turn off
Blocker's protection is open, and if so, it goes back to the home screen. It doesn't read other
apps, and it doesn't store or send anything.

**Device admin.** Blocker uses device admin only to prevent being uninstalled. It doesn't use
any device admin powers (it can't lock, wipe or change your phone).

**Stored on your phone.** Your block list, settings and a salted hash of your password (never the
password itself) are stored only in the app's private storage on your phone. Android backup is
turned off for the app.

## What the app does not do

- No analytics, crash reporting, ads or tracking.
- No accounts, no servers run by the developer, no data sent to the developer.
- No data sharing or selling.

## Open source

The full source code is public at https://github.com/Belafon/Blocker, so you can check all of
the above.

## Contact

Questions: open an issue at https://github.com/Belafon/Blocker/issues.
