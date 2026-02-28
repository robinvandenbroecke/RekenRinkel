# Long-Term Memory

## Important Information
Created: Sun Feb 15 12:00:03 AM UTC 2026
Last updated: Sat Feb 28 17:35:00 UTC 2026

## CRITICAL TECHNICAL LESSONS LEARNED (2026-02-24)

### 1. SYNOLOGY NAS CALIBRE RESTORATION
**Situation**: Complete Calibre Docker backup restoration on Synology NAS
**Problem**: Two library paths existed - 49GB backup vs 604KB empty library
**Solution**: ChatGPT 5.2 successfully restored full setup with HTTPS access

**Key Technical Insights:**
- **Backup Location**: `/volume1/Docker/calibre/` (49GB complete backup)
- **Correct Library**: `/volume1/Docker/calibre/calibre books` (use this)
- **Wrong Library**: `/volume1/Media/Calibre Library` (604KB empty - DO NOT USE)
- **Docker on Synology**: Located at `/usr/local/bin/docker`, requires sudo
- **Sudo Password**: `Wouterok1!`

**Container Architecture:**
1. **calibre-gui** (linuxserver/calibre): Full desktop interface
   - Mounts: `/config` → `/volume1/Docker/calibre`
   - Mounts: `/books` → `/volume1/Docker/calibre/calibre books`
   - Network: `calibre-net`

2. **calibre-final** (linuxserver/calibre-web): Lightweight web interface
   - Port: 8080:8083
   - Mounts: Same library path as GUI

3. **calibre-proxy** (nginx): HTTPS reverse proxy
   - Port: 8443:443
   - Config: `/volume1/docker/reverse-proxy/conf.d/calibre.conf`

**Access URLs:**
- 🔒 **HTTPS (GUI)**: `https://vandenbroecke-1.tailab32cb.ts.net:8443`
- 🌐 **HTTP (Web)**: `http://100.89.210.124:8080`
- 🎯 **Tailscale IP**: `100.89.210.124`
- 🏠 **Local IP**: `192.168.0.38`

**Critical SSH Configuration:**
```bash
# Primary connection
ssh synology-claw  # via ~/.ssh/config

# Key location
/root/.ssh/clawdbot_synology_ed25519_nopw
```

### 2. ROBIN'S WORKING STYLE & PREFERENCES
**Technical Problem-Solving:**
- Prefers **direct solutions** with minimal questions
- Values **critical analysis** over polite filler
- Appreciates **clear trade-offs** (3 options max)
- Wants **actionable steps** (what to do tomorrow)

**Communication Style:**
- **Concise, neutral** - no small talk or excessive empathy
- **Truth-focused** - prefers likely truth with nuance over political correctness
- **Critical thinking** - challenge assumptions, name uncertainties
- **Family-compatible** - solutions must work within family logistics

**Domain Expertise Areas:**
1. **Orthopedics/Traumatology**: Clinical documentation, surgery protocols
2. **Scientific Work**: Studies, datasets, manuscripts, statistical analysis
3. **Language Learning**: Italian (high tempo), Chinese (conversational)
4. **Practical IT**: Home automation, Synology NAS, Docker, networking
5. **Financial/Legal**: BE/DE context, self-employment, risk analysis

### 3. DOCKER & CONTAINER MANAGEMENT PATTERNS
**Successful Patterns:**
- Always **keep old containers** as rollback (rename with `-old-timestamp`)
- Use **named networks** for container communication (`calibre-net`)
- **Mount paths must be exact** - case sensitivity matters on Synology
- **Test connectivity** before declaring success

**Troubleshooting Sequence:**
1. Check container status: `docker ps --filter "name=calibre"`
2. Inspect mounts: `docker inspect <container> | grep -A10 Mounts`
3. Check logs: `docker logs <container> --tail 50`
4. Test service: `curl -I http://localhost:<port>`

### 4. DOCUMENTATION STANDARDS ESTABLISHED
**File Structure:**
- `CALIBRE_SYNO_SETUP.md`: Complete technical documentation
- `TOOLS.md`: Quick reference with essential commands
- `MEMORY.md`: Long-term lessons and patterns

**Documentation Must Include:**
- ✅ **Access URLs** with clear labels (HTTPS/HTTP)
- ✅ **Critical paths** (what to use vs what NOT to use)
- ✅ **Quick commands** for common operations
- ✅ **Troubleshooting steps**
- ✅ **Contact/context** for future reference

### 5. TELEGRAM/MESSAGING INTERFACE RULES (CRITICAL - 2026-02-28)
**Hard Constraint: NO Timeout Messages in Chat**

User explicitly stated: "Ik wil geen time outs meer in de telegram chat en als er een time out is wil ik dat je zelf een oplossing zoekt"

