// ── Domain types ────────────────────────────────────────

export interface Player {
  id: number
  name: string
  token: string
  isHost: boolean
  score: number
}

export interface LobbyPlayer {
  id: number
  name: string
  isHost: boolean
}

export type CategoryType = 'GENRE' | 'DECADE'

export interface GameConfig {
  rounds: number
  category: string          // e.g. "POP", "ROCK", "2010s"
  categoryType: CategoryType
}

export interface RoundState {
  trackId: number
  previewUrl: string | null
  options: string[]         // ["Title — Artist", ...]
  roundNumber: number
  totalRounds: number
}

export type Phase =
  | 'home'
  | 'solo_config'
  | 'lobby'
  | 'playing'
  | 'answer_reveal'
  | 'round_result'
  | 'game_over'

// ── WebSocket message DTOs (Server → Client) ────────────
// NOTE: The backend sends NO "type" or "roomCode" field in any message.
// Duck-typing is used in useWebSocket.ts to dispatch to the correct handler.

export interface RoundStartMessage {
  roundNumber: number
  totalRounds: number
  trackId: number
  previewUrl: string | null
  options: string[]
}

export interface RoundResultMessage {
  roundNumber: number
  correctTrackId: number
  correctAnswer: string         // "Title — Artist"
  scores: Record<string, number>  // playerToken → cumulative score
}

export interface RoomStateMessage {
  players: LobbyPlayer[]
  status: string
}

export interface RoomConfigMessage {
  roomCode: string
  rounds: number
  category: string
  categoryType: CategoryType
}

export interface GameOverMessage {
  scores: Record<string, number>   // playerToken → final score
  winnerPlayerToken: string | null
}

export interface MusicPausedMessage { paused: boolean }

// ── WebSocket message DTOs (Client → Server) ────────────

export interface SubmitAnswerPayload {
  roomCode: string
  trackId: number
  answerIndex: number
  timeMs: number
}

export interface UpdateConfigPayload {
  roomCode: string
  rounds: number
  category: string
  categoryType: CategoryType
}

export interface GamePausePayload { roomCode: string; paused: boolean }

export interface StartGamePayload {
  roomCode: string
  rounds: number
  category: string
  categoryType: CategoryType
}

export interface RejoinPayload {
  roomCode: string
  playerToken: string
}
