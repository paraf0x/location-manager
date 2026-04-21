import { RconClient } from 'mc-e2e';
async function main() {
  const r = new RconClient({ host: 'localhost', port: 25575, password: 'e2e-test' });
  await r.connect();
  // bukkit:reload confirm is deprecated but still works; it re-loads every
  // plugin, which forces BaseManager to re-read locations (with fresh
  // isPublic values) from the SQLite DB.
  console.log(await r.send('bukkit:reload confirm'));
  await new Promise(res => setTimeout(res, 5000));
  console.log(await r.send('pl3xmap fullrender world'));
  await r.disconnect();
}
main();
