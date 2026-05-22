import { McServer, SqliteClient } from 'mc-e2e';

const TEST_SERVER_DIR = process.env.TEST_SERVER_DIR ?? '/Users/maksymvasyukov/purpur-test';
const JAR_NAME = 'purpur.jar';

let server: McServer | null = null;
let refCount = 0;

export async function acquireServer(): Promise<McServer> {
  refCount++;
  if (server) return server;

  server = new McServer({
    dir: TEST_SERVER_DIR,
    jar: JAR_NAME,
    jvmArgs: ['-Xms512M', '-Xmx2G'],
    port: 25565,
    rcon: { port: 25575, password: 'e2e-test' },
    patchProperties: true,
    acceptEula: true,
    inheritStdio: true,
  });

  // mineflayer/minecraft-protocol has no data for MC 26.1.x yet, so a bot
  // cannot speak the 26.1.2 protocol natively. ViaVersion + ViaBackwards on
  // the server translate protocols, so the bot connects as 1.21.9 and Via
  // bridges it to the real 26.1.2 server. Pin every bot to that version.
  const e2eBotVersion = process.env.E2E_BOT_VERSION ?? '1.21.9';
  const createBotOrig = server.createBot.bind(server);
  server.createBot = (username, options) =>
    createBotOrig(username, { version: e2eBotVersion, ...options });

  console.log('[e2e] Starting Minecraft server...');
  await server.start();
  console.log('[e2e] Server is ready.');
  return server;
}

export async function releaseServer(): Promise<void> {
  refCount--;
  if (refCount <= 0 && server) {
    console.log('[e2e] Stopping server...');
    await server.stop();
    server = null;
    console.log('[e2e] Server stopped.');
  }
}

export function getServer(): McServer {
  if (!server) throw new Error('Server not started. Call acquireServer() in beforeAll.');
  return server;
}

export function getDb(relativePath = 'plugins/BaseManager/storage.db'): SqliteClient {
  return getServer().sqlite(relativePath);
}