**Enforced Rules:**
1. **Max 10 seconds timeout** for ALL exec calls
2. **NO background exec** without guaranteed cleanup path
3. **NO "Exec failed" or "SIGKILL" messages** ever sent to user
4. **Graceful degradation**: If command times out, try alternative approach silently
5. **Small steps over big searches**: Use `-maxdepth` limits, avoid `find /volume1` without constraints

**Examples:**
```bash
# ❌ FORBIDDEN - Causes timeout messages
find /volume1 -type f ...              # Too slow
exec ... background=true               # Can hang
long-running-command without timeout   # Risky

# ✅ ALLOWED - Guaranteed fast
find /volume1 -maxdepth 2 -type d      # Bounded search
ssh synology "timeout 10 command"      # Explicit timeout
ls -la /specific/path                  # Direct access
```

**If timeout occurs:**
- Handle silently
- Try narrower search (smaller depth, specific path)
- Report findings only, never the timeout itself

### 6. COLLABORATION WITH OTHER MODELS
**For Kimi 2.5 or other models taking over:**
- Read `SOUL.md` first - defines Robin's co-pilot persona
- Read `USER.md` - contains personal context and preferences
- Read `CALIBRE_SYNO_SETUP.md` - complete technical context
- Read **TELEGRAM/MESSAGING INTERFACE RULES above** - critical constraint
- Check `TOOLS.md` - quick reference for Synology access

**Working Philosophy:**
- Be **genuinely helpful, not performatively helpful**
- Have **opinions** - allowed to disagree and prefer things
- Be **resourceful before asking** - try to figure it out first
- Earn **trust through competence** - careful with external actions
- Remember **you're a guest** - treat access with respect

### 6. SECURITY & ACCESS PATTERNS
**Synology Access:**
- Tailscale provides secure tunnel (100.89.210.124)
- SSH keys stored in `/root/.ssh/`
- Docker requires sudo with password `Wouterok1!`
- Shared folders: `/volume1/Calibre_Share/` has symlinks

**Data Safety:**
- Always **preserve backups** before making changes
- Create **rollback containers** with timestamp
- Document **exact paths** to avoid confusion
- Test **connectivity** from multiple angles

---
**NEXT AGENT NOTE**: This memory contains critical context for continuing Robin's technical work. The Calibre setup is now operational with full HTTPS access. Follow the documented patterns for maintenance and troubleshooting.


### 2026-02-15
    ## Orthopedic Presentation Best Practices
      ## Key Insights (43 presentations analyzed):
      1. **Optimal Slide Counts**: Conference=15-25, Resident=30-40, Fellow=25-35
      2. **13-Section Structure**: Title→Disclosures→Objectives→Intro→Anatomy→Diagnostics→Treatment→Technique→Outcomes→Complications→Cases→Summary→References
      3. **Design**: Professional blues/greys, sans-serif fonts (min 18pt), medical images emphasis
      4. **Evidence**: Always mention study design + LOE, recent references (5 years)
      5. **Top Sources**: OrthoBullets (95% quality), SlideShare, academic institutions
      6. **Tools Created**: find_orthopedic_ppts.py, orthopedic_pptx_creator.py, style guide
      7. **For LET**: "Lateral Extra-articular Tenodesis in ACL Reconstruction: Evidence-Based Update"
    
    ## Academic Research Integration
      ## Configured:
      1. **Zotero**: User ID 11043771, API configured, orthopedic collections
      2. **Research Tools**: PubMed/arXiv/Semantic Scholar search, literature review skill
      3. **OpenEvidence/System.com**: API endpoints identified, mock clients created
    
    ## Technical Infrastructure
      ## Fixed: Python symlink (/usr/bin/python → /usr/bin/python3)
      ## Virtual environment with all research dependencies
    
    ## Next Actions
      ## For Future Presentations:
      1. Use orthopedic_pptx_creator.py template
      2. Apply 13-section structure consistently
      3. Follow color/typography guidelines
      4. Integrate evidence from Zotero
      5. Use analysis insights for audience targeting

### 2026-02-13
    ## Decisions Made
      ## Important Decisions
      1. **USER.md comprehensive update** with timezone and context
      2. **Future time references** will use Brussels time as primary
      3. **Cron job scheduling** to consider timezone differences
      4. **Family-compatible solutions** aligned with Belgian daily rhythms
      
    
    ## Next Actions
      ## Next Actions
      1. Check Synology SMART test results (account for timezone)
      2. Test memory system with timezone-aware scheduling

### 2026-02-12
    ## Decisions Made
      ## Important Decisions
      1. Implemented security boundaries between session types
      2. Established 30-day retention policy for raw logs
      3. Created automated summary system structure
      4. Set token limits for different memory levels
      
    
    ## Next Actions
      ## Next Actions
      1. Test memory search functionality with new structure
      2. Create automation scripts for daily summaries

### 3. HEADLESS BROWSER SETUP (2026-02-24)
**Situation**: Need for automated web browser to login to router and network devices
**Solution**: Installed Playwright with Chromium for headless automation

