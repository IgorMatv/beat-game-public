import { useRef, useCallback } from 'react'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'
import { useGameStore } from '../store/useGameStore'
import type { RoundStartMessage, RoundResultMessage, GameOverMessage, RoomStateMessage, RoomConfigMessage, SubmitAnswerPayload, StartGamePayload, RejoinPayload, UpdateConfigPayload, MusicPausedMessage, GamePausePayload } from '../types'

// VITE_API_BASE_URL must use https:// in production to avoid mixed-content blocking.
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

// Backend sends no "type" field - detect message shape by unique field presence on both topics.
function isRoundStart(m: unknown): m is RoundStartMessage {
  return typeof m === 'object' && m !== null && 'roundNumber' in m && 'trackId' in m && 'options' in m && 'previewUrl' in m
}
function isRoundResult(m: unknown): m is RoundResultMessage {
  return typeof m === 'object' && m !== null && 'correctAnswer' in m && 'correctTrackId' in m
}
function isGameOver(m: unknown): m is GameOverMessage {
  return typeof m === 'object' && m !== null && 'winnerPlayerToken' in m
}
function isRoomState(m: unknown): m is RoomStateMessage {
  return typeof m === 'object' && m !== null && 'players' in m && 'status' in m
}
function isRoomConfig(m: unknown): m is RoomConfigMessage {
  return typeof m === 'object' && m !== null
    && 'rounds' in m && 'category' in m && 'categoryType' in m
    && !('players' in m)
}
function isMusicPaused(m: unknown): m is MusicPausedMessage {
  return typeof m === 'object' && m !== null
    && 'paused' in m && typeof (m as Record<string, unknown>).paused === 'boolean'
    && Object.keys(m as object).length === 1
}

export function useWebSocket() {
  const clientRef = useRef<Client | null>(null)

  const connect = useCallback((roomCode: string, playerToken: string, onConnected?: () => void) => {
    if (clientRef.current?.active) return

    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws`),
      connectHeaders: { playerToken, roomCode },
      reconnectDelay: 5000,
      onConnect: () => {
        console.log('[WS] connected')

        client.subscribe(`/topic/room.${roomCode}`, (frame) => {
          try {
            const raw: unknown = JSON.parse(frame.body)
            if (isRoomState(raw))       useGameStore.getState().applyRoomState(raw)
            else if (isRoomConfig(raw)) useGameStore.getState().applyRoomConfig(raw)
            else console.warn('[WS] unknown room message', raw)
          } catch (err) {
            console.error('[WS] failed to parse room message', err)
          }
        })

        client.subscribe(`/topic/game.${roomCode}`, (frame) => {
          try {
            const raw: unknown = JSON.parse(frame.body)
            const store = useGameStore.getState()
            if (isRoundStart(raw))        store.applyRoundStart(raw)
            else if (isRoundResult(raw))  store.applyRoundResult(raw)
            else if (isGameOver(raw))     store.applyGameOver(raw)
            else if (isMusicPaused(raw))  store.setMusicPaused((raw as MusicPausedMessage).paused)
            else console.warn('[WS] unknown game message', raw)
          } catch (err) {
            console.error('[WS] failed to parse game message', err)
          }
        })

        const { phase } = useGameStore.getState()
        if (phase !== 'home' && phase !== 'lobby') {
          const payload: RejoinPayload = { roomCode, playerToken }
          client.publish({ destination: '/app/game.rejoin', body: JSON.stringify(payload) })
        }

        onConnected?.()
      },
      onDisconnect: () => console.log('[WS] disconnected'),
      onStompError: (frame) => console.error('[WS] error', frame),
    })

    client.activate()
    clientRef.current = client
  }, [])

  const disconnect = useCallback(() => {
    clientRef.current?.deactivate()
    clientRef.current = null
  }, [])

  const sendAnswer = useCallback((payload: SubmitAnswerPayload) => {
    clientRef.current?.publish({ destination: '/app/game.answer', body: JSON.stringify(payload) })
  }, [])

  const sendReady = useCallback((roomCode: string) => {
    clientRef.current?.publish({ destination: '/app/game.ready', body: JSON.stringify({ roomCode }) })
  }, [])

  const sendStart = useCallback((payload: StartGamePayload) => {
    clientRef.current?.publish({ destination: '/app/game.start', body: JSON.stringify(payload) })
  }, [])

  const sendRejoin = useCallback((roomCode: string, playerToken: string) => {
    clientRef.current?.publish({
      destination: '/app/game.rejoin',
      body: JSON.stringify({ roomCode, playerToken }),
    })
  }, [])

  const sendConfig = useCallback((payload: UpdateConfigPayload) => {
    clientRef.current?.publish({ destination: '/app/room.config', body: JSON.stringify(payload) })
  }, [])

  const sendPause = useCallback((payload: GamePausePayload) => {
    clientRef.current?.publish({ destination: '/app/game.pause', body: JSON.stringify(payload) })
  }, [])

  return { connect, disconnect, sendAnswer, sendReady, sendStart, sendRejoin, sendConfig, sendPause }
}
