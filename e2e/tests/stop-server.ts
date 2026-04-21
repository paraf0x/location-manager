import { RconClient } from 'mc-e2e';
async function main() {
  const r = new RconClient({ host: 'localhost', port: 25575, password: 'e2e-test' });
  await r.connect();
  console.log(await r.send('stop'));
  try { await r.disconnect(); } catch { /* socket may already be gone */ }
}
main().catch(() => process.exit(0));
