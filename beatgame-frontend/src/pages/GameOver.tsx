import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { useGameStore } from '../store/useGameStore'
import './GameOver.css'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export default function GameOver() {
  const navigate = useNavigate()
  const store = useGameStore()
  const confettiRef = useRef<HTMLCanvasElement>(null)
  const intentionalNav = useRef(false)

  const gameOver = store.lastGameOver
  const winnerId = gameOver?.winnerPlayerId ?? null
  const iWon = winnerId === store.playerId
  const isTie = gameOver !== null && winnerId === null

  // Redirect if store was reset (e.g. page refresh on /game-over), but not when we're navigating on purpose
  useEffect(() => {
    if (!gameOver && !intentionalNav.current) navigate('/', { replace: true })
  }, [gameOver, navigate])

  // Confetti when local player won
  useEffect(() => {
    if (!iWon || !confettiRef.current) return
    const cvs = confettiRef.current
    const ctx = cvs.getContext('2d')!

    function resize() {
      cvs.width = window.innerWidth * devicePixelRatio
      cvs.height = window.innerHeight * devicePixelRatio
      cvs.style.width = `${window.innerWidth}px`
      cvs.style.height = `${window.innerHeight}px`
      ctx.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0)
    }
    resize()
    window.addEventListener('resize', resize)

    const COLORS = ['#f5c84a', '#f7d57a', '#b388ff', '#70a9ff', '#ffffff']
    const particles = Array.from({ length: 90 }, () => ({
      x: window.innerWidth / 2 + (Math.random() - 0.5) * 120,
      y: window.innerHeight * 0.22 + (Math.random() - 0.5) * 30,
      vx: (Math.random() - 0.5) * 6,
      vy: Math.random() * -8 - 3,
      g: 0.18 + Math.random() * 0.06,
      r: 2 + Math.random() * 3,
      rot: Math.random() * Math.PI,
      vr: (Math.random() - 0.5) * 0.2,
      c: COLORS[Math.floor(Math.random() * COLORS.length)],
      life: 1,
    }))

    const burstAt = performance.now()
    let rafId: number

    function tick() {
      rafId = requestAnimationFrame(tick)
      ctx.clearRect(0, 0, cvs.width, cvs.height)
      const elapsed = (performance.now() - burstAt) / 1000
      for (const p of particles) {
        p.vy += p.g; p.x += p.vx; p.y += p.vy; p.rot += p.vr
        if (elapsed > 2.2) p.life = Math.max(0, 1 - (elapsed - 2.2) / 2)
        ctx.save()
        ctx.translate(p.x, p.y)
        ctx.rotate(p.rot)
        ctx.globalAlpha = p.life
        ctx.fillStyle = p.c
        ctx.fillRect(-p.r, -p.r * 2, p.r * 2, p.r * 3.5)
        ctx.restore()
      }
      if (elapsed > 4.5) cancelAnimationFrame(rafId)
    }
    tick()

    return () => {
      cancelAnimationFrame(rafId)
      window.removeEventListener('resize', resize)
    }
  }, [iWon])

  if (!gameOver) return null

  const { scores } = gameOver
  const { playerId, playerName, isHost, lobbyPlayers, gameMode, roomCode } = store
  const totalRounds = store.currentRound?.totalRounds ?? '?'

  // Build playerId → name map
  const nameById: Record<string, string> = { [playerId]: playerName }
  if (gameMode === 'multi') {
    const opPlayer = lobbyPlayers.find(p => p.isHost !== isHost)
    const opId = Object.keys(scores).find(id => id !== playerId)
    if (opPlayer && opId) nameById[opId] = opPlayer.name
  }

  // Sorted scoreboard entries
  const entries = Object.entries(scores)
    .map(([id, score]) => ({ id, score, name: nameById[id] ?? 'Player' }))
    .sort((a, b) => b.score - a.score)

  function entryClass(id: string): string {
    if (isTie) return 'tied'
    return id === winnerId ? 'winner' : 'loser'
  }

  const topScore = entries[0]?.score ?? 0
  const secondScore = entries[1]?.score ?? 0
  const margin = topScore - secondScore
  const winnerName = winnerId ? (nameById[winnerId] ?? 'Player') : ''

  async function handlePlayAgain() {
    intentionalNav.current = true
    if (store.gameMode === 'solo') {
      const name = store.playerName
      store.reset()
      store.setPlayerName(name)
      navigate('/solo/config')
    } else {
      const { roomCode } = store
      // Either player may click first — reset is idempotent (sets WAITING + clears
      // redis regardless of current state), so whoever clicks first ensures the room
      // is ready before navigating instead of relying on the host having already done
      // it (issue #35: a guest who clicked first could be stranded outside the room).
      if (roomCode) {
        try {
          await fetch(`${API_BASE}/api/rooms/${roomCode}/reset`, { method: 'POST' })
        } catch (err) {
          console.error('[GameOver] resetRoom failed', err)
        }
      }
      store.resetForRematch()
      navigate(`/room/${roomCode}`)
    }
  }

  function handleNewGame() { intentionalNav.current = true; store.reset(); navigate('/') }

  const trophySvg = isTie ? (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M6 8h12" /><path d="M6 12h12" /><path d="M6 16h12" />
    </svg>
  ) : (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M6 4h12v4a6 6 0 0 1-12 0z" />
      <path d="M6 6H4a2 2 0 0 0 0 4h2" />
      <path d="M18 6h2a2 2 0 0 1 0 4h-2" />
      <path d="M10 14h4" /><path d="M12 14v4" /><path d="M8 20h8" />
    </svg>
  )

  return (
    <div
      className="min-h-dvh grid place-items-start justify-items-center overflow-x-hidden relative"
      style={{ padding: '16px' }}
    >
      <div className={`go-ambient${isTie ? ' tie' : ''}`} />
      <div className="grain" />
      <canvas className="go-confetti" ref={confettiRef} />

      <main className="go-stage">

        {/* Topbar */}
        <div className="go-topbar">
          <div>
            <span className={`go-dot${isTie ? ' tie' : ''}`} />
            Game over · {totalRounds} rounds
          </div>
          {roomCode && <div>Room {roomCode}</div>}
        </div>

        {/* Hero */}
        <section className="go-hero">
          <div className={`go-trophy${isTie ? ' tie' : ''}`}>
            {trophySvg}
          </div>

          <div className={`go-kicker${isTie ? ' tie' : ''}`}>
            {isTie
              ? <><b>Dead even</b> · nobody blinked</>
              : <><b>Winner</b> · +{margin.toLocaleString()} margin</>
            }
          </div>

          <h1 className={`go-headline${isTie ? ' tie' : ''}`}>
            {isTie
              ? <>It&apos;s a <span className="go-name">tie</span>!</>
              : <><span className="go-name">{winnerName}</span> wins!</>
            }
          </h1>

          <div className="go-subhead">
            <b>{topScore.toLocaleString()}</b> points{isTie ? ' each' : ''}
          </div>
        </section>

        {/* Scoreboard */}
        <section className="go-board">
          {entries.map((entry, i) => {
            const cls = entryClass(entry.id)
            const isMe = entry.id === playerId
            return (
              <div key={entry.id} className={`go-entry ${cls}`}>
                <div className="go-rank">#{i + 1}</div>
                <div className="go-who">
                  <div className="go-nm">
                    {cls === 'winner' && (
                      <svg className="go-crown" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M3 17l2.5-9 4.5 5 2-7 2 7 4.5-5L21 17H3zm0 2h18v2H3z" />
                      </svg>
                    )}
                    {entry.name}
                    {isMe && <span className="go-youbadge">You</span>}
                  </div>
                </div>
                <div className="go-score">
                  {entry.score.toLocaleString()}
                  <span className="go-dim">pts</span>
                </div>
              </div>
            )
          })}
        </section>

      </main>

      {/* Actions */}
      <div className="go-actions-bar">
        <div className="go-actions">
          <button className="go-btn go-btn-primary" onClick={handlePlayAgain}>
            <span>Play again</span>
            <svg className="go-arrow" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M5 12h14" /><path d="M13 6l6 6-6 6" />
            </svg>
          </button>
          <button className="go-btn go-btn-ghost" onClick={handleNewGame}>
            New game · back to home
          </button>
        </div>
      </div>

    </div>
  )
}
