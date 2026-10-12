// Test player: joins, chats, runs commands on request (stdin), reports events on stdout.
const mineflayer = require('mineflayer')
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: process.argv[2] || 'Steve', version: process.argv[3] || false })
const log = (...a) => console.log(...a)
bot.once('spawn', () => { log('SPAWNED'); setTimeout(() => bot.chat('hello from bot'), 1000) })
bot.on('messagestr', (m) => log('MSG ' + m))
bot.on('kicked', (r) => { log('KICKED ' + JSON.stringify(r)); process.exit(0) })
bot.on('error', (e) => log('ERROR ' + e.message))
bot.on('game', () => log('GAMEMODE ' + bot.game.gameMode))
process.stdin.on('data', (d) => {
  const line = d.toString().trim()
  if (line === 'inv') log('INV ' + bot.inventory.items().map(i => i.name + ':' + (i.customName || '')).join(','))
  else if (line.startsWith('/')) bot.chat(line)
})
