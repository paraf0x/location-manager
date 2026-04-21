import Database from 'better-sqlite3';
import { gunzipSync } from 'node:zlib';

const db = new Database('/Users/maksymvasyukov/purpur-test/plugins/BaseManager/storage.db', { readonly: true });
const rows = db.prepare("SELECT name, tag, icon FROM locations WHERE name IN ('Base','Beacoland','HOLE','GeneralStore','PlainHead','NoIcon')").all() as Array<{ name: string; tag: string; icon: string | null }>;
for (const row of rows) {
  console.log(`\n[${row.tag}] ${row.name}`);
  if (!row.icon) { console.log('  (no icon set)'); continue; }
  console.log(`  length: ${row.icon.length} chars (base64)`);
  const bytes = Buffer.from(row.icon, 'base64');
  console.log(`  first hex: ${bytes.slice(0, 8).toString('hex')}`);
  const isGzip = bytes[0] === 0x1f && bytes[1] === 0x8b;
  let raw = bytes;
  if (isGzip) { try { raw = gunzipSync(bytes); } catch { /* noop */ } }
  const text = raw.toString('utf8').replace(/[\x00-\x1f]/g, '.');
  console.log(`  decoded: ${text.slice(0, 200)}`);
}
db.close();
