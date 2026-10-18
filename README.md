# Blocker

A free, open-source website blocker for Android, built for self-control. It has no ads, no
accounts and no tracking.

Blocker blocks the websites you choose in every browser and app. It's hard to switch off on
purpose: once you lock it with a password, the only ways out are the password or a long
emergency code that you have to copy onto paper and type back in.

Works on Android 8.0 and newer.

## Features

- **Blocks websites everywhere**: every browser and every app, including subdomains
  (`facebook.com` also blocks `www.facebook.com` and `m.facebook.com`).
- **Sensible defaults**: well-known adult sites, YouTube (including the app) and X/Twitter are
  blocked out of the box. You can add your own sites.
- **Password lock**: stopping the filter, removing sites and turning off protection all need the
  password. Adding sites never does.
- **Random password**: for maximum strictness, set a 64-character password nobody knows.
- **Emergency code**: the hard way out. The app shows it as an image on a separate screen.
  Screenshots, copying and pasting are blocked, so you have to write it on paper and type it in.
- **Uninstall protection**: Blocker becomes a device admin, so it can't be uninstalled,
  force stopped or have its data cleared while it's protected.
- **Settings guard**: while Blocker is locked, an accessibility service closes the Settings
  screens that would turn the protection off.
- **Step-by-step help cards** on first start (and under *Help*). Each card has a button that
  opens the right Settings screen.
- **Private**: everything happens on the phone. See [PRIVACY.md](PRIVACY.md).

## How it works

Blocker runs a *local* VPN that only carries website-name lookups (DNS). Android is told to use a
fake DNS server inside the VPN, and only that one address is routed into it. All other traffic
goes straight to your network as usual.

Lookups for blocked domains get a "no such domain" answer, so the site doesn't load. All other
lookups go to your network's normal DNS server, or to Cloudflare (1.1.1.1) or Google
(8.8.8.8) if that doesn't answer. Blocker never sees page content, passwords or messages,
and it doesn't log anything.

To stop browsers from going around the filter with encrypted DNS, the hostnames of well-known
DNS-over-HTTPS providers are always blocked.

## Install

Build it from source (see below) and install the APK, or get it from the
[Releases](https://github.com/Belafon/Blocker/releases) page once one is published. The app opens
with help cards that walk you through the setup. In short:

1. Start the filter and accept the VPN request.
2. Optionally turn off Blocker's notifications. The filter keeps running.
3. Allow Blocker to run in the background without battery restrictions. On Xiaomi phones,
   also turn on *Autostart*.
4. Turn on *Always-on VPN* for Blocker, so the filter starts right after a restart.
   **Leave "Block connections without VPN" off.** Blocker's VPN carries only DNS, so with that
   option on, no internet works at all.
5. Enable uninstall protection (device admin).
6. Turn on the Settings guard (accessibility). On Android 13+, apps installed outside the
   Play Store first need *App info → ⋮ → Allow restricted settings*.
7. **Last**, set a password. Once Blocker is locked, the guard closes the Settings screens from
   the previous steps.

## Emergency code

```
eE!7 HjLi %Lg4 %e3H hq^F s2eC XyTC nng5 w9Xj 6HqV Y$a) 6qqD ($8q wF5f 3KDW Cyu^
```

Yes, it's public, on purpose. It's not a secret. It's there so that unlocking takes real effort
instead of a quick tap. Upper and lower case matter, spaces don't. The code uses letters, digits
and `!@#$%^&*()`, but never the look-alikes `0 O o 1 l I`.

It's defined in
[`EmergencyCode.java`](app/src/main/java/io/github/belafon/blocker/EmergencyCode.java). To use
your own, change it there before building: 64 random characters from `EmergencyCode.ALPHABET`.

## Uninstalling

Unlock first, with the password (*Remove password*) or with the emergency code, then
uninstall normally. Using the emergency code also turns off the device admin.

## Building

Requirements: JDK 17 and the Android SDK (platform 36). There are no third-party dependencies.

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Limitations

Blocker is a self-control tool, not a parental-control system. Someone determined can still get
around it:

- **Safe mode** turns off third-party apps (the filter and the guard). The device admin still
  blocks uninstalling, but it can be deactivated in Settings while in safe mode.
- **ADB** (USB debugging) can remove the device admin and uninstall the app.
- A browser set to a **custom DNS-over-HTTPS** server, or another VPN or proxy, can bypass DNS
  filtering.
- The guard recognises Settings screens by their content. Some phone makers' Settings apps
  look different, so it may miss some screens there.
- While locked, the guard also closes the main Accessibility settings page, because that page
  lists *Blocker Guard*.
- Sites opened shortly before you blocked them may keep working for a minute because of DNS
  caching.

## License

[GPL-3.0](LICENSE). You're free to use, study, modify and share it. Modified versions have to
stay open source under the same license.
