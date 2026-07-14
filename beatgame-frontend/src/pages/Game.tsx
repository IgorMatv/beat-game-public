import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Howl } from 'howler'
import { useGameStore } from '../store/useGameStore'
import { useWebSocket } from '../hooks/useWebSocket'
import './Game.css'
import RoundResultOverlay from './RoundResultOverlay'

const TOTAL_SECS = 15
const BAR_COUNT = 48
const LETTERS = ['A', 'B', 'C', 'D'] as const

function seededBars(trackId: number): number[] {
  const bars: number[] = []
  let n = trackId
  for (let i = 0; i < BAR_COUNT; i++) {
    n = (n * 1103515245 + 12345) & 0x7fffffff
    bars.push(22 + ((n % 1000) / 1000) * 14)
  }
  return bars
}

export default function Game() {
  const { code: roomCode } = useParams<{ code: string }>()
  const navigate = useNavigate()
  const store = useGameStore()
  const { connect, disconnect, sendAnswer, sendReady, sendStart, sendPause } = useWebSocket()

  const playerToken = store.playerToken
  const currentRound = store.currentRound
  const phase = store.phase
  const isHost = store.isHost
  const musicPaused = store.musicPaused

  // Scoreboard
  const myScore = store.scores[playerToken] ?? 0
  const opponentPlayer = store.gameMode === 'multi'
    ? store.lobbyPlayers.find(p => p.isHost !== isHost) ?? null
    : null
  const opponentToken = Object.keys(store.scores).find(t => t !== playerToken) ?? null
  const opponentScore = opponentToken ? (store.scores[opponentToken] ?? 0) : 0

  // Local answer selection
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null)
  const [myTimeMs, setMyTimeMs] = useState<number | null>(null)

  // Timer
  const [timeLeft, setTimeLeft] = useState(TOTAL_SECS)
  const roundStartTimeRef = useRef<number>(Date.now())

  // Audio
  const howlRef = useRef<Howl | null>(null)
  const [playedFraction, setPlayedFraction] = useState(0)

  // WebSocket
  useEffect(() => {
    if (!roomCode || !playerToken) return
    const onConnected = store.gameMode === 'solo'
      ? () => sendStart({
          roomCode,
          rounds: store.gameConfig.rounds,
          category: store.gameConfig.category,
          categoryType: store.gameConfig.categoryType,
        })
      : undefined
    connect(roomCode, playerToken, onConnected)
    return () => { disconnect() }
  }, [roomCode, playerToken, connect, disconnect])

  // Audio: play new track when round changes
  useEffect(() => {
    if (!currentRound?.previewUrl) {
      setPlayedFraction(0)
      return
    }
    howlRef.current?.stop()
    howlRef.current?.unload()
    setPlayedFraction(0)

    const h = new Howl({ src: [currentRound.previewUrl], html5: true })
    howlRef.current = h
    h.play()

    const id = setInterval(() => {
      const pos = (h.seek() as number) || 0
      setPlayedFraction(Math.min(pos / 30, 1))
    }, 200)

    return () => {
      clearInterval(id)
      h.stop()
      h.unload()
    }
  }, [currentRound?.trackId])

  // Timer: count down during playing phase
  useEffect(() => {
    if (phase !== 'playing') return
    roundStartTimeRef.current = Date.now()
    setTimeLeft(TOTAL_SECS)
    const id = setInterval(() => {
      setTimeLeft(t => (t <= 1 ? (clearInterval(id), 0) : t - 1))
    }, 1000)
    return () => clearInterval(id)
  }, [phase, currentRound?.roundNumber])

  // Reset selection when new round starts — useLayoutEffect prevents the one-frame flash
  useLayoutEffect(() => {
    if (phase === 'playing') {
      setSelectedIndex(null)
      setMyTimeMs(null)
    }
  }, [phase, currentRound?.roundNumber])

  // Show answer colors for 2 s before revealing the score overlay
  useEffect(() => {
    if (phase !== 'answer_reveal') return
    const id = setTimeout(() => store.setPhase('round_result'), 2000)
    return () => clearTimeout(id)
  }, [phase])

  // Sync Howler pause/resume with musicPaused store value
  useEffect(() => {
    const h = howlRef.current
    if (!h) return
    if (musicPaused) {
      h.pause()
    } else if (h.state() === 'loaded') {
      h.play()
    }
  }, [musicPaused])

  // Game over — Phase 8 will handle this properly
  useEffect(() => {
    if (phase === 'game_over') navigate('/game-over', { replace: true })
  }, [phase, navigate])

  function handleAnswer(index: number) {
    if (store.hasAnswered || phase !== 'playing' || !currentRound || !roomCode) return
    setSelectedIndex(index)
    const timeMs = Date.now() - roundStartTimeRef.current
    setMyTimeMs(timeMs)
    sendAnswer({ roomCode, trackId: currentRound.trackId, answerIndex: index, timeMs })
    store.setHasAnswered(true)
  }

  const inReveal = phase === 'answer_reveal' || phase === 'round_result'

  // Determine correct answer index after round result
  const correctIndex: number | null = (() => {
    if (!(inReveal && currentRound && store.lastRoundResult)) return null
    const idx = currentRound.options.findIndex(o => o === store.lastRoundResult!.correctAnswer)
    if (idx === -1) {
      console.error('[Game] correctAnswer not found in options', store.lastRoundResult.correctAnswer, currentRound.options)
      return null
    }
    return idx
  })()

  // Waveform bar heights (deterministic per track)
  const barHeights = currentRound
    ? seededBars(currentRound.trackId)
    : Array.from({ length: BAR_COUNT }, () => 24)

  // Status dots
  const myDotClass = store.hasAnswered ? 'answered' : phase === 'playing' ? 'thinking' : ''
  const opDotClass = inReveal ? 'answered' : phase === 'playing' ? 'thinking' : ''

  // Timer SVG
  const CIRC = 2 * Math.PI * 44
  const dashOffset = ((1 - timeLeft / TOTAL_SECS) * CIRC).toFixed(2)
  const urgent = timeLeft <= 3 && phase === 'playing'

  // Cover reveal
  const revealed = inReveal

  // Audio elapsed for display
  const elapsed = Math.round(playedFraction * 30)
  const elapsedStr = `0:${String(elapsed).padStart(2, '0')}`

  if (!currentRound) {
    return (
      <div className="min-h-dvh grid place-items-center" style={{ color: 'var(--fg)' }}>
        Connecting…
      </div>
    )
  }

  return (
    <div
      className="min-h-dvh grid place-items-start justify-items-center overflow-x-hidden relative"
      style={{ padding: '16px' }}
    >
      <div className="gm-ambient" />
      <div className="grain" />

      <main className="gm-stage">
        {/* Topbar */}
        <div className="gm-topbar">
          <div className="gm-round-pill">
            Round <b>{currentRound.roundNumber}</b> / {currentRound.totalRounds}
            <span className="gm-dots" aria-hidden="true">
              {Array.from({ length: currentRound.totalRounds }, (_, i) => (
                <i
                  key={i}
                  className={
                    i < currentRound.roundNumber - 1 ? 'done' :
                    i === currentRound.roundNumber - 1 ? 'current' : ''
                  }
                />
              ))}
            </span>
          </div>
          <button className="gm-leave" onClick={() => { store.reset(); navigate('/') }}>
            Leave
          </button>
        </div>

        {/* Scoreboard */}
        <div className="gm-scoreboard">
          <div className="gm-player">
            <div className="gm-name-row">
              <span className={`gm-dot${myDotClass ? ` ${myDotClass}` : ''}`} />
              <span className="gm-name">You</span>
            </div>
            <div className="gm-score">{myScore.toLocaleString()}</div>
          </div>
          <div className="gm-vs">VS</div>
          <div className="gm-player right">
            <div className="gm-name-row right">
              <span className="gm-name">{opponentPlayer?.name ?? '—'}</span>
              {opponentPlayer && (
                <span className={`gm-dot${opDotClass ? ` ${opDotClass}` : ''}`} />
              )}
            </div>
            <div className="gm-score">{opponentPlayer ? opponentScore.toLocaleString() : '—'}</div>
          </div>
        </div>

        {/* Audio */}
        <div className="gm-audio">
          <div className="gm-audio-head">
            <div className="gm-live">
              <span className="gm-pulse" />
              Now playing
            </div>
            <div>{elapsedStr} / 0:30</div>
          </div>

          <div className={`gm-cover-wrap${revealed ? ' revealed' : ''}`} aria-label="Album cover">
            <div className="gm-cover-q" aria-hidden="true">?</div>
          </div>

          <div className="gm-progress">
            <div className="gm-wave-bars" aria-hidden="true">
              {barHeights.map((h, i) => {
                const p = i / BAR_COUNT
                const isPlayed = p < playedFraction
                const isCursor = !isPlayed && p < playedFraction + 0.025
                return (
                  <span
                    key={i}
                    style={{
                      height: `${h}px`,
                      background: isPlayed
                        ? 'var(--accent)'
                        : isCursor
                        ? 'var(--accent-2)'
                        : 'var(--surface-2)',
                      opacity: isPlayed ? 1 : 0.8,
                    }}
                  />
                )
              })}
            </div>
            <div className="gm-play-row">
              <div className="gm-play-indicator">
                <span className="gm-eq" aria-hidden="true"><i /><i /><i /></span>
                30-sec preview
              </div>
              <div className="gm-time">
                <b>{elapsedStr}</b> / 0:30
              </div>
            </div>
          </div>
        </div>

        {/* Timer */}
        <div className="gm-timer-row">
          <div className={`gm-timer${urgent ? ' urgent' : ''}`}>
            <svg viewBox="0 0 100 100">
              <circle className="gm-track" cx="50" cy="50" r="44" fill="none" strokeWidth="6" />
              <circle
                className="gm-fill"
                cx="50" cy="50" r="44" fill="none" strokeWidth="6"
                strokeDasharray={CIRC.toFixed(2)}
                strokeDashoffset={dashOffset}
              />
            </svg>
            <div className="gm-num">
              <div>
                <span>{timeLeft}</span>
                <small>seconds</small>
              </div>
            </div>
          </div>
        </div>

        <div className="gm-prompt"><span>Pick the right</span> title — artist</div>

        {/* Answers */}
        <div className="gm-answers">
          {currentRound.options.map((opt, i) => {
            const parts = opt.split(' — ')
            const title = parts[0] ?? opt
            const artist = parts[1] ?? ''
            const isSelected = selectedIndex === i && !inReveal
            const isCorrect = inReveal && correctIndex === i
            const isWrong = inReveal && selectedIndex === i && correctIndex !== i
            const isDim = inReveal && !isCorrect && !isWrong
            return (
              <button
                key={i}
                className={[
                  'gm-answer',
                  isSelected ? 'selected' : '',
                  isCorrect ? 'correct' : '',
                  isWrong ? 'wrong' : '',
                  isDim ? 'dim' : '',
                ].filter(Boolean).join(' ')}
                onClick={() => handleAnswer(i)}
                disabled={store.hasAnswered || phase !== 'playing'}
              >
                <span className="gm-letter">{LETTERS[i]}</span>
                <span className="gm-title">{title}</span>
                <span className="gm-artist">{artist}</span>
              </button>
            )
          })}
        </div>
      </main>

      {phase === 'round_result' && store.lastRoundResult && currentRound && roomCode && (
        <RoundResultOverlay
          roomCode={roomCode}
          myTimeMs={myTimeMs}
          onReady={() => sendReady(roomCode)}
          onPauseToggle={() => sendPause({ roomCode, paused: !store.musicPaused })}
        />
      )}
    </div>
  )
}
