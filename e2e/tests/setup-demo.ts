/**
 * Demo setup script: starts the Purpur test server, connects a bot, creates
 * waypoints with every supported icon type, disconnects the bot, and LEAVES
 * THE SERVER RUNNING so you can inspect Pl3xmap at http://localhost:8080.
 *
 * Run:  cd e2e && npx tsx tests/setup-demo.ts
 *
 * The process will print the Pl3xmap URL and then detach (the server keeps
 * running in a child process until you /stop it manually).
 */
import { McServer } from 'mc-e2e';

const TEST_SERVER_DIR = process.env.TEST_SERVER_DIR ?? '/Users/maksymvasyukov/purpur-test';
const BOT_NAME = 'DemoBot';

/** HDB heads to set as icons. IDs are stable in the Arcaniax database. */
const HDB_HEADS: Array<{ id: number; name: string }> = [
  { id: 1,     name: 'Melon' },        // green plant head
  { id: 50,    name: 'SkeletonX' },    // character head
  { id: 100,   name: 'SmileyA' },      // decorative
  { id: 2000,  name: 'CastleA' },      // architectural
  { id: 5000,  name: 'FoodA' },        // food head
  { id: 10000, name: 'MonsterA' },     // monster head
];

/** Vanilla material icons — one per general category Pl3xmap renders. */
const MATERIAL_ICONS: Array<{ tag: string; name: string; material: string }> = [
  { tag: 'BASE',    name: 'DiamondBase',    material: 'diamond_block' },
  { tag: 'BASE',    name: 'NetheriteVault', material: 'netherite_block' },
  { tag: 'BASE',    name: 'BeaconHub',      material: 'beacon' },
  { tag: 'FARM',    name: 'WheatFarm',      material: 'wheat' },
  { tag: 'FARM',    name: 'MelonFarm',      material: 'melon' },
  { tag: 'MINE',    name: 'IronMine',       material: 'iron_ore' },
  { tag: 'MINE',    name: 'AncientDebris',  material: 'ancient_debris' },
  { tag: 'PORTAL',  name: 'NetherPortal',   material: 'obsidian' },
  { tag: 'PORTAL',  name: 'EndPortal',      material: 'end_portal_frame' },
  { tag: 'SHOP',    name: 'GeneralStore',   material: 'chest' },
  { tag: 'SHOP',    name: 'Enchanter',      material: 'enchanting_table' },
  { tag: 'CAMP',    name: 'Campfire',       material: 'campfire' },
  { tag: 'CAMP',    name: 'SignPost',       material: 'oak_sign' },
];

/** Banner icons — Pl3xmap renders banners as tall markers with the banner design. */
const BANNER_ICONS: Array<{ tag: string; name: string; material: string }> = [
  { tag: 'TERR', name: 'RedTerritory',  material: 'red_banner' },
  { tag: 'TERR', name: 'BlueTerritory', material: 'blue_banner' },
  { tag: 'TERR', name: 'WhiteCastle',   material: 'white_banner' },
];

/**
 * Spread waypoints in a spiral around spawn so they don't overlap on the map.
 * Returns a teleport command to run BEFORE saving each waypoint so the
 * location actually records coordinates at the intended spot.
 */
function spiralCoord(i: number): [number, number, number] {
  // Simple ring layout: 8 points per ring, 100 blocks radius per ring.
  const ring = Math.floor(i / 8) + 1;
  const angle = (i % 8) * (Math.PI / 4);
  const radius = ring * 100;
  return [Math.round(Math.cos(angle) * radius), 90, Math.round(Math.sin(angle) * radius)];
}

async function wait(ms: number): Promise<void> {
  return new Promise(r => setTimeout(r, ms));
}

