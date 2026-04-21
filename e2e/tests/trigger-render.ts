import { RconClient } from 'mc-e2e';
async function main() {
  const r = new RconClient({ host: 'localhost', port: 25575, password: 'e2e-test' });
  await r.connect();
  console.log(await r.send('pl3xmap fullrender world'));
  await r.disconnect();
}
main();
