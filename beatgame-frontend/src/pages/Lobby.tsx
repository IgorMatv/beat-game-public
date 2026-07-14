import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Howler } from 'howler'
import { useGameStore } from '../store/useGameStore'
import { useWebSocket } from '../hooks/useWebSocket'
import type { CategoryType, LobbyPlayer } from '../types'
import './Lobby.css'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

interface ChipOption { value: string; label: string }

const GENRE_OPTIONS: ChipOption[] = [
  { value: 'POP', label: 'Pop' }, { value: 'ROCK', label: 'Rock' },
  { value: 'HIP_HOP', label: 'Hip-Hop' }, { value: 'ELECTRONIC', label: 'Electronic' },
  { value: 'RNB', label: 'R&B' }, { value: 'JAZZ', label: 'Jazz' },
  { value: 'CLASSICAL', label: 'Classical' }, { value: 'METAL', label: 'Metal' },
  { value: 'COUNTRY', label: 'Country' }, { value: 'LATIN', label: 'Latin' },
  { value: 'UKRAINIAN', label: '🇺🇦 Ukrainian' },
]
const DECADE_OPTIONS: ChipOption[] = [
  { value: '1980', label: '80s' }, { value: '1990', label: '90s' },
  { value: '2000', label: '2000s' }, { value: '2010', label: '2010s' },
  { value: '2020', label: '2020s' },
]
const OPTIONS: Record<CategoryType, ChipOption[]> = {
  GENRE: GENRE_OPTIONS,
  DECADE: DECADE_OPTIONS,
}
const OPTS_LABEL: Record<CategoryType, string> = {
  GENRE: 'Pick a genre', DECADE: 'Pick a decade',
}
const DEFAULT_PICK: Record<CategoryType, string> = {
  GENRE: 'POP', DECADE: '2010',
}
const ROUNDS_META: Record<number, string> = { 5: '~3 min', 10: '~5 min', 15: '~8 min' }