**Installation Details:**
- **Location**: `/root/clawd/scripts/node_modules/playwright`
- **Chromium**: `~/.cache/ms-playwright/chromium-1208/`
- **Version**: Playwright v1.58.2
- **Working Script**: `/root/clawd/scripts/router_login.js`

**Capabilities:**
- Headless browser automation (no GUI needed)
- Screenshot capture for debugging
- Form filling and submission
- Page content extraction
- JavaScript execution on pages

**Usage:**
```bash
cd /root/clawd/scripts
node router_login.js
```

### 4. TAILSCALE SUBNET ROUTER & EXIT NODE (2026-02-24)
**Situation**: Need to access home network devices (192.168.0.x) from VPS
**Solution**: Configured Synology as both subnet router AND exit node

**Configuration:**
- **Synology (vandenbroecke-1)**: Advertises 192.168.0.0/24 + 0.0.0.0/0
- **VPS**: Accepts routes via `tailscale up --accept-routes`
- **Subnet Router**: Makes all 192.168.0.x devices reachable
- **Exit Node**: Routes all internet traffic through home connection

**Commands:**
```bash
# From VPS - activate exit node (use home IP)
sudo tailscale up --exit-node=100.89.210.124 --accept-routes --ssh

# From VPS - deactivate exit node (use VPS IP directly)
sudo tailscale up --exit-node= --accept-routes --ssh

# Check current IP
curl https://ipinfo.io
```

**Router Access:**
- **Router IP**: 192.168.0.1 (Technicolor/Proximus BBOX)
- **Login**: Password only (username not required)
- **Password**: `QHdEAYsHWRH93W9`
- **Access Method**: Web interface at http://192.168.0.1

**Security Note:** Router has JavaScript encryption (sjcl.js) making automated login challenging. Login works via password field + Enter key (login button is JavaScript-triggered).

### 5. NETWORK DISCOVERY PROCEDURE
**Reliable Method - Synology ARP Table:**
```bash
ssh synology-claw "cat /proc/net/arp | grep -v 'IP address'"
```
- Shows all devices that have recently communicated with Synology
- Displays IP, MAC address, and interface
- Updates in real-time as devices connect/disconnect

**Alternative - Nmap Scan:**
```bash
nmap -sn 192.168.0.0/24  # Ping sweep (non-intrusive)
nmap -sV 192.168.0.0/24  # Service detection (slower, more detailed)
```

**Home Network Architecture:**
- **Subnet**: 192.168.0.0/24
- **Router**: 192.168.0.1 (Technicolor)
- **Synology NAS**: 192.168.0.38 (also 100.89.210.124 on Tailscale)
- **Other devices**: Discovered via ARP table
- **Tailscale Gateway**: 100.89.210.124 routes all 192.168.0.x traffic

### 6. NETWORK DEVICE MAPPING (2026-02-24)
**Complete inventory of discovered devices on 192.168.0.0/24:**

| Device | IP Address | MAC Address | Vendor | Notes |
|--------|------------|-------------|--------|-------|
| **Router** | 192.168.0.1 | 02:10:18:28:6b:34 | Technicolor | Proximus BBOX, admin access |
| **Synology NAS** | 192.168.0.38 | (Tailscale) | Synology | Main server, subnet router |
| **Raspberry Pi** | 192.168.0.105 | dc:a6:32:a0:4f:73 | Raspberry Pi Foundation | Single-board computer |
| **Apple Device** | 192.168.0.93 | 20:1e:88:0f:29:0e | Apple Inc | iPhone/iPad/Mac |
| **Samsung Device** | 192.168.0.81 | 98:8d:46:e0:02:45 | Samsung Electronics | Smart TV/Phone/Tablet |
| **Google Device** | 192.168.0.139 | 12:fe:af:c1:96:13 | Google/Nest | Chromecast/Nest/Home |
| **Intel Computer** | 192.168.0.159 | 64:07:f6:15:08:b3 | Intel Corporate | Laptop/PC with Intel WiFi |

**Active Tailscale Devices:**
- **vandenbroecke-1** (Synology): 100.89.210.124 - Exit node + subnet router
- **ubuntu-4gb-nbg1-1** (VPS): 100.80.97.7 - Accepts routes
- **laptop-kdm0a6sa**: 100.72.169.49 - Windows laptop
- **oneplus-cph2581**: 100.111.236.17 - Android phone

**Reserved/Inactive IPs:**
- 192.168.0.2-18: Attempted connections, no MAC resolution
- 192.168.0.23, .245: Reserved/DHCP pool

**Discovery Method:**
```bash
# From Synology (most reliable)
ssh synology-claw "cat /proc/net/arp | grep 192.168"

# Ping sweep to populate ARP table
for i in $(seq 1 254); do
  ping -c 1 -W 1 192.168.0.$i &
done
```
