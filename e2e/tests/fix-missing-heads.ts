/**
 * Reconnect a bot to the running server and fix any HDB waypoints whose
 * icon setting failed during the demo (invalid HDB ID etc).
 */
import { RconClient } from 'mc-e2e';
import mineflayer from 'mineflayer';

const BOT_NAME = 'FixBot';

const FIXES: Array<{ name: string; hdbId: number }> = [
  { name: 'HDB_CastleA', hdbId: 500 },   // retry with a safer ID
  { name: 'HDB_FoodA',   hdbId: 3000 },  // retry with a safer ID
];

async function main(): Promise<void> {
  const rcon = new RconClient({ host: 'localhost', port: 25575, password: 'e2e-test' });
  await rcon.connect();

  const bot = mineflayer.createBot({
    host: 'localhost',
    port: 25565,
    username: BOT_NAME,
    auth: 'offline',
    hideErrors: true,
  });

  await new Promise<void>((resolve, reject) => {
    bot.once('spawn', () => resolve());
    bot.once('error', reject);
    bot.once('end', reason => reject(new Error(`bot ended: ${reason}`)));
  });

  await rcon.send(`op ${BOT_NAME}`);
  await rcon.send(`gamemode creative ${BOT_NAME}`);
  await new Promise(r => setTimeout(r, 500));

  // Bot chat warmup so plugin responses route correctly.
  bot.chat('/loc save BASE __fixwarmup');
  await new Promise(r => setTimeout(r, 1500));
  bot.chat('/loc delete BASE __fixwarmup');
  await new Promise(r => setTimeout(r, 1000));

  for (const fix of FIXES) {
    console.log(`[fix] ${fix.name} -> HDB id=${fix.hdbId}`);
    await rcon.send(`clear ${BOT_NAME}`);
    await new Promise(r => setTimeout(r, 300));
    const resp = await rcon.send(`hdb give ${fix.hdbId} 1 ${BOT_NAME}`);
    console.log(`  hdb response: ${resp.trim()}`);
    await new Promise(r => setTimeout(r, 1200));

    bot.chat(`/loc icon HEAD ${fix.name} hand`);
    await new Promise(r => setTimeout(r, 1500));
  }

  // Re-render markers.
  await rcon.send('pl3xmap fullrender world').catch(() => {});

  console.log('[fix] Done. Disconnecting.');
  bot.quit();
  await rcon.disconnect();
  process.exit(0);
}

main().catch(err => {
  console.error('[fix] ERROR:', err);
  process.exit(1);
});
