import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useGameStore } from '../store/useGameStore'
import type { CategoryType } from '../types'
import './SoloConfig.css'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

interface ChipOption { value: string; label: string }

const OPTIONS: Record<CategoryType, ChipOption[]> = {
  GENRE: [
    { value: 'POP',        label: 'Pop' },
    { value: 'ROCK',       label: 'Rock' },
    { value: 'HIP_HOP',   label: 'Hip-Hop' },
    { value: 'ELECTRONIC', label: 'Electronic' },
    { value: 'RNB',        label: 'R&B' },
    { value: 'JAZZ',       label: 'Jazz' },
    { value: 'CLASSICAL',  label: 'Classical' },
    { value: 'METAL',      label: 'Metal' },
    { value: 'COUNTRY',    label: 'Country' },
    { value: 'LATIN',      label: 'Latin' },
    { value: 'UKRAINIAN',  label: '🇺🇦 Ukrainian' },
  ],
  DECADE: [
    { value: '1980', label: '80s' },
    { value: '1990', label: '90s' },
    { value: '2000', label: '2000s' },
    { value: '2010', label: '2010s' },
    { value: '2020', label: '2020s' },
  ],
}
const OPTS_LABEL: Record<CategoryType, string> = {
  GENRE: 'Pick a genre',
  DECADE: 'Pick a decade',
}
const DEFAULT_PICK: Record<CategoryType, string> = {
  GENRE: 'POP',
  DECADE: '2010',
}
const ROUNDS_META: Record<number, string> = { 5: '~3 min', 10: '~5 min', 15: '~8 min' }

export default function SoloConfig() {
  const navigate = useNavigate()
  const store = useGameStore()

  const [playerName, setPlayerName] = useState(store.playerName)
  const [categoryType, setCategoryType] = useState<CategoryType>('GENRE')
  const [category, setCategory] = useState('POP')
  const [rounds, setRounds] = useState(10)
  const [starting, setStarting] = useState(false)

  function handleTabChange(ct: CategoryType) {
    setCategoryType(ct)
    setCategory(DEFAULT_PICK[ct])
  }

  async function handleStart() {
    if (!playerName.trim()) return
    setStarting(true)
    try {
      const res = await fetch(`${API_BASE}/api/rooms/solo`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerName: playerName.trim() }),
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data: { roomCode: string; playerToken: string; playerId: number } = await res.json()
      store.setPlayerName(playerName.trim())
      store.setPlayerToken(data.playerToken)
      store.setPlayerId(String(data.playerId))
      store.setRoomCode(data.roomCode)
      store.setGameMode('solo')
      store.updateGameConfig({ rounds, category, categoryType })
      navigate(`/game/${data.roomCode}`)
    } catch (err) {
      console.error('[SoloConfig] createSoloRoom failed', err)
      setStarting(false)
    }
  }

  return (
    <div className="min-h-dvh grid place-items-start justify-items-center p-4 overflow-x-hidden relative">
      <div className="sc-ambient" />
      <div className="grain" />

      <main className="sc-stage">
        {/* Top bar */}
        <div className="sc-topbar">
          <Link to="/" className="sc-back">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M19 12H5" /><path d="M11 6l-6 6 6 6" />
            </svg>
            Back
          </Link>
          <span>Solo mode</span>
        </div>

        {/* Player name */}
        <div className="sc-section">
          <div className="sc-section-head">
            <span>Your name</span>
          </div>
          <input
            className="input"
            type="text"
            placeholder="Enter your name"
            maxLength={16}
            autoComplete="off"
            spellCheck={false}
            value={playerName}
            onChange={e => setPlayerName(e.target.value)}
          />
        </div>

        {/* Category tabs */}
        <div className="sc-section">
          <div className="sc-section-head">
            <span>Category</span>
          </div>
          <div className="sc-tabs">
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

        {/* Chip grid */}
        <div className="sc-section">
          <div className="sc-section-head">
            <span>{OPTS_LABEL[categoryType]}</span>
          </div>
          <div className="sc-chips">
            {OPTIONS[categoryType].map(opt => (
              <button
                key={opt.value}
                className={`sc-chip${category === opt.value ? ' on' : ''}`}
                onClick={() => setCategory(opt.value)}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>

        {/* Rounds */}
        <div className="sc-section">
          <div className="sc-section-head">
            <span>Rounds</span>
            <span>{ROUNDS_META[rounds]}</span>
          </div>
          <div className="sc-segmented">
            {([5, 10, 15] as const).map(r => (
              <button
                key={r}
                className={rounds === r ? 'on' : ''}
                onClick={() => setRounds(r)}
              >
                <span className="n">{r}</span>
                <span className="l">{r === 5 ? 'Quick' : r === 10 ? 'Standard' : 'Marathon'}</span>
              </button>
            ))}
          </div>
        </div>
      </main>

      {/* Sticky start bar */}
      <div className="sc-start-bar">
        <div className="sc-start-inner">
          <button className="sc-start" onClick={handleStart} disabled={starting || !playerName.trim()}>
            <span>{starting ? 'Starting…' : 'Start'}</span>
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
          </button>
        </div>
      </div>
    </div>
  )
}
