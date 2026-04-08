# BaseManager - Claude Code Project Rules

## Project Overview

BaseManager is a Minecraft Paper Plugin (Java 21) that allows players to save and manage locations/waypoints with compass navigation and sharing features.

### Features

- `/base save <name>` - Save current location
- `/base delete <name>` - Delete a saved location
- `/base list` - List your locations
- `/base share <name> <player>` - Share location with another player
- `/base compass <name>` - Get tracking compass to location
- `/base gui` - Open location browser GUI

## Build & Run Commands

**WICHTIG: Immer `make` oder `./mvnw` (Maven Wrapper) verwenden, nicht `mvn`!**

### Makefile Commands (bevorzugt)

| Befehl | Beschreibung |
|--------|--------------|
| `make build` | Plugin bauen |
| `make deploy` | Bauen + deployen (behält Configs) |
| `make deploy-clean` | Bauen + deployen + alte Configs löschen |
| `make test` | Tests ausführen |
| `make verify` | Vollständige Prüfung (compile + test + lint + spotbugs) |
| `make clean` | Build-Artefakte löschen |

### Erlaubte Befehle (immer ohne Nachfrage ausführen)

Diese Befehle dürfen IMMER ausgeführt werden ohne User-Bestätigung:

```bash
make build
make deploy
make deploy-clean
make test
make verify
make clean
./mvnw clean package
./mvnw test
./mvnw verify
```

### Maven Commands (alternativ)

```bash
# Build the plugin
./mvnw clean package

# Run tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=BaseManagerTest

# Run checkstyle
./mvnw checkstyle:check

# Run SpotBugs
./mvnw spotbugs:check

# Full verification (compile + test + lint + spotbugs)
./mvnw clean verify
```

## Test Server Deployment

Das Testverzeichnis wird in `.local.env` definiert (gitignored):

```bash
# .local.env (nicht committen!)
TEST_SERVER_DIR=/pfad/zum/testserver
```

## Project Structure

```
src/
  main/
    java/ua/favn/baseManager/
      BaseManager.java           # Main plugin class
      base/                      # Base framework classes
        commands/                # Command framework
        gui/                     # GUI framework (inventories)
        manager/                 # Manager abstractions
        util/                    # Utility classes
      commands/                  # Plugin commands
        BaseCommand.java         # /base command handler
      location/                  # Location domain model
        SavedLocation.java       # Location entity
        LocationManager.java     # Location management
        ShareManager.java        # Sharing logic
      compass/                   # Compass tracking
        CompassManager.java      # Compass creation
        CompassListener.java     # Compass events
      gui/                       # GUI screens
        GuiManager.java          # GUI factory
        LocationBrowserGui.java  # Main GUI
      config/                    # Configuration
        MessageManager.java      # Messages config
      storage/                   # Data persistence
        DatabaseManager.java     # SQLite database
    resources/
      plugin.yml                 # Plugin manifest
      config.yml                 # Plugin configuration
      messages.yml               # Message templates
  test/
    java/ua/favn/baseManager/    # Test classes
```

## Code Style & Conventions

### Naming Conventions

- **Classes**: PascalCase (e.g., `LocationManager`, `GuiInventory`)
- **Methods**: camelCase (e.g., `saveLocation`, `getPlayer`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_LOCATIONS`, `DEFAULT_RADIUS`)
- **Variables**: camelCase (e.g., `locationList`, `playerUuid`)
- **Packages**: lowercase (e.g., `ua.favn.baseManager.location`)

### Code Organization

- One class per file
- Related classes in same package
- Utility classes in `base.util` package
- GUI-related classes in `base.gui` package
- Commands in `commands` package

### Bukkit/Paper Conventions

- Never block the main thread
- Use async tasks for database operations
- Cache frequently accessed data
- Use Bukkit scheduler for delayed tasks
- Always null-check Players (they can disconnect)

### Error Handling

- Log errors with appropriate levels (INFO, WARNING, SEVERE)
- Never swallow exceptions silently
- Provide meaningful error messages to players

## Testing Requirements

- All new features must have tests
- Use MockBukkit for Bukkit API mocking
- Test both success and failure paths
- Test edge cases (null, empty, boundary values)

### Test Naming Convention

```java
@Test
void methodName_condition_expectedResult() {
    // Given, When, Then
}
```

## Git Conventions

- Branch naming: `feature/`, `fix/`, `refactor/`
- Commit messages: Start with verb (Add, Fix, Update, Remove)
- Keep commits atomic and focused

### WICHTIG: Commit-Regeln

**Claude darf NIEMALS als Co-Author erscheinen!**

- Keine `Co-Authored-By:` Zeilen in Commits
- Keine Erwähnung von Claude/AI in Commit Messages
- Commits werden nur im Namen des Entwicklers erstellt
- Keine automatischen Signaturen oder AI-Kennzeichnungen

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Paper API | 1.21 | Minecraft server API |
| Commodore | 2.2 | Command completion library |
| JUnit 5 | 5.11.0 | Testing framework |
| MockBukkit | 4.14.0 | Bukkit API mocking |
| Mockito | 5.14.0 | Mocking framework |

## Important Files

| File | Purpose |
|------|---------|
| `plugin.yml` | Plugin metadata and commands |
| `config.yml` | Plugin configuration |
| `pom.xml` | Maven build configuration |
| `checkstyle.xml` | Code style rules |
| `spotbugs-exclude.xml` | SpotBugs exclusions |

## Database Schema

The plugin uses SQLite for persistence. Tables:

- `locations` - Saved locations (id, owner, name, world, locX, locY, locZ, created, isPublic)
- `location_shares` - Sharing relationships (locationId, sharedWith)