async function main(): Promise<void> {
  const server = new McServer({
    dir: TEST_SERVER_DIR,
    jar: 'purpur.jar',
    jvmArgs: ['-Xms512M', '-Xmx2G'],
    port: 25565,
    rcon: { port: 25575, password: 'e2e-test' },
    patchProperties: true,
    acceptEula: true,
    inheritStdio: false,
  });

  console.log('[demo] Starting Purpur server...');
  await server.start();
  console.log('[demo] Server ready.');

  const rc = await server.rcon();
  const rcon = async (cmd: string): Promise<string> => rc.send(cmd);

  const bot = await server.createBot(BOT_NAME);
  await rcon(`op ${BOT_NAME}`);
  await rcon(`gamemode creative ${BOT_NAME}`);
  await wait(1000);

  // Warmup so bot.chat gets a response reliably.
  bot.chat('/loc save BASE __warmup');
  await bot.nextMessage(/saved|Location/i, 10_000).catch(() => {});
  bot.chat('/loc delete BASE __warmup');
  await bot.nextMessage(/deleted|removed/i, 5_000).catch(() => {});

  let index = 0;

  const saveAt = async (tag: string, name: string): Promise<void> => {
    const [x, y, z] = spiralCoord(index++);
    await rcon(`tp ${BOT_NAME} ${x} ${y} ${z}`);
    await wait(400);
    bot.chat(`/loc save ${tag} ${name}`);
    await bot.nextMessage(/saved|updated|Location/i, 5_000).catch(() => {});
  };

  const setMaterialIcon = async (tag: string, name: string, material: string): Promise<void> => {
    bot.chat(`/loc icon ${tag} ${name} ${material}`);
    await bot.nextMessage(/Icon.*changed|changed to/i, 5_000).catch(() => {});
  };

  const setHdbIcon = async (tag: string, name: string, hdbId: number): Promise<void> => {
    await rcon(`clear ${BOT_NAME}`);
    await wait(300);
    await rcon(`hdb give ${hdbId} 1 ${BOT_NAME}`);
    await wait(1500);
    bot.chat(`/loc icon ${tag} ${name} hand`);
    await bot.nextMessage(/Icon.*changed|changed to/i, 5_000).catch(() => {});
  };

  console.log('[demo] Creating material-icon waypoints...');
  for (const m of MATERIAL_ICONS) {
    await saveAt(m.tag, m.name);
    await setMaterialIcon(m.tag, m.name, m.material);
    console.log(`  [${m.tag}] ${m.name} -> ${m.material}`);
  }

  console.log('[demo] Creating banner-icon waypoints...');
  for (const b of BANNER_ICONS) {
    await saveAt(b.tag, b.name);
    await setMaterialIcon(b.tag, b.name, b.material);
    console.log(`  [${b.tag}] ${b.name} -> ${b.material}`);
  }

  console.log('[demo] Creating HDB head waypoints...');
  for (const h of HDB_HEADS) {
    const name = `HDB_${h.name}`;
    await saveAt('HEAD', name);
    await setHdbIcon('HEAD', name, h.id);
    console.log(`  [HEAD] ${name} -> HDB id=${h.id} (${h.name})`);
  }

  // Also: a plain (untextured) vanilla player_head to show the difference.
  await saveAt('HEAD', 'PlainHead');
  await setMaterialIcon('HEAD', 'PlainHead', 'player_head');
  console.log('  [HEAD] PlainHead -> minecraft:player_head (no texture)');

  // And the LODESTONE fallback (no icon set).
  await saveAt('BASE', 'NoIcon');
  console.log('  [BASE] NoIcon -> (no icon, LODESTONE default)');

  console.log('[demo] Mark Pl3xmap for a full render of the new markers.');
  await rcon('pl3xmap fullrender world').catch(() => {});

  console.log('[demo] Disconnecting bot; server keeps running.');
  await bot.disconnect();

  console.log('\n================================================');
  console.log('  Server is running.');
  console.log('  Pl3xmap URL: http://localhost:8080');
  console.log('  To stop the server: open a terminal and run:');
  console.log('    mcrcon-like: (connect to RCON port 25575 / password "e2e-test") send "stop"');
  console.log('    OR kill the server java process:');
  console.log(`    pkill -f 'purpur.jar'`);
  console.log('================================================\n');

  // Detach: unref the child process so this script can exit without killing the server.
  const proc = server.process;
  if (proc) {
    proc.unref();
    proc.stdout?.unref();
    proc.stderr?.unref();
    proc.stdin?.unref();
  }
  // Give everything a moment to flush, then exit without calling server.stop().
  await wait(500);
  process.exit(0);
}

main().catch(err => {
  console.error('[demo] ERROR:', err);
  process.exit(1);
});
