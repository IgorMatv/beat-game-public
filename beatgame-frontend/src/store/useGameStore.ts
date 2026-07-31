import { create } from 'zustand'
import type { Player, LobbyPlayer, GameConfig, CategoryType, RoundState, Phase, RoundStartMessage, RoundResultMessage, RoomStateMessage, GameOverMessage, RoomConfigMessage } from '../types'

interface GameStore {
  playerName: string
  playerToken: string
  playerId: string
  roomCode: string
  gameMode: 'solo' | 'multi'
  players: Player[]
  lobbyPlayers: LobbyPlayer[]
  isHost: boolean
  gameConfig: GameConfig
  currentRound: RoundState | null
  scores: Record<string, number>
  prevScores: Record<string, number>
  phase: Phase
  hasAnswered: boolean
  lastRoundResult: RoundResultMessage | null
  lastGameOver: GameOverMessage | null
  musicPaused: boolean
  startGameError: string | null

  setPlayerName(name: string): void
  setPlayerToken(token: string): void
  setPlayerId(id: string): void
  setRoomCode(code: string): void
  setIsHost(v: boolean): void
  setGameMode(m: 'solo' | 'multi'): void
  setPhase(p: Phase): void
  setPlayers(players: Player[]): void
  updateGameConfig(cfg: Partial<GameConfig>): void
  applyRoundStart(msg: RoundStartMessage): void
  applyRoundResult(msg: RoundResultMessage): void
  applyRoomState(msg: RoomStateMessage): void
  applyRoomConfig(msg: RoomConfigMessage): void
  applyGameOver(msg: GameOverMessage): void
  setHasAnswered(v: boolean): void
  setMusicPaused(paused: boolean): void
  setStartGameError(reason: string | null): void
  resetForRematch(): void
  reset(): void
}

function generateToken(): string {
  // randomUUID requires secure context (HTTPS); getRandomValues works over HTTP too
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const b = crypto.getRandomValues(new Uint8Array(16))
  b[6] = (b[6] & 0x0f) | 0x40
  b[8] = (b[8] & 0x3f) | 0x80
  return [...b].map((v, i) =>
    ([3, 5, 7, 9].includes(i) ? '-' : '') + v.toString(16).padStart(2, '0')
  ).join('')
}

function getOrCreateToken(): string {
  const stored = localStorage.getItem('playerToken')
  if (stored) return stored
  const token = generateToken()
  localStorage.setItem('playerToken', token)
  return token
}

function getStoredPlayerId(): string {
  return localStorage.getItem('playerId') ?? ''
}

const DEFAULT_CONFIG: GameConfig = Object.freeze({ rounds: 10, category: 'POP', categoryType: 'GENRE' })

export const useGameStore = create<GameStore>((set) => ({
  playerName: '',
  playerToken: getOrCreateToken(),
  playerId: getStoredPlayerId(),
  roomCode: '',
  gameMode: 'multi',
  players: [],
  lobbyPlayers: [],
  isHost: false,
  gameConfig: { ...DEFAULT_CONFIG },
  currentRound: null,
  scores: {},
  prevScores: {},
  phase: 'home',
  hasAnswered: false,
  lastRoundResult: null,
  lastGameOver: null,
  musicPaused: false,
  startGameError: null,

  setPlayerName: (name) => set({ playerName: name }),
  setPlayerToken: (token) => {
    localStorage.setItem('playerToken', token)
    set({ playerToken: token })
  },
  setPlayerId: (id) => {
    localStorage.setItem('playerId', id)
    set({ playerId: id })
  },
  setRoomCode: (code) => set({ roomCode: code }),
  setIsHost: (v) => set({ isHost: v }),
  setGameMode: (m) => set({ gameMode: m }),
  setPhase: (p) => set({ phase: p }),
  setPlayers: (players) => set({ players }),
  updateGameConfig: (cfg) => set((s) => ({ gameConfig: { ...s.gameConfig, ...cfg } })),

  applyRoundStart: (msg) => set({
    currentRound: {
      trackId: msg.trackId,
      previewUrl: msg.previewUrl,
      options: msg.options,
      roundNumber: msg.roundNumber,
      totalRounds: msg.totalRounds,
      remainingSeconds: msg.remainingSeconds,
    },
    scores: msg.scores,
    hasAnswered: false,
    phase: 'playing',
    musicPaused: false,
  }),

  applyRoundResult: (msg) => set((s) => ({
    prevScores: { ...s.scores },
    scores: msg.scores,
    lastRoundResult: msg,
    phase: 'answer_reveal',
  })),

  applyRoomState: (msg) => set((s) => {
    const mine = msg.players.find((p) => String(p.id) === s.playerId)
    return {
      lobbyPlayers: msg.players,
      isHost: mine ? mine.isHost : s.isHost,
      phase: s.phase === 'home' || s.phase === 'lobby' ? 'lobby' : s.phase,
    }
  }),

  applyRoomConfig: (msg) => set({
    gameConfig: { rounds: msg.rounds, category: msg.category, categoryType: msg.categoryType as CategoryType },
  }),

  applyGameOver: (msg) => set({
    scores: msg.scores,
    lastGameOver: msg,
    phase: 'game_over',
  }),

  setHasAnswered: (v) => set({ hasAnswered: v }),
  setMusicPaused: (paused) => set({ musicPaused: paused }),
  setStartGameError: (reason) => set({ startGameError: reason }),

  resetForRematch: () => set({
    gameConfig: { ...DEFAULT_CONFIG },
    currentRound: null,
    scores: {},
    prevScores: {},
    phase: 'lobby',
    hasAnswered: false,
    lastRoundResult: null,
    lastGameOver: null,
    players: [],
    startGameError: null,
  }),

  reset: () => set({
    playerName: '',
    roomCode: '',
    gameMode: 'multi',
    players: [],
    lobbyPlayers: [],
    isHost: false,
    gameConfig: { ...DEFAULT_CONFIG },
    currentRound: null,
    scores: {},
    prevScores: {},
    phase: 'home',
    hasAnswered: false,
    lastRoundResult: null,
    lastGameOver: null,
    musicPaused: false,
    startGameError: null,
  }),
}))
