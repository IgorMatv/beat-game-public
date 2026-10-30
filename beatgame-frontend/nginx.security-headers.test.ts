import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

// Regression check for issue #71: the /ws location silently lacked the
// shared security-headers include that every other HTTPS location has.
// nginx's add_header is not inherited by a location that declares its own
// add_header, so each location must include the shared file explicitly —
// nothing catches a missing include at `nginx -t` time, since it's valid
// config either way. This test does.
describe('nginx.prod.conf security headers', () => {
  const conf = readFileSync(resolve(process.cwd(), 'nginx.prod.conf'), 'utf-8')

  const httpsServerMatch = conf.match(/listen 443 ssl;[\s\S]*/)
  if (!httpsServerMatch) throw new Error('could not find the HTTPS server block in nginx.prod.conf')
  const httpsServer = httpsServerMatch[0]

  const locationBlocks = httpsServer
    .split(/(?=\n\s*location )/)
    .filter(block => /^\s*\n?\s*location /.test(block) || block.trimStart().startsWith('location '))

  it('finds at least the locations this config is known to declare', () => {
    // Guards the test itself against a parsing regression (e.g. a reformatted
    // file silently producing zero location matches, which would make every
    // check below vacuously pass).
    expect(locationBlocks.length).toBeGreaterThanOrEqual(5)
  })

  it.each(locationBlocks.map(block => [block.match(/location\s+([^\s{]+)/)?.[1] ?? '?', block] as const))(
    'location %s includes the shared security-headers config',
    (_name, block) => {
      expect(block).toContain('include /etc/nginx/conf.d/security-headers.conf;')
    }
  )
})
