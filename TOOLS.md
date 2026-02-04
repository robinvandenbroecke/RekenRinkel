# TOOLS.md - Local Notes

Skills define _how_ tools work. This file is for _your_ specifics — the stuff that's unique to your setup.

## What Goes Here

Things like:

- Camera names and locations
- SSH hosts and aliases
- Preferred voices for TTS
- Speaker/room names
- Device nicknames
- Anything environment-specific

## Examples

```markdown
### Cameras

- living-room → Main area, 180° wide angle
- front-door → Entrance, motion-triggered

### SSH

- home-server → 192.168.1.100, user: admin

# Synology (via Tailscale)
- synology-vandenbroecke → 100.112.100.14 (vandenbroecke.tailab32cb.ts.net)
  - ssh user: vdb-nys (root might also work if enabled)
  - ssh port: 22
  - quick test: ssh vdb-nys@100.112.100.14

### Radarr / Sonarr

- Sonarr (series): http://100.112.100.14:8989
- Radarr (films): http://100.112.100.14:8310
- Preferences:
  - quality: 720p or 1080p
  - avoid: CAM
  - when adding: don’t auto-search unless requested

(⚠️ API keys are stored in ~/.openclaw/openclaw.json env.vars, not in this repo.)

### TTS

- Preferred voice: "Nova" (warm, slightly British)
- Default speaker: Kitchen HomePod
```

## Why Separate?

Skills are shared. Your setup is yours. Keeping them apart means you can update skills without losing your notes, and share skills without leaking your infrastructure.

---

Add whatever helps you do your job. This is your cheat sheet.
