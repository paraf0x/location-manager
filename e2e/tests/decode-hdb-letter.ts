import Database from 'better-sqlite3';
import { gunzipSync } from 'node:zlib';

const db = new Database('/Users/maksymvasyukov/purpur-test/plugins/BaseManager/storage.db', { readonly: true });
const row = db.prepare("SELECT name, tag, icon FROM locations WHERE tag = 'TEST' ORDER BY id DESC LIMIT 1").get() as { name: string; tag: string; icon: string | null } | undefined;
if (!row?.icon) { console.log('no row or icon'); process.exit(0); }
console.log(`[${row.tag}] ${row.name}  (${row.icon.length} chars base64)`);
const bytes = Buffer.from(row.icon, 'base64');
console.log(`first hex: ${bytes.slice(0, 4).toString('hex')}  size: ${bytes.length}B`);
const isGzip = bytes[0] === 0x1f && bytes[1] === 0x8b;
const raw = isGzip ? gunzipSync(bytes) : bytes;
console.log(`decompressed: ${raw.length}B`);
const asText = raw.toString('utf8').replace(/[\x00-\x1f]/g, '.');
console.log('\nDecoded NBT:\n' + asText);
db.close();
