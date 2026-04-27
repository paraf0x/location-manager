import { describe, test, expect, beforeAll, afterAll, beforeEach } from 'vitest';
import fs from 'node:fs';
import { gunzipSync } from 'node:zlib';
import type { McBot } from 'mc-e2e';
import { acquireServer, releaseServer, getServer, getDb } from './helpers.js';

const BOT_NAME = 'BmTest';
const TEST_SERVER_DIR = process.env.TEST_SERVER_DIR ?? '/Users/maksymvasyukov/purpur-test';
const BASEMANAGER_CONFIG_PATH = `${TEST_SERVER_DIR}/plugins/BaseManager/config.yml`;
const PL3XMAP_MARKERS_PATH = `${TEST_SERVER_DIR}/plugins/Pl3xMap/web/tiles/world/markers/basemanager.json`;

async function rcon(command: string): Promise<string> {
  const r = await getServer().rcon();
  return r.send(command);
}

/** Run a BaseManager command as the bot via execute-as. */
async function asBot(locCommand: string): Promise<string> {
  return rcon(`execute as ${BOT_NAME} at ${BOT_NAME} run ${locCommand}`);
}

/**
 * Extract lore lines from a raw mineflayer slot item. The WindowSnapshot's
 * built-in extractor expects `components['minecraft:lore']` (object form),
 * but mineflayer for 1.21+ actually delivers components as an array of
 * `{ type: 'lore', data: { value: [...] } }` entries. Walk the array.
 */
function extractRawLore(rawSlot: unknown): string[] {
  if (!rawSlot || typeof rawSlot !== 'object') return [];
  const item = rawSlot as { components?: unknown };
  if (!Array.isArray(item.components)) return [];
  for (const entry of item.components) {
    if (!entry || typeof entry !== 'object') continue;
    const e = entry as { type?: string; data?: unknown };
    if (e.type !== 'lore' && e.type !== 'minecraft:lore') continue;
    const data = e.data;
    if (Array.isArray(data)) return data.map(nbtToPlainText);
    if (data && typeof data === 'object') {
      const value = (data as { value?: unknown }).value;
      if (Array.isArray(value)) return value.map(nbtToPlainText);
    }
  }
  return [];
}

function nbtToPlainText(node: unknown): string {
  if (node == null) return '';
  if (typeof node === 'string') {
    try {
      const parsed = JSON.parse(node);
      return extractTextFromJson(parsed);
    } catch {
      return stripFormat(node);
    }
  }
  if (typeof node === 'object') {
    return extractTextFromJson(node);
  }
  return String(node);
}

function extractTextFromJson(node: unknown): string {
  if (node == null) return '';
  if (typeof node === 'string') return stripFormat(node);
  if (typeof node !== 'object') return String(node);
  const obj = node as Record<string, unknown>;
  // Compound-style: { text: 'x', extra: [...] }
  // or prismarine-nbt wrapper: { type: 'compound', value: {...} }
  let text = '';
  if (typeof obj.text === 'string') {
    text += obj.text;
  } else if (obj.text && typeof obj.text === 'object') {
    const v = (obj.text as { value?: unknown }).value;
    if (typeof v === 'string') text += v;
  }
  if (obj.value && typeof obj.value === 'object') {
    text += extractTextFromJson(obj.value);
  }
  if (Array.isArray(obj.extra)) {
    for (const ex of obj.extra) text += extractTextFromJson(ex);
  } else if (obj.extra && typeof obj.extra === 'object') {
    const exVal = (obj.extra as { value?: unknown }).value;
    if (Array.isArray(exVal)) {
      for (const ex of exVal) text += extractTextFromJson(ex);
    }
  }
  return stripFormat(text);
}

function stripFormat(s: string): string {
  return s.replace(/§./g, '');
}

function setPl3xmapPublicOnly(enabled: boolean): void {
  const config = fs.readFileSync(BASEMANAGER_CONFIG_PATH, 'utf8');
  const updated = config.replace(/(^\s*public-only:\s*)(true|false)\s*$/m, `$1${enabled}`);
  if (updated === config) {
    throw new Error(`Could not update public-only in ${BASEMANAGER_CONFIG_PATH}`);
  }
  fs.writeFileSync(BASEMANAGER_CONFIG_PATH, updated, 'utf8');
}

function readPl3xmapMarkers(): Array<{ data?: { image?: string }; options?: { tooltip?: { content?: string } } }> {
  return JSON.parse(fs.readFileSync(PL3XMAP_MARKERS_PATH, 'utf8'));
}