export default function Lobby() {
  const { code: roomCode } = useParams<{ code: string }>()
  const navigate = useNavigate()
  const store = useGameStore()
  const { connect, disconnect, sendStart, sendConfig } = useWebSocket()

  const isHost = store.isHost
  const playerToken = store.playerToken
  const lobbyPlayers = store.lobbyPlayers

  // Derive host and guest from lobbyPlayers; fall back to local store info for host
  const hostPlayer: LobbyPlayer | null =
    lobbyPlayers.find(p => p.isHost) ??
    (isHost ? { id: 0, name: store.playerName, isHost: true } : null)
  const guestPlayer: LobbyPlayer | null =
    lobbyPlayers.find(p => !p.isHost) ?? null
  const bothJoined = hostPlayer !== null && guestPlayer !== null

  // Settings state — local, synced to store
  const [categoryType, setCategoryType] = useState<CategoryType>(store.gameConfig.categoryType)
  const [category, setCategory] = useState(store.gameConfig.category)
  const [rounds, setRounds] = useState(store.gameConfig.rounds)

  // Toast
  const [copied, setCopied] = useState(false)

  // Navigate to game when round starts
  useEffect(() => {
    if (store.phase === 'playing') {
      navigate(`/game/${roomCode}`, { replace: true })
    }
  }, [store.phase, roomCode, navigate])

  // Fetch initial players list (covers returning from game-over where WS was disconnected)
  useEffect(() => {
    if (!roomCode) return
    fetch(`${API_BASE}/api/rooms/${roomCode}/players`)
      .then(r => r.ok ? r.json() : [])
      .then((players: LobbyPlayer[]) => store.applyRoomState({ players, status: 'WAITING' }))
      .catch(() => {})
  }, [roomCode])

  // WebSocket connect / disconnect
  useEffect(() => {
    if (roomCode && playerToken) {
      connect(roomCode, playerToken)
    }
    return () => { disconnect() }
  }, [roomCode, playerToken, connect, disconnect])

  // Silent audio unlock on first interaction
  useEffect(() => {
    function unlock() {
      Howler.ctx?.resume()
    }
    window.addEventListener('click', unlock, { once: true })
    window.addEventListener('touchstart', unlock, { once: true })
    return () => {
      window.removeEventListener('click', unlock)
      window.removeEventListener('touchstart', unlock)
    }
  }, [])

  // Sync store config → local UI state for guest (host drives via sendConfig)
  useEffect(() => {
    if (isHost) return
    const { rounds: r, category: cat, categoryType: ct } = store.gameConfig
    setRounds(r)
    setCategory(cat)
    setCategoryType(ct as CategoryType)
  }, [store.gameConfig, isHost])

  function handleTabChange(ct: CategoryType) {
    const pick = DEFAULT_PICK[ct]
    setCategoryType(ct)
    setCategory(pick)
    store.updateGameConfig({ categoryType: ct, category: pick })
    if (roomCode && isHost) sendConfig({ roomCode, rounds, category: pick, categoryType: ct })
  }

  function handleChipChange(c: string) {
    setCategory(c)
    store.updateGameConfig({ category: c })
    if (roomCode && isHost) sendConfig({ roomCode, rounds, category: c, categoryType })
  }

  function handleRoundsChange(r: number) {
    setRounds(r)
    store.updateGameConfig({ rounds: r })
    if (roomCode && isHost) sendConfig({ roomCode, rounds: r, category, categoryType })
  }

  async function handleCopy() {
    const text = roomCode ?? ''
    let ok = false
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(text)
        ok = true
      }
    } catch { /* fall through */ }

    if (!ok) {
      // execCommand fallback — iOS needs font-size ≥ 16px (prevents zoom) + setSelectionRange
      const el = document.createElement('textarea')
      el.value = text
      el.style.cssText = 'position:fixed;top:0;left:0;width:2em;height:2em;font-size:16px;opacity:0;border:none;outline:none;'
      el.readOnly = true
      document.body.appendChild(el)
      el.focus()
      el.select()
      el.setSelectionRange(0, el.value.length)
      ok = document.execCommand('copy')
      document.body.removeChild(el)
    }

    if (ok) {
      setCopied(true)
      setTimeout(() => setCopied(false), 1400)
    }
  }

  async function handleShare() {
    if (navigator.share) {
      try {
        await navigator.share({
          title: 'BeatGame',
          text: `Join my room: ${roomCode}`,
          url: window.location.href,
        })
        return
      } catch { /* user cancelled or API failed */ }
    }
    handleCopy()
  }

  function handleStart() {
    if (!roomCode) return
    sendStart({ roomCode, rounds, category, categoryType })
  }

  // Start bar label / state
  let startLabel = 'Start game'
  let startDisabled = false
  if (isHost) {
    if (!bothJoined) { startLabel = 'Waiting for player…'; startDisabled = true }
  } else {
    startLabel = 'Waiting for host to start…'
    startDisabled = true
  }

  return (
    <div className="min-h-dvh grid place-items-start justify-items-center p-4 overflow-x-hidden relative">
      <div className="lb-ambient" />
      <div className="grain" />

      <main className="lb-stage">
        {/* Top bar */}
        <div className="lb-topbar">
          <div className="lb-crumb">
            <span className="dot" />
            Lobby
          </div>
          <Link to="/" className="lb-leave" onClick={() => store.reset()}>
            Cancel
          </Link>
        </div>

        {/* Room code card */}
        <section className="lb-code-card">
          <div className="lb-code-label">Room code — share to invite</div>
          <div className="lb-code-row">
            <div className="lb-code">{roomCode}</div>
            <button className={`lb-copy${copied ? ' copied' : ''}`} onClick={handleCopy} title="Copy code">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <rect x="9" y="9" width="11" height="11" rx="2"/>
                <path d="M5 15V5a2 2 0 0 1 2-2h10"/>
              </svg>
            </button>
            {'share' in navigator && (
              <button className="lb-share" onClick={handleShare} title="Share link">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M12 3v12"/><path d="M7 8l5-5 5 5"/>
                  <path d="M5 15v4a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-4"/>
                </svg>
              </button>
            )}
          </div>
          <div className="lb-code-hint"><b>6 characters</b> · share with your opponent</div>
        </section>

        {/* Player slots */}
        <section className="lb-players">
          {/* Host slot */}
          <div className={`lb-slot${hostPlayer ? ' filled' : ' empty'}`}>
            <div className="lb-avatar">
              {hostPlayer ? hostPlayer.name[0].toUpperCase() : '?'}
            </div>
            <div className="lb-name">{hostPlayer?.name ?? 'Waiting…'}</div>
            <div className="lb-role">
              {isHost && <span className="lb-badge">You</span>}
              Host
            </div>
          </div>

          <div className="lb-vs">VS</div>

          {/* Guest slot */}
          <div className={`lb-slot${guestPlayer ? ' filled' : ' empty'}`}>
            <div className="lb-avatar">
              {guestPlayer ? guestPlayer.name[0].toUpperCase() : '?'}
            </div>
            <div className="lb-name">
              {guestPlayer ? guestPlayer.name : 'Waiting for player'}
            </div>
            <div className="lb-role">
              {!isHost && guestPlayer && <span className="lb-badge">You</span>}
              {guestPlayer ? 'Guest' : 'Room open'}
            </div>
          </div>
        </section>

        {/* Settings */}
        <div className={`lb-settings${!isHost ? ' locked' : ''}`}>
          {/* Category */}
          <div className="lb-section">
            <div className="lb-section-head">
              <span>Category</span>
              {!isHost && (
                <span className="lb-lock">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="4" y="11" width="16" height="10" rx="2"/>
                    <path d="M8 11V7a4 4 0 0 1 8 0v4"/>
                  </svg>
                  Host picks
                </span>
              )}
            </div>
            <div className="lb-tabs">
              {(['GENRE', 'DECADE'] as CategoryType[]).map(ct => (
                <button
                  key={ct}
                  className={categoryType === ct ? 'on' : ''}
                  onClick={() => handleTabChange(ct)}
                >
                  {ct.charAt(0) + ct.slice(1).toLowerCase()}
                </button>
              ))}
            </div>
          </div>

          {/* Options chip grid */}
          <div className="lb-section">
            <div className="lb-section-head">
              <span>{OPTS_LABEL[categoryType]}</span>
            </div>
            <div className="lb-chips">
              {OPTIONS[categoryType].map(opt => (
                <button
                  key={opt.value}
                  className={`lb-chip${category === opt.value ? ' on' : ''}`}
                  onClick={() => handleChipChange(opt.value)}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* Rounds */}
          <div className="lb-section">
            <div className="lb-section-head">
              <span>Rounds</span>
              <span>{ROUNDS_META[rounds]}</span>
            </div>
            <div className="lb-segmented">
              {([5, 10, 15] as const).map(r => (
                <button
                  key={r}
                  className={rounds === r ? 'on' : ''}
                  onClick={() => handleRoundsChange(r)}
                >
                  <span className="n">{r}</span>
                  <span className="l">{r === 5 ? 'Quick' : r === 10 ? 'Standard' : 'Marathon'}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      </main>

      {/* Sticky start bar */}
      <div className="lb-start-bar">
        <div className="lb-start-inner">
          <button className="lb-start" onClick={handleStart} disabled={startDisabled}>
            <span>{startLabel}</span>
            {isHost && bothJoined && (
              <svg
                className="arrow"
                width="18" height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M5 12h14" /><path d="M13 6l6 6-6 6" />
              </svg>
            )}
          </button>
          <div className="lb-start-note">
            {bothJoined ? 'Both players ready' : 'Waiting for opponent to join'}
          </div>
        </div>
      </div>

      {/* Copy toast */}
      <div className={`lb-toast${copied ? ' show' : ''}`}>
        Copied · {roomCode}
      </div>
    </div>
  )
}
