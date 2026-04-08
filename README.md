# Location Manager

A Minecraft Paper plugin for managing shared waypoints using lodestones, signs, and tracking compasses.

Players create locations by placing a sign on a lodestone and waxing it with honeycomb. Locations are visible to all players through a GUI browser and can be navigated to with tracking compasses that show distance in the action bar.

## Features

- **Lodestone + sign registration** with wax-to-confirm flow
- **Multi-dimension support** - Overworld and Nether locations linked by tag:name
- **Tracking compass** with real-time distance in action bar
- **Compass needle updates** when changing dimensions
- **Item frame icons** - use banners, player heads, or any item as location icon in the GUI
- **Per-player item frame glowing** when holding a compass (via ProtocolLib)
- **GUI browser** with pagination and filters
- **Tag system** - organize locations with custom tags
- **SQLite persistence** with multi-dimension coordinate storage

## Requirements

- Paper or Purpur 1.21+
- Java 21
- [ProtocolLib](https://github.com/dmulloy2/ProtocolLib) (optional - enables per-player item frame glowing)

## Installation

1. Download `basemanager-x.x.x.jar` from [Releases](https://github.com/paraf0x/location-manager/releases)
2. Place in your server's `plugins/` folder
3. (Optional) Install ProtocolLib for item frame glowing
4. Restart the server

## Location Lifecycle

```mermaid
flowchart TD
    Start([Player at Lodestone]) --> PlaceSign[Place sign on lodestone]
    PlaceSign --> SetTag["Line 1: Tag (e.g. team name)"]
    SetTag --> SetName["Lines 2-4: Location name"]
    SetName --> OptFrame{Item frame\non lodestone?}
    OptFrame -->|Yes| AddItem["Place item in frame\n(banner, head, etc.)"]
    OptFrame -->|No| Wax
    AddItem --> Wax

    Wax["Wax sign with honeycomb"] --> CheckDup{Same tag:name\nexists?}
    CheckDup -->|No| Create[New location created]
    CheckDup -->|Yes, other dimension| Link["Coords added to\nexisting location"]
    CheckDup -->|Yes, same dimension| Block[Blocked - duplicate]

    Create --> Registered((Location\nRegistered))
    Link --> Registered

    Registered --> Use

    subgraph Use [Usage]
        GUI["GUI Browser\n/loc"]
        Compass["Tracking Compass\nwith action bar"]
        Glow["Item frame glows\nfor compass holder"]
        GUI --> Compass
        Compass --> Glow
    end

    Registered --> Delete

    subgraph Delete [Deletion]
        BreakLodestone["Break lodestone"] --> Removed
        BreakSign["Break sign"] --> Removed
        Removed((Location\nDeleted))
    end

    style Registered fill:#4a9,stroke:#333,color:#fff
    style Removed fill:#c44,stroke:#333,color:#fff
    style Block fill:#c44,stroke:#333,color:#fff
```

## Cross-Dimension Compass

When a location has coordinates in both Overworld and Nether, the tracking compass automatically switches between them:

- **In Overworld**: Compass points to the Overworld coordinates, action bar shows distance
- **Enter Nether portal**: Compass needle updates to point to the Nether coordinates
- **Return to Overworld**: Compass switches back to Overworld coordinates
- **No coords in current dimension**: Compass spins freely, action bar shows "No base here"

Auto-dispose only triggers in the **origin dimension** (where you got the compass). Walking past a Nether portal won't accidentally consume your compass.

```mermaid
flowchart LR
    OW["Overworld\nCompass points to OW coords"] -->|Enter Nether| NE["Nether\nCompass points to Nether coords"]
    NE -->|Return to Overworld| OW
    NE -->|No Nether coords| Spin["Compass spins\n'No base here'"]
```

## Sign Format

```
+------------------+
| [WOLVES]         |  <- Line 1: Tag (with or without brackets, empty = "BASE")
| Main Base        |  <- Line 2: Name part 1
| North            |  <- Line 3: Name part 2 (optional)
|                  |  <- Line 4: Name part 3 (optional)
+------------------+
```

Result: Tag = `WOLVES`, Name = `Main Base North`

- Lines 2-4 are joined with spaces, empty lines ignored
- Tag is case-insensitive, stored uppercase
- Any tag is allowed (no whitelist)

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/loc` | Open GUI browser | `basemanager.use` (all) |
| `/loc save <tag> <name>` | Save current location | `basemanager.admin` (op) |
| `/loc delete <tag> <name>` | Delete a location | `basemanager.admin` (op) |
| `/loc compass <tag> <name>` | Get tracking compass | `basemanager.admin` (op) |
| `/loc icon <tag> <name> <material>` | Set location icon | `basemanager.admin` (op) |
| `/loc list` | List all locations | `basemanager.admin` (op) |
| `/loc reload` | Reload configuration | `basemanager.reload` (op) |

Aliases: `/base`, `/b`, `/location`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `basemanager.use` | Open the location GUI | All players |
| `basemanager.admin` | Use management commands | OP |
| `basemanager.reload` | Reload configuration | OP |

## Configuration

```yaml
# Compass settings
compass:
  auto-dispose-on-arrival: true   # Remove compass when arriving
  arrival-radius: 10              # Blocks distance for arrival trigger
  show-distance-actionbar: true   # Show distance while holding compass
  actionbar-update-ticks: 20      # Update interval (20 = 1 second)

# Location limits
limits:
  max-locations-per-player: 50    # 0 = unlimited
  min-name-length: 2
  max-name-length: 32

# Database
database:
  file: storage.db                # SQLite file in plugin folder

# Lodestone registration
lodestone:
  enabled: true
  allowed-tags: []                # Empty = any tag allowed
```

## Building

```bash
# Build the plugin
./mvnw clean package

# Run tests
./mvnw test

# Full verification (compile + test + lint + spotbugs)
./mvnw clean verify
```

The built JAR is at `target/basemanager-x.x.x.jar`.