async function waitForMarkerImage(locName: string, timeoutMs = 15000): Promise<string | null> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const markers = readPl3xmapMarkers();
    const hit = markers.find(marker => (marker.options?.tooltip?.content ?? '').includes(`>${locName}<`));
    if (hit) {
      return hit.data?.image ?? null;
    }
    await new Promise(r => setTimeout(r, 500));
  }
  return null;
}

describe('BaseManager E2E', () => {
  let bot: McBot;

  beforeAll(async () => {
    await acquireServer();
    bot = await getServer().createBot(BOT_NAME);
    await rcon(`op ${BOT_NAME}`);
    await rcon(`gamemode creative ${BOT_NAME}`);
    await new Promise(r => setTimeout(r, 1500));
    // Warmup: the FIRST chat the bot sends sometimes doesn't produce a
    // response from plugins (mineflayer/Paper interaction). Use RCON to
    // prime a dummy BaseManager command, then fire a chat to "warm up"
    // the chat pipeline before any test runs.
    await asBot('loc save BASE __warmup');
    await new Promise(r => setTimeout(r, 500));
    await asBot('loc delete BASE __warmup');
    await new Promise(r => setTimeout(r, 500));
    bot.chat('/loc save BASE __warmup2');
    await bot.nextMessage(/saved|Location/i, 10_000).catch(() => { /* tolerate */ });
    await asBot('loc delete BASE __warmup2');
    bot.clearMessages();
  });

  afterAll(async () => {
    if (bot) await bot.disconnect();
    await releaseServer();
  });

  beforeEach(() => {
    bot.clearMessages();
  });

  test('Issue 1+2: item title drops BASE: prefix, lore shows Creator not Owner', async () => {
    await asBot('loc save BASE titleCheck');
    await new Promise(r => setTimeout(r, 500));

    bot.chat('/loc gui');
    const win = await bot.nextWindow(10_000);

    const locItem = win.findSlot(s => s.name === 'titleCheck');
    const filled = win.filledSlots().map(s => ({ name: s.name, item: s.item }));
    expect(locItem, `GUI slots: ${JSON.stringify(filled)}`).toBeDefined();

    expect(locItem!.name).toBe('titleCheck');
    expect(locItem!.name).not.toContain('BASE:');

    // Extract lore from the raw mineflayer window (WindowSnapshot's built-in
    // extractor doesn't support 1.21+ component array format).
    const rawWindow = (bot.raw as unknown as { currentWindow?: { slots?: unknown[] } }).currentWindow;
    const rawSlot = rawWindow?.slots?.[locItem!.index];
    const lore = extractRawLore(rawSlot);
    const loreText = lore.join('\n');

    expect(loreText, `lore was: ${JSON.stringify(lore)}`).toContain('Creator:');
    expect(loreText).not.toContain('Owner:');

    await bot.closeWindow();
    await asBot('loc delete BASE titleCheck');
  });

  test('Issue 3: /loc icon hand stores an HDB head (with custom texture) in DB', async () => {
    // Use a unique-per-run name (short alphanumeric, under 32 chars) so cache
    // state from prior runs can't mask this run.
    const locName = 'iconA' + (Date.now() % 1000000);
    bot.chat(`/loc save BASE ${locName}`);
    await bot.nextMessage(/saved|Location/i, 5_000);

    // Clean inventory, then give the bot a real HDB head (not just a vanilla
    // player_head). `/hdb give <id> <amount> <player>` delivers the head with
    // its SkullMeta PlayerProfile and custom texture intact — this exercises
    // the HDB-specific code path the user originally reported as broken.
    await rcon(`clear ${BOT_NAME}`);
    await new Promise(r => setTimeout(r, 500));
    const hdbResp = await rcon(`hdb give 1 1 ${BOT_NAME}`);
    console.log('DEBUG hdb give response:', hdbResp);
    await new Promise(r => setTimeout(r, 2000));

    // Verify the bot is actually holding a head with profile/texture component.
    const heldIdx = (bot.raw.quickBarSlot ?? 0) + 36;
    const heldSlot = bot.raw.inventory.slots[heldIdx];
    console.log('DEBUG held slot name:', heldSlot?.name,
      'quickBarSlot:', bot.raw.quickBarSlot, 'heldIdx:', heldIdx);
    const hasHead = heldSlot?.name === 'player_head';
    expect(hasHead, `bot should hold player_head but holds: ${heldSlot?.name ?? 'nothing'}`).toBe(true);

    bot.chat(`/loc icon BASE ${locName} hand`);
    await bot.nextMessage(/Icon.*changed|changed to|icon/i, 5_000).catch(() => {});
    await new Promise(r => setTimeout(r, 500));

    const db = getDb();
    const row = db.get<{ icon: string | null; name: string }>(
      "SELECT name, icon FROM locations WHERE tag = 'BASE' AND name = ?",
      locName,
    );
    db.close();
    expect(row, 'Location row should exist').toBeDefined();
    expect(row!.icon, 'icon column should be non-null after hand set').toBeTruthy();
    expect(typeof row!.icon).toBe('string');

    // The icon is a Base64-encoded, gzip-compressed NBT byte blob produced by
    // ItemStack.serializeAsBytes(). Decompress to raw NBT and inspect.
    const iconBytes = Buffer.from(row!.icon!, 'base64');
    console.log('DEBUG icon first bytes (hex):', iconBytes.slice(0, 16).toString('hex'));
    console.log('DEBUG icon size (compressed):', iconBytes.length);
    // GZIP magic 0x1f 0x8b signals compression.
    const rawNbt = iconBytes[0] === 0x1f && iconBytes[1] === 0x8b
      ? gunzipSync(iconBytes)
      : iconBytes;
    console.log('DEBUG icon size (decompressed):', rawNbt.length);
    const nbtString = rawNbt.toString('binary');

    // Must be a player_head (the HDB head's material).
    expect(nbtString).toMatch(/player_head/i);

    // Must carry texture/profile data so the head renders with its skin.
    // HDB heads encode the skin via a PlayerProfile (custom_name "Melon",
    // name "HeadDatabase", and a textures property with a minecraft.net URL).
    expect(nbtString, 'serialized icon must preserve HDB texture data').toMatch(
      /textures|profile|HeadDatabase|Melon|skin_patch/i,
    );

    await asBot(`loc delete BASE ${locName}`);
  });

  test('Issue 3 GUI: HDB head icon renders with its texture in the location GUI', async () => {
    const locName = 'hdbA' + (Date.now() % 1000000);
    bot.chat(`/loc save BASE ${locName}`);
    await bot.nextMessage(/saved|Location/i, 5_000);

    await rcon(`clear ${BOT_NAME}`);
    await new Promise(r => setTimeout(r, 300));
    await rcon(`hdb give 1 1 ${BOT_NAME}`);
    await new Promise(r => setTimeout(r, 1000));

    bot.chat(`/loc icon BASE ${locName} hand`);
    await bot.nextMessage(/Icon.*changed|changed to|icon/i, 5_000).catch(() => {});
    await new Promise(r => setTimeout(r, 500));

    // Sanity: DB should have the icon.
    const db = getDb();
    const row = db.get<{ icon: string | null }>(
      "SELECT icon FROM locations WHERE tag = 'BASE' AND name = ?", locName,
    );
    db.close();
    console.log('DEBUG hdbA icon size:', row?.icon?.length, 'prefix:', row?.icon?.slice(0, 20));
    expect(row?.icon, 'icon must be set in DB before opening GUI').toBeTruthy();

    // Open the location GUI and confirm the location item is a player_head
    // (not the default LODESTONE fallback) AND that the raw slot carries a
    // profile/texture component (proof HDB head survived the display path).
    bot.chat('/loc gui');
    const win = await bot.nextWindow(10_000);
    const locItem = win.findSlot(s => s.name === locName);
    expect(locItem, `location ${locName} should be in GUI`).toBeDefined();
    expect(locItem!.item).toBe('minecraft:player_head');

    // Peek at raw mineflayer slot to verify texture/profile component is there.
    const rawWindow = (bot.raw as unknown as { currentWindow?: { slots?: unknown[] } }).currentWindow;
    const rawSlot = rawWindow?.slots?.[locItem!.index] as
      | { components?: Array<{ type?: string; data?: unknown }> }
      | null
      | undefined;
    const hasProfile = !!rawSlot?.components?.some(c =>
      c?.type === 'profile' || c?.type === 'minecraft:profile',
    );
    expect(hasProfile, `raw slot components: ${JSON.stringify(rawSlot?.components?.map(c => c?.type))}`)
      .toBe(true);

    await bot.closeWindow();
    await asBot(`loc delete BASE ${locName}`);
  });

  test('Issue 3 error: /loc icon hand with empty main hand rejects gracefully', async () => {
    bot.chat('/loc save BASE iconEmpty');
    await bot.nextMessage(/saved|Location/i, 5_000);

    // Explicitly clear main-hand: /clear alone leaves the held slot empty on 1.21.
    await rcon(`clear ${BOT_NAME}`);
    await rcon(`item replace entity ${BOT_NAME} weapon.mainhand with minecraft:air`);
    await new Promise(r => setTimeout(r, 500));

    bot.clearMessages();
    bot.chat('/loc icon BASE iconEmpty hand');
    try {
      const msg = await bot.nextMessage(/Hold an item|main hand/i, 5_000);
      expect(msg.text).toMatch(/Hold an item|main hand/i);
    } catch (err) {
      console.log(
        'DEBUG all messages received:',
        JSON.stringify(bot.messageHistory.map(m => m.text), null, 2).slice(0, 2000),
      );
      throw err;
    }

    bot.chat('/loc delete BASE iconEmpty');
    await bot.nextMessage(/deleted|removed/i, 5_000).catch(() => {});
  });

  test('Issue 3 map payload: slim-metadata head resolves to basemanager_head icon', async () => {
    const locName = 'hdbSlim' + (Date.now() % 1000000);
    const slimTextureValue =
      'ewogICJ0aW1lc3RhbXAiIDogMTc3NzI3MDc5MzQxNSwKICAicHJvZmlsZUlkIiA6ICI2NDBiMDc4YzcyOTE0MjA3OTk4M2Q4YjM4Y2NhZjU0OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJQbG9pbmtTYXVyIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2Q4YjQ2NjU4MTZmOWIyMDFlYzU4ZDM2ZmZmMGQyODE2ZTA4NjdjMzgyY2ViM2E4MDgxZTNjYmI0NTA4MTdiZTUiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==';

    const originalConfig = fs.readFileSync(BASEMANAGER_CONFIG_PATH, 'utf8');
    try {
      setPl3xmapPublicOnly(false);
      bot.chat('/loc reload');
      await new Promise(r => setTimeout(r, 1500));

      bot.chat(`/loc save TEST ${locName}`);
      await bot.nextMessage(/saved|Location/i, 8_000);

      await rcon(
        `item replace entity ${BOT_NAME} weapon.mainhand with minecraft:player_head[minecraft:profile={name:"PloinkSaur",properties:[{name:"textures",value:"${slimTextureValue}"}]}]`,
      );
      await new Promise(r => setTimeout(r, 700));

      bot.chat(`/loc icon TEST ${locName} hand`);
      await bot.nextMessage(/Icon.*changed|changed to|icon/i, 8_000).catch(() => {});
      await new Promise(r => setTimeout(r, 1000));

      await rcon('pl3xmap fullrender world');
      const image = await waitForMarkerImage(locName, 20000);
      expect(image, `marker image for ${locName} should exist`).toBeTruthy();
      expect(image, `marker image for ${locName} should be a head icon`).toMatch(/^basemanager_head_/);
      expect(image).not.toBe('basemanager_default');
    } finally {
      fs.writeFileSync(BASEMANAGER_CONFIG_PATH, originalConfig, 'utf8');
      bot.chat('/loc reload');
      await new Promise(r => setTimeout(r, 1500));
      bot.chat(`/loc delete TEST ${locName}`);
      await bot.nextMessage(/deleted|removed/i, 6_000).catch(() => {});
    }
  });

  test('Issue 4 parse: multi-word name is joined in the lookup', async () => {
    bot.clearMessages();
    // Non-existent space-name. Error must include the FULL joined name,
    // proving the parser joined args[2..length-2] rather than stopping at args[2].
    bot.chat('/loc share BASE Ghost House SomeFakePlayer');
    const msg = await bot.nextMessage(/Location not found/i, 5_000);
    expect(msg.text).toContain('Ghost House');
    // Old broken behavior would report "Location not found: BASE:Ghost".
    expect(msg.text).not.toMatch(/Location not found:\s*BASE:Ghost\s*$/);
  });

  test('Issue 4 regression: single-word names still parse correctly', async () => {
    bot.chat('/loc save BASE shareOne');
    await bot.nextMessage(/saved|Location/i, 5_000);

    bot.clearMessages();
    bot.chat('/loc share BASE shareOne NonexistentPlayer');
    const msg = await bot.nextMessage(/not found/i, 5_000);
    expect(msg.text).toMatch(/Player not found.*NonexistentPlayer/i);
    expect(msg.text).not.toMatch(/Location not found/i);

    bot.chat('/loc delete BASE shareOne');
    await bot.nextMessage(/deleted|removed/i, 5_000).catch(() => {});
  });
});
